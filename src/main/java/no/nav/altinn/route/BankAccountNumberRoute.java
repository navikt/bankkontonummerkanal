package no.nav.altinn.route;

import io.prometheus.client.Counter;
import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonRequest;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.datatype.DatatypeFactory;
import java.util.*;

import static no.nav.altinn.validators.OrganizationStructureValidator.validateOrganizationStructure;

public class BankAccountNumberRoute implements Runnable {
    private final static Counter INCOMING_MESSAGE_COUNTER = Counter.build().name("incoming_message_count")
            .help("Counts the number of incoming messages").register();
    private final static Counter INVALID_ORG_STRUCTURE_COUNTER = Counter.build().name("invalid_org_strcture_count")
            .help("Counts the number of messages that failed because the organization structure was invalid").create();
    private final static Counter SUCCESSFUL_MESSAGE_COUNTER = Counter.build().name("successful_message_count")
            .help("Counts the number of successful messages").register();

    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private Consumer<String, ExternalAttachment> consumer;
    private no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver employer;
    private BehandleArbeidsgiver handleEmployer;
    private final BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

    public BankAccountNumberRoute(String partition, Properties kafkaConfig, EnvironmentConfig environmentConfig) {
        consumer = new KafkaConsumer<>(kafkaConfig);
        consumer.subscribe(Collections.singletonList(partition));

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

    }

    @Override
    public void run() {
        while (true) {
            try {
                ConsumerRecords<String, ExternalAttachment> records = consumer.poll(100);
                for (ConsumerRecord<String, ExternalAttachment> record : records) {
                    INCOMING_MESSAGE_COUNTER.inc();
                    ExternalAttachment externalAttachment = record.value();

                    OppdaterKontonummerRequest updateBankAccountRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(externalAttachment.getBatch());

                    HentOrganisasjonRequest getOrganisationRequest = new HentOrganisasjonRequest();
                    getOrganisationRequest.setOrgNr(updateBankAccountRequest.getOverordnetEnhet().getOrgNr());
                    getOrganisationRequest.setHentRelaterteOrganisasjoner(true);

                    HentOrganisasjonResponse organisationResponse = employer.hentOrganisasjon(getOrganisationRequest);

                    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

                    updateBankAccountRequest.getSporingsdetalj().setTransaksjonsId(externalAttachment.getArchRef());
                    updateBankAccountRequest.getSporingsdetalj().setInnsendtTidspunkt(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));

                    if(validateOrganizationStructure(organisationResponse, updateBankAccountRequest)) {
                        handleEmployer.oppdaterKontonummer(updateBankAccountRequest);
                        log.info("Successfully updated the account number for: {}", updateBankAccountRequest.getOverordnetEnhet().getOrgNr());
                        SUCCESSFUL_MESSAGE_COUNTER.inc();
                    } else {
                        log.error("Invalid organisation structure for request");
                        INVALID_ORG_STRUCTURE_COUNTER.inc();
                    }
                }
                consumer.commitSync();
            } catch (Exception e) {
                log.error("An error occurred while pushing to AAreg: ", e);
            }
        }
    }
}
