package no.nav.altinn.utils;

import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.common.KafkaEnvironment;
import org.apache.cxf.Bus;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;

import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

import static no.nav.altinn.utils.XmlUtils.readXml;

public class TestUtil {

    public static int randomPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    public static KafkaConsumer<String, ExternalAttachment> createConsumer(KafkaEnvironment kafkaEnvironment) throws IOException {
        Properties kafkaProperties = new Properties();
        kafkaProperties.load(new InputStreamReader(TestUtil.class.getResourceAsStream("/kafka_consumer.properties")));
        kafkaProperties.setProperty("bootstrap.servers", kafkaEnvironment.getBrokersURL());
        kafkaProperties.setProperty("schema.registry.url", kafkaEnvironment.getServerPark().getSchemaregistry().getUrl());
        kafkaProperties.setProperty("auto.offset.reset", "earliest");
        kafkaProperties.remove("security.protocol");

        return new KafkaConsumer<>(kafkaProperties);
    }

    public static KafkaProducer<String, ExternalAttachment> createProducer(KafkaEnvironment kafkaEnvironment) throws IOException {
        Properties producerProperties = new Properties();
        producerProperties.load(new InputStreamReader(TestUtil.class.getResourceAsStream("/kafka_producer.properties")));
        producerProperties.setProperty("bootstrap.servers", kafkaEnvironment.getBrokersURL());
        producerProperties.setProperty("schema.registry.url", kafkaEnvironment.getServerPark().getSchemaregistry().getUrl());
        producerProperties.remove("security.protocol");

        return new KafkaProducer<>(producerProperties);
    }

    public static AbstractFeature inInterceptor(EnvironmentConfig environmentConfig) {
        HashMap<String, Object> props = new HashMap<>();

        props.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        props.put(WSHandlerConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
        props.put(WSHandlerConstants.PW_CALLBACK_REF, (CallbackHandler) callbacks -> {
            WSPasswordCallback callback = (WSPasswordCallback) callbacks[0];
            if (callback.getIdentifier().equals(environmentConfig.aaregWSUsername)) {
                callback.setPassword(environmentConfig.aaregWSPassword);
            }
        });

        return new AbstractFeature() {
            @Override
            protected void initializeProvider(InterceptorProvider provider, Bus bus) {
                provider.getInInterceptors().add(new WSS4JInInterceptor(props));
            }
        };
    }

    public static ExternalAttachment defaultExternalAttachment() throws IOException {
        return ExternalAttachment.newBuilder()
                .setArchiveReference(UUID.randomUUID().toString())
                .setServiceCode("a")
                .setServiceEditionCode("b")
                .setBatch(readXml("/xmlextractor/message.xml"))
                .build();
    }
}
