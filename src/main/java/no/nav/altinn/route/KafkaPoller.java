package no.nav.altinn.route;

import no.nav.altinn.messages.IncomingMessage;
import no.nav.altinnkanal.avro.ExternalAttachment;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class KafkaPoller implements Publisher<IncomingMessage> {
    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final Consumer<String, ExternalAttachment> consumer;

    public KafkaPoller(Consumer<String, ExternalAttachment> consumer) {
        this.consumer = consumer;
    }

    public ArrayList<IncomingMessage> poll() {
        ArrayList<IncomingMessage> incomingMessages = new ArrayList<>();
        for (ConsumerRecord<String, ExternalAttachment> consumerRecords : consumer.poll(1)) {
            log.info("Polled message from kafka");
            ExternalAttachment externalAttachment = consumerRecords.value();
            incomingMessages.add(new IncomingMessage(externalAttachment.getBatch(), externalAttachment));
        }
        return incomingMessages;
    }

    public void commit() {
        consumer.commitSync();
    }


    @Override
    public void subscribe(Subscriber<? super IncomingMessage> subscriber) {
        while (true) {
            try {
                for (IncomingMessage incomingMessage : poll()) {
                    subscriber.onNext(incomingMessage);
                }
                commit();
            } catch (Throwable t) {
                log.error("THROWABLE");
                log.error("Exception: ", t);
            }
        }
    }
}
