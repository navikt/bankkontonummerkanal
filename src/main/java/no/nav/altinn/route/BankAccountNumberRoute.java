package no.nav.altinn.route;

import io.prometheus.client.Counter;
import io.prometheus.client.Summary;
import net.logstash.logback.marker.LogstashMarker;
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
import static net.logstash.logback.marker.Markers.append;
import static net.logstash.logback.marker.Markers.appendArray;

public class BankAccountNumberRoute implements Runnable {
    public static final String ATTACHMENTS_REGEX = "<Attachments>.+</Attachments>";
    public static final String ATTACHMENTS_REPLACEMENT = "<Attachments><!--Removed for readability--></Attachments>";

    private final static String METRICS_NS = "bankkontonummerkanal";

    private final static Counter INCOMING_MESSAGE_COUNTER = Counter.build()
            .namespace(METRICS_NS)
            .name("incoming_message_count")
            .help("Counts the number of incoming messages")
            .register();
    private final static Counter SUCESSFUL_MESSAGE_COUNTER = Counter.build()
            .namespace(METRICS_NS)
            .name("successful_message_counter")
            .help("Counts the number of successful messages transferred to AAReg")
            .register();
    private final static Counter UNSUCESSFUL_MESSAGE_COUNTER = Counter.build()
            .namespace(METRICS_NS)
            .labelNames("archiveReference", "offset", "partition")
            .name("unsuccessful_message_counter")
            .help("Counts the number of unsuccessful messages")
            .register();
    private final static Summary FULL_ROUTE_TIMER = Summary.build()
            .namespace(METRICS_NS)
            .name("full_route_timer")
            .help("The time it takes a message to go through the full route")
            .register();
    public final static Summary AAREG_QUERY_TIMER = Summary.build()
            .namespace(METRICS_NS)
            .name("aareg_query_timer")
            .help("The time it takes to query aareg for the organisation information")
            .register();
    private final static Summary AAREG_UPDATE_TIMER = Summary.build()
            .namespace(METRICS_NS)
            .name("aareg_update_timer")
            .help("The time it takes to update the bank account number at aareg")
            .register();
    private final static Counter MISSING_NEW_BANKACCOUNTNUMBER_COUNTER = Counter.build()
            .namespace(METRICS_NS)
            .name("missing_new_bankaccountnumber")
            .help("Counts the number of messages that failed because missing new bankaccountnumnber")
            .register();

    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final BankAccountXmlExtractor xmlExtractor = new BankAccountXmlExtractor();
    private final AARegOrganisationStructureValidator structureValidator;
    private final BehandleArbeidsgiver handleEmployer;
    private final KafkaConsumer<String, ExternalAttachment> consumer;
    private final long retryInterval;
    private final int retryMaxRetries;
    private final Runnable shutdownHook;

    private boolean running = true;
    private int retryCount = 0;
    private String lastArchiveReference;

