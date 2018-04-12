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
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.*;

public class BankAccountNumberRoute implements Runnable {
    private final static Counter INCOMING_MESSAGE_COUNTER = Counter.build().name("incoming_message_count")
            .help("Counts the number of incoming messages").register();
    private final static Counter SUCESSFUL_MESSAGE_COUNTER = Counter.build().name("successful_message_counter")
            .help("Counts the number of successful messages transferred to AAReg").register();
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
    private final AARegOrganisationStructureValidator structureValidator;
    private final BehandleArbeidsgiver handleEmployer;
    private final KafkaConsumer<String, ExternalAttachment> consumer;

    private boolean running = true;

    public BankAccountNumberRoute(Arbeidsgiver employer, BehandleArbeidsgiver handleEmployer, KafkaConsumer<String, ExternalAttachment> consumer) {
        this.handleEmployer = handleEmployer;
        this.consumer = consumer;
        this.structureValidator = new AARegOrganisationStructureValidator(employer);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            for (ConsumerRecord<String, ExternalAttachment> record : consumer.poll(1000)) {
                ExternalAttachment externalAttachment = record.value();

                Gauge.Timer fullRouteTimer = FULL_ROUTE_TIMER.startTimer();
                INCOMING_MESSAGE_COUNTER.inc();
                try {
                    OppdaterKontonummerRequest updateRequest = xmlExtractor.extract(externalAttachment);
                    if (retry(5, () -> structureValidator.validate(updateRequest), HentOrganisasjonOrganisasjonIkkeFunnet.class)) {
                        try (Gauge.Timer ignored = AAREG_UPDATE_TIMER.startTimer()) {
                            retry(5, () -> {
                                handleEmployer.oppdaterKontonummer(updateRequest);
                                log.info("Successfully updated the account number for: {}", updateRequest.getOverordnetEnhet().getOrgNr());
                                SUCESSFUL_MESSAGE_COUNTER.inc();
                                return true;
                            }, SoapFault.class);
                        }
                        fullRouteTimer.close();
                    } else {
                        log.error("Received message with invalid organisation. {}, {}",
                                keyValue("archRef", externalAttachment.getArchRef()),
                                keyValue("fullXmlMessage", externalAttachment.getBatch()));
                        INVALID_ORG_STRUCTURE_COUNTER.inc();
                    }
                    consumer.commitSync();
                } catch (WakeupException e) {
                    log.warn("Received a wakeup exception, shutting down poller?", e);
                } catch (Exception e) {
                    log.error("Exception caught while updating account number in AAReg. {}, {}",
                            keyValue("archRef", externalAttachment.getArchRef()),
                            keyValue("fullXmlMessage", externalAttachment.getBatch()),
                            e);
                }
            }
        }
        stop();
    }

    private boolean retry(int times, RetryTask supplier, Class<? extends Exception> exception) throws Exception {
        for (int retries = 0; retries < times - 1; retries++) {
            try {
                return supplier.apply();
            } catch (Exception e) {
                if (!exception.isInstance(e))
                    throw e;
            }
        }

        return supplier.apply();
    }

    private interface RetryTask {
        boolean apply() throws Exception;
    }
}
