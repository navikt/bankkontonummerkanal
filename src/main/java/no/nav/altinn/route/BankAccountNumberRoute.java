package no.nav.altinn.route;

import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonRequest;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.binding.Arbeidsgiver;
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

import static no.nav.altinn.validators.OrganizationStructureValidator.validateOrganizationStructure;

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

                    OppdaterKontonummerRequest updateBankAccountRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(externalAttachment.getBatch());

                    HentOrganisasjonRequest getOrganisationRequest = new HentOrganisasjonRequest();
                    HentOrganisasjonResponse organisationResponse = employer.hentOrganisasjon(getOrganisationRequest);

                    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

                    updateBankAccountRequest.getSporingsdetalj().setTransaksjonsId(externalAttachment.getArchRef());
                    updateBankAccountRequest.getSporingsdetalj().setInnsendtTidspunkt(datatypeFactory.newXMLGregorianCalendar());

                    if( validateOrganizationStructure(organisationResponse, updateBankAccountRequest))
                    {
                        handleEmployer.oppdaterKontonummer(updateBankAccountRequest);
                    }
                    else
                    {
                        //TODO send to back to topic
                    }

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