    public BankAccountNumberRoute(Arbeidsgiver employer, BehandleArbeidsgiver handleEmployer,
                                  KafkaConsumer<String, ExternalAttachment> consumer,
                                  long retryInterval, int retryMaxRetries, Runnable shutdownHook) {
        this.handleEmployer = handleEmployer;
        this.consumer = consumer;
        this.structureValidator = new AARegOrganisationStructureValidator(employer);
        this.retryInterval = retryInterval;
        this.retryMaxRetries = retryMaxRetries;
        this.shutdownHook = shutdownHook;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                log.debug("Polling for new records");
                for (ConsumerRecord<String, ExternalAttachment> record : consumer.poll(1000)) {
                    if (record.value().getArchiveReference().equals(lastArchiveReference)) {
                        retryCount++;
                    } else {
                        retryCount = 0;
                    }
                    lastArchiveReference = record.value().getArchiveReference();
                    handleMessage(record);
                }
            }
        } finally {
            shutdownHook.run();
        }
    }

    private LogstashMarker structureOf(OppdaterKontonummerRequest req) {
        return append("mainOrg", req.getOverordnetEnhet())
                .and(appendArray("daughterOrgs", req.getUnderliggendeBedriftListe().toArray()));
    }

    void handleMessage(ConsumerRecord<String, ExternalAttachment> record) {
        ExternalAttachment externalAttachment = record.value();
        String[] alertLabels = new String[] {
                record.value().getArchiveReference(),
                Long.toString(record.offset()),
                Integer.toString(record.partition())
        };
        INCOMING_MESSAGE_COUNTER.inc();
        String orgNr = "undefined";
        LogstashMarker logStructure = null;
        try (Summary.Timer ignoredFullRouteTimer = FULL_ROUTE_TIMER.startTimer()) {
            OppdaterKontonummerRequest updateRequest = xmlExtractor.extract(externalAttachment);
            if (updateRequest.getOverordnetEnhet() != null)
                orgNr = updateRequest.getOverordnetEnhet().getOrgNr();

            logStructure = structureOf(updateRequest);

            if (updateRequest.getOverordnetEnhet().getKontonummer() == null
                    && updateRequest.getUnderliggendeBedriftListe().stream().allMatch(d -> d.getKontonummer() == null)) {
                MISSING_NEW_BANKACCOUNTNUMBER_COUNTER.inc();
                log.error(logStructure, "Received a bank account number without any new bankaccount number {}, {}, {}, {}, {}",
                        keyValue("orgNumber", orgNr),
                        keyValue("archRef", record.value().getArchiveReference()),
                        keyValue("offset", record.offset()),
                        keyValue("partition", record.partition()),
                        keyValue("sendMail", true));
                return;
            }

            AARegOrganisationStructureValidator.Result result = structureValidator.validate(updateRequest,
                    record.value().getArchiveReference());
            if (result == AARegOrganisationStructureValidator.Result.Ok) {
                try (Summary.Timer ignoredAaregUpdateTimer = AAREG_UPDATE_TIMER.startTimer()) {
                    if (updateRequest.getOverordnetEnhet().getKontonummer() == null || updateRequest.getOverordnetEnhet().getKontonummer().isEmpty()) {
                        updateRequest.setOverordnetEnhet(null);
                    }
                    updateRequest.getUnderliggendeBedriftListe()
                            .removeIf(b -> b.getKontonummer() == null || b.getKontonummer().isEmpty());
                    handleEmployer.oppdaterKontonummer(updateRequest);
                    ignoredAaregUpdateTimer.observeDuration();
                }
                ignoredFullRouteTimer.observeDuration();
                log.info(structureOf(updateRequest), "Successfully updated the account number for: {}, {}",
                        keyValue("archRef", record.value().getArchiveReference()),
                        keyValue("orgNumber", orgNr));
                SUCESSFUL_MESSAGE_COUNTER.inc();
                consumer.commitSync();
            } else {
                logFailedMessage(record, orgNr, logStructure, null);
                UNSUCESSFUL_MESSAGE_COUNTER.labels(alertLabels).inc();
                consumer.commitSync();
            }
        } catch (SOAPFaultException | XMLStreamException | DatatypeConfigurationException |
                HentOrganisasjonOrganisasjonIkkeFunnet e) {
            // XMLStreamException and DatatypeConfigurationException should not occur
            // HentOrganisasjonOrganisasjonIkkeFunnet but might occur when schema is incomplete
            // All these errors are non-recoverable, so we dump them into the log, for kibana to pick it up
            // and send an notification
            logFailedMessage(record, orgNr, logStructure, e);
            consumer.commitSync();
            UNSUCESSFUL_MESSAGE_COUNTER.labels(alertLabels).inc();
        } catch (Exception e) {
            doRetry(record, orgNr, logStructure, e);
        }
    }

    private void doRetry(ConsumerRecord<String, ExternalAttachment> record, String orgNr, LogstashMarker logStructure, Exception e) {
        if (retryCount < retryMaxRetries) {
            log.warn(logStructure, "Exception caught while updating account number in AAReg, will retry in "
                            + retryInterval + " retry " + (retryCount+1) + "/" + retryMaxRetries + ". {}, {}, {}, {}",
                    keyValue("archRef", record.value().getArchiveReference()),
                    keyValue("offset", record.offset()),
                    keyValue("partition", record.partition()),
                    keyValue("sendMail", false),
                    e);

            consumer.seek(new TopicPartition(record.topic(), record.partition()), record.offset());

            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException interruptedException) {
                log.error("Interrupted while waiting to retry", interruptedException);
            }
        } else {
            logFailedMessage(record, orgNr, logStructure, e);
            consumer.commitSync();
        }
    }

    public void logFailedMessage(ConsumerRecord<String, ExternalAttachment> record, String orgNr, LogstashMarker logStructure, Exception e) {
        if (log.isDebugEnabled()) {
            log.debug("Content of failed message: {}, {}, {}, {}",
                    keyValue("archRef", record.value().getArchiveReference()),
                    keyValue("offset", record.offset()),
                    keyValue("partition", record.partition()),
                    keyValue("xmlMessage", record.value().getBatch().replaceAll(ATTACHMENTS_REGEX,
                            ATTACHMENTS_REPLACEMENT)));
        }
        log.error(logStructure, "Exception caught while updating account number in AAReg. {}, {}, {}, {}, {}",
                keyValue("archRef", record.value().getArchiveReference()),
                keyValue("offset", record.offset()),
                keyValue("partition", record.partition()),
                keyValue("sendMail", true),
                keyValue("orgNumber", orgNr),
                e);
    }
}
