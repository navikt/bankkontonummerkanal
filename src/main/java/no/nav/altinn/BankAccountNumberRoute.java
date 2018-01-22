package no.nav.altinn;

import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.HentOrganisasjon;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.binding.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.OppdaterKontonummer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.Collections;
import java.util.Properties;

public class BankAccountNumberRoute implements Runnable {
    private Consumer<String, ExternalAttachment> consumer;
    private HentOrganisasjon fetchOrganisation;
    private OppdaterKontonummer updateAccountNumber;

    public BankAccountNumberRoute(String partition, Properties kafkaConfig) {
        consumer = new KafkaConsumer<>(kafkaConfig);
        consumer.subscribe(Collections.singletonList(partition));

        Arbeidsgiver employer = new Arbeidsgiver();
        fetchOrganisation = employer.getPort(HentOrganisasjon.class);

    }

    @Override
    public void run() {
        while (true) {
            try {
                ConsumerRecords<String, ExternalAttachment> records = consumer.poll(100);
                for (ConsumerRecord<String, ExternalAttachment> record : records) {
                    ExternalAttachment externalAttachment = record.value();
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
