package no.nav.altinn.route;

import io.prometheus.client.Counter;
import io.reactivex.functions.Predicate;
import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.messages.ExtractedMessage;
import no.nav.altinn.messages.IncomingMessage;
import no.nav.altinn.validators.AARegOrganisationStructureValidator;
import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.ws.soap.SOAPFaultException;
import java.nio.charset.Charset;
import java.util.*;

public class BankAccountNumberRoute implements Runnable {
    private final static Counter INCOMING_MESSAGE_COUNTER = Counter.build().name("incoming_message_count")
            .help("Counts the number of incoming messages").register();
    private final static Counter INVALID_ORG_STRUCTURE_COUNTER = Counter.build().name("invalid_org_strcture_count")
            .help("Counts the number of messages that failed because the organization structure was invalid").create();

    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private Arbeidsgiver employer;
    private BehandleArbeidsgiver handleEmployer;
    private final BankAccountXmlExtractor xmlExtractor = new BankAccountXmlExtractor();
    private final KafkaBackoutTask backoutTask;
    private final KafkaPoller poller;
    private final AARegOrganisationStructureValidator structureValidator;
    private final AARegUpdaterTask updaterTask;
    private final long retryInterval = 5000;

    public BankAccountNumberRoute(String partition, String backoutTopic, Charset charset,
                                  Properties kafkaConsumerProperties, Properties kafkaProducerProperties,
                                  EnvironmentConfig environmentConfig) {
        // Configure password callback handler
        CallbackHandler pwCallback = callbacks -> ((WSPasswordCallback)callbacks[0]).setPassword(environmentConfig.aaregWSPassword);

        // Configure WS security with username password
        HashMap<String, Object> interceptorProperties = new HashMap<>();
        interceptorProperties.put(WSHandlerConstants.USER, environmentConfig.aaregWSUsername);
        interceptorProperties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        interceptorProperties.put(WSHandlerConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
        interceptorProperties.put(WSHandlerConstants.PW_CALLBACK_REF, pwCallback);
        WSS4JOutInterceptor passwordOutInterceptor = new WSS4JOutInterceptor(interceptorProperties);

        // Configure the endpoint used for hentArbeidsgiver
        JaxWsProxyFactoryBean arbeidsGiverFactory = new JaxWsProxyFactoryBean();
        arbeidsGiverFactory.setAddress(environmentConfig.aaregHentOrganisasjonEndpointURL);
        arbeidsGiverFactory.setOutInterceptors(Arrays.asList(
                new LoggingOutInterceptor(),
                passwordOutInterceptor
        ));
        arbeidsGiverFactory.setInInterceptors(Collections.singletonList(new LoggingInInterceptor()));
        arbeidsGiverFactory.setServiceClass(Arbeidsgiver.class);
        this.employer = (Arbeidsgiver) arbeidsGiverFactory.create();

        // Configure then endpoint used for oppdaterKontonummer
        JaxWsProxyFactoryBean handleEmployerFactory = new JaxWsProxyFactoryBean();
        handleEmployerFactory.setAddress(environmentConfig.aaregOppdaterKontonummerEndpointURL);
        handleEmployerFactory.setOutInterceptors(Arrays.asList(
                new LoggingOutInterceptor(),
                passwordOutInterceptor
        ));
        handleEmployerFactory.setInInterceptors(Collections.singletonList(new LoggingInInterceptor()));
        handleEmployerFactory.setServiceClass(BehandleArbeidsgiver.class);
        handleEmployer = (BehandleArbeidsgiver) handleEmployerFactory.create();

        Consumer<String, ExternalAttachment> consumer = new KafkaConsumer<>(kafkaConsumerProperties);
        consumer.subscribe(Collections.singletonList(partition));
        poller = new KafkaPoller(consumer, charset);

        KafkaProducer<String, ExternalAttachment> producer = new KafkaProducer<>(kafkaProducerProperties);
        backoutTask = new KafkaBackoutTask(backoutTopic, producer);

        structureValidator = new AARegOrganisationStructureValidator(employer);
        updaterTask = new AARegUpdaterTask(handleEmployer);
    }

    @Override
    public void run() {
        while (true) {
            for (IncomingMessage incoming : poller.poll()) {
                INCOMING_MESSAGE_COUNTER.inc();
                try {
                    ExtractedMessage<OppdaterKontonummerRequest> extracted = xmlExtractor.apply(incoming);
                    if (retry(5, extracted, structureValidator)) {
                        retry(5, extracted, e -> {
                            updaterTask.accept(e);
                            return true;
                        });
                    } else {
                        log.error("Received message with invalid organisation structure with archive reference {}",
                                incoming.externalAttachment.getArchRef());
                        INVALID_ORG_STRUCTURE_COUNTER.inc();
                        backoutTask.accept(incoming);
                    }
                } catch (Exception e) {
                    log.error("An error occurred, sending to backout topic", e);
                    try {
                        backoutTask.accept(incoming);
                    } catch (Exception backoutException) {
                        log.error("Failed to send to backout, dumping message content to log", backoutException);
                        log.error("Archive reference: ", incoming.externalAttachment.getArchRef());
                        log.error(incoming.xmlMessage);
                    }
                }
                poller.commit();
            }
        }
    }

    private <T> boolean retry(int times, T value, Predicate<T> predicate) throws Exception {
        for (int retries = 0; retries < times; retries++) {
            try {
                return predicate.test(value);
            } catch (Exception e) {
                if (e instanceof SOAPFaultException)
                    throw e; // Rethrow soap faults since we only want to retry when AAReg is down
                log.warn("Unable to contact AAReg, retrying in " + retryInterval + " ms", e);
            }
        }
        return predicate.test(value);
    }
}
