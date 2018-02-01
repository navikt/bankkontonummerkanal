package no.nav.altinn;

import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.part.arbeidsgiver.v1.Kontonummer;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonRequest;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.binding.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.binding.BehandleArbeidsgiverWSEXPBehandleArbeidsgiverHttpService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import java.util.Collections;
import java.util.Properties;

public class BankAccountNumberRoute implements Runnable {
    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);

    private Consumer<String, ExternalAttachment> consumer;
    private no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver employer;
    private BehandleArbeidsgiver handleEmployer;
    private final BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

    public BankAccountNumberRoute(String partition, Properties kafkaConfig) {
        consumer = new KafkaConsumer<>(kafkaConfig);
        consumer.subscribe(Collections.singletonList(partition));

        Arbeidsgiver employer = new Arbeidsgiver();
        this.employer = employer.getArbeidsgiverPort();

        BehandleArbeidsgiverWSEXPBehandleArbeidsgiverHttpService handleEmployerService = new BehandleArbeidsgiverWSEXPBehandleArbeidsgiverHttpService();
        handleEmployer = handleEmployerService.getBehandleArbeidsgiverWSEXPBehandleArbeidsgiverHttpPort();

    }

    @Override
    public void run() {
        while (true) {
            try {
                ConsumerRecords<String, ExternalAttachment> records = consumer.poll(100);
                for (ConsumerRecord<String, ExternalAttachment> record : records) {
                    ExternalAttachment externalAttachment = record.value();

                    OppdaterKontonummerRequest update = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(externalAttachment.getBatch());

                    HentOrganisasjonRequest getOrganisationRequest = new HentOrganisasjonRequest();

                    Kontonummer bankAccountNumber = employer.hentOrganisasjon(getOrganisationRequest).getOrganisasjon().getBankkontonr();



                    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

                    update.getSporingsdetalj().setTransaksjonsId(externalAttachment.getArchRef());
                    update.getSporingsdetalj().setInnsendtTidspunkt(datatypeFactory.newXMLGregorianCalendar());

                    handleEmployer.oppdaterKontonummer(update);

                    // TODO: Extract data
                    // TODO: Validate and push against cxf
                }
                consumer.commitSync();
            } catch (Exception e) {
                // TODO: Log this
            }
        }
    }
}
