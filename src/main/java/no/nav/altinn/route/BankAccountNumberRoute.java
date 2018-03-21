package no.nav.altinn.route;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.reactivex.functions.Predicate;
import no.nav.altinn.config.ApplicationProperties;
import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.messages.ExtractedMessage;
import no.nav.altinn.messages.IncomingMessage;
import no.nav.altinn.validators.AARegOrganisationStructureValidator;
import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.ws.soap.SOAPFaultException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import static net.logstash.logback.argument.StructuredArguments.*;

public class BankAccountNumberRoute implements Runnable {
    private final static Counter INCOMING_MESSAGE_COUNTER = Counter.build().name("incoming_message_count")
            .help("Counts the number of incoming messages").register();
    private final static Counter INVALID_ORG_STRUCTURE_COUNTER = Counter.build().name("invalid_org_strcture_count")
            .help("Counts the number of messages that failed because the organization structure was invalid").create();
    private final static Gauge FULL_ROUTE_TIMER = Gauge.build().name("full_route_timer")
            .help("The time it takes a message to go through the full route").create();
    public final static Gauge AAREG_QUERY_TIMER = Gauge.build().name("aareg_query_timer")
            .help("The time it takes to query aareg for the organisation information").create();
    private final static Gauge AAREG_UPDATE_TIMER = Gauge.build().name("aareg_update_timer")
            .help("The time it takes to update the bank account number at aareg").create();

    private final static long RETRY_INTERVAL = 5000;

    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final BankAccountXmlExtractor xmlExtractor = new BankAccountXmlExtractor();
    private final KafkaPoller poller;
    private final AARegOrganisationStructureValidator structureValidator;
    private final AARegUpdaterTask updaterTask;
    private boolean running = true;

    public BankAccountNumberRoute(ApplicationProperties applicationProperties, Properties kafkaConsumerProperties,
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
        arbeidsGiverFactory.getFeatures().add(new LoggingFeature());
        arbeidsGiverFactory.setOutInterceptors(Collections.singletonList(passwordOutInterceptor));
        arbeidsGiverFactory.setServiceClass(Arbeidsgiver.class);
        Arbeidsgiver employer = (Arbeidsgiver) arbeidsGiverFactory.create();

        // Configure then endpoint used for oppdaterKontonummer
        JaxWsProxyFactoryBean handleEmployerFactory = new JaxWsProxyFactoryBean();
        handleEmployerFactory.setAddress(environmentConfig.aaregOppdaterKontonummerEndpointURL);
        handleEmployerFactory.getFeatures().add(new LoggingFeature());
        handleEmployerFactory.setOutInterceptors(Collections.singletonList(passwordOutInterceptor));
        handleEmployerFactory.setServiceClass(BehandleArbeidsgiver.class);
        BehandleArbeidsgiver handleEmployer = (BehandleArbeidsgiver) handleEmployerFactory.create();

        Consumer<String, ExternalAttachment> consumer = new KafkaConsumer<>(kafkaConsumerProperties);
        consumer.subscribe(Collections.singletonList(applicationProperties.getBankAccountChangedTopic()));
        poller = new KafkaPoller(consumer);

        structureValidator = new AARegOrganisationStructureValidator(employer);
        updaterTask = new AARegUpdaterTask(handleEmployer);
    }

    public void stop() {
        running = false;
        poller.stop();
    }

    @Override
    public void run() {
        while (running) {
            for (IncomingMessage incoming : poller.poll()) {
                Gauge.Timer fullRouteTimer = FULL_ROUTE_TIMER.startTimer();
                INCOMING_MESSAGE_COUNTER.inc();
                ExternalAttachment externalAttachment = incoming.externalAttachment;
                try {
                    ExtractedMessage<OppdaterKontonummerRequest> extracted = xmlExtractor.apply(incoming);
                    if (retry(5, extracted, structureValidator)) {
                        Gauge.Timer aaregUpdateTimer = AAREG_UPDATE_TIMER.startTimer();
                        retry(5, extracted, e -> {
                            updaterTask.accept(e);
                            return true;
                        });
                        aaregUpdateTimer.close();
                        fullRouteTimer.close();
                    } else {
                        log.error("Received message with invalid organisation. {}, {}",
                                keyValue("archRef", incoming.externalAttachment.getArchRef()),
                                keyValue("fullXmlMessage", incoming.xmlMessage));
                        INVALID_ORG_STRUCTURE_COUNTER.inc();
                    }
                } catch (Exception e) {
                    log.error("Exception caught while updating account number in AAReg. {}, {}",
                            keyValue("archRef", externalAttachment.getArchRef()),
                            keyValue("fullXmlMessage", incoming.xmlMessage),
                            e);
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
                log.warn("Unable to contact AAReg, retrying in " + RETRY_INTERVAL + " ms", e);
            }
        }
        return predicate.test(value);
    }
}
