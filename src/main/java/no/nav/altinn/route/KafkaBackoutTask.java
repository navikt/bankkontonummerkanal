package no.nav.altinn.route;

import io.reactivex.functions.Consumer;
import no.nav.altinn.messages.IncomingMessage;
import no.nav.altinnkanal.avro.ExternalAttachment;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaBackoutTask implements Consumer<IncomingMessage> {
    private final String topic;
    private final Producer<String, ExternalAttachment> producer;

    public KafkaBackoutTask(String topic, Producer<String, ExternalAttachment> producer) {
        this.topic = topic;
        this.producer = producer;
    }


    @Override
    public void accept(IncomingMessage incomingMessage) throws Exception {
        producer.send(new ProducerRecord<>(topic, incomingMessage.externalAttachment));
    }
}
