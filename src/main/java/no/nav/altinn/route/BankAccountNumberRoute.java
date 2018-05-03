package no.nav.altinn.route;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import no.nav.altinn.validators.AARegOrganisationStructureValidator;
import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.HentOrganisasjonOrganisasjonIkkeFunnet;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.soap.SOAPFaultException;

import static net.logstash.logback.argument.StructuredArguments.*;

public class BankAccountNumberRoute implements Runnable {
    private final static Counter INCOMING_MESSAGE_COUNTER = Counter.build().name("incoming_message_count")
            .help("Counts the number of incoming messages").register();
    private final static Counter SUCESSFUL_MESSAGE_COUNTER = Counter.build().name("successful_message_counter")
            .help("Counts the number of successful messages transferred to AAReg").register();
    private final static Counter INVALID_ORG_STRUCTURE_COUNTER = Counter.build().name("invalid_org_strcture_count")
            .help("Counts the number of messages that failed because the organization structure was invalid").register();
    private final static Gauge FULL_ROUTE_TIMER = Gauge.build().name("full_route_timer")
            .help("The time it takes a message to go through the full route").register();
    public final static Gauge AAREG_QUERY_TIMER = Gauge.build().name("aareg_query_timer")
            .help("The time it takes to query aareg for the organisation information").register();
    private final static Gauge AAREG_UPDATE_TIMER = Gauge.build().name("aareg_update_timer")
            .help("The time it takes to update the bank account number at aareg").register();

    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final BankAccountXmlExtractor xmlExtractor = new BankAccountXmlExtractor();
    private final AARegOrganisationStructureValidator structureValidator;
    private final BehandleArbeidsgiver handleEmployer;
    private final KafkaConsumer<String, ExternalAttachment> consumer;
    private final long retryInterval;
    private final int retryMaxRetries;

    private boolean running = true;
    private int retryCount = 0;
    private String lastArchiveReference;

    public BankAccountNumberRoute(Arbeidsgiver employer, BehandleArbeidsgiver handleEmployer,
                                  KafkaConsumer<String, ExternalAttachment> consumer, long retryInterval, int retryMaxRetries) {
        this.handleEmployer = handleEmployer;
        this.consumer = consumer;
        this.structureValidator = new AARegOrganisationStructureValidator(employer);
        this.retryInterval = retryInterval;
        this.retryMaxRetries = retryMaxRetries;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            log.debug("Polling for new records");
            for (ConsumerRecord<String, ExternalAttachment> record : consumer.poll(1000)) {
                if (record.value().getArchRef().equals(lastArchiveReference)) {
                    retryCount ++;
                } else {
                    retryCount = 0;
                }
                lastArchiveReference = record.value().getArchRef();
                handleMessage(record);
            }
        }
        stop();
    }

    private void handleMessage(ConsumerRecord<String, ExternalAttachment> record) {
        ExternalAttachment externalAttachment = record.value();
        INCOMING_MESSAGE_COUNTER.inc();
        try (Gauge.Timer ignoredFullRouteTimer = FULL_ROUTE_TIMER.startTimer()) {
            OppdaterKontonummerRequest updateRequest = xmlExtractor.extract(externalAttachment);

            if (structureValidator.validate(updateRequest)) {
                try (Gauge.Timer ignoredAaregUpdateTimer = AAREG_UPDATE_TIMER.startTimer()) {
                    handleEmployer.oppdaterKontonummer(updateRequest);
                }
                log.info("Successfully updated the account number for: {}",
                        keyValue("orgNumber", updateRequest.getOverordnetEnhet().getOrgNr()));
                SUCESSFUL_MESSAGE_COUNTER.inc();
                consumer.commitSync();
            } else {
                log.error("Received message with invalid organisation. {}, {}",
                        keyValue("archRef", externalAttachment.getArchRef()),
                        keyValue("fullXmlMessage", externalAttachment.getBatch()));
                INVALID_ORG_STRUCTURE_COUNTER.inc();
                consumer.commitSync();
            }
        } catch (SOAPFaultException | XMLStreamException | DatatypeConfigurationException | HentOrganisasjonOrganisasjonIkkeFunnet e) {
            // XMLStreamException and DatatypeConfigurationException should not occur
            // HentOrganisasjonOrganisasjonIkkeFunnet but might occur when schema is incomplete
            // All these errors are non-recoverable, so we dump them into the log, for kibana to pick it up
            // and send an notification
            logFailedMessage(record, e);
            consumer.commitSync();
        } catch (Exception e) {
            doRetry(record, e);
        }
    }

    public void doRetry(ConsumerRecord<String, ExternalAttachment> record, Exception e) {
        if (retryCount < retryMaxRetries) {
            log.warn("Exception caught while updating account number in AAReg, will retry in " + retryInterval + " retry " + (retryCount+1) + "/" + retryMaxRetries + ". {}, {}, {}",
                    keyValue("archRef", record.value().getArchRef()),
                    keyValue("offset", record.offset()),
                    keyValue("sendMail", false),
                    e);

            consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());

            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException interruptedException) {
                log.error("Interrupted while waiting to retry", interruptedException);
            }
        } else {
            logFailedMessage(record, e);
            consumer.commitSync();
        }
    }

    public void logFailedMessage(ConsumerRecord<String, ExternalAttachment> record, Exception e) {
        log.error("Exception caught while updating account number in AAReg. {}, {}, {}, {}",
                keyValue("archRef", record.value().getArchRef()),
                keyValue("offset", record.offset()),
                keyValue("sendMail", true),
                keyValue("fullXmlMessage", record.value().getBatch()),
                e);
    }
}
