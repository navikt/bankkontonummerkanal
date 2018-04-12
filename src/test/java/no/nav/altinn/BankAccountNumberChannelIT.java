package no.nav.altinn;

import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.route.BankAccountNumberRoute;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.common.KafkaEnvironment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.security.auth.callback.CallbackHandler;
import javax.xml.ws.Endpoint;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

import static no.nav.altinn.utils.DomainObjectUtils.*;
import static no.nav.altinn.utils.XmlUtils.*;
import static org.mockito.Mockito.*;

public class BankAccountNumberChannelIT {
    private static final String MOCK_USERNAME = "mockUsername";
    private static final String MOCK_PASSWORD = "mockPassword";

    private static final Arbeidsgiver arbeidsgiver = mock(Arbeidsgiver.class);
    private static final BehandleArbeidsgiver behandleArbeidsgiver = mock(BehandleArbeidsgiver.class);
    private static EnvironmentConfig environmentConfig;
    private static KafkaEnvironment kafkaEnvironment;
    private static KafkaConsumer<String, ExternalAttachment> kafkaConsumer;
    private static KafkaProducer<String, ExternalAttachment> kafkaProducer;
    private static BankAccountNumberChannel bankAccountNumberChannel;

    public static int randomPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        int port = randomPort();
        String hentOrgNrURL = "http://localhost:" + port + "/ws/arbeidsgiver/hentOrganisasjon";
        String oppdatertKontoNrUrl = "http://localhost:" + port + "/ws/behandleArbeidsgiver/oppdaterKontonummer";
        environmentConfig = new EnvironmentConfig(hentOrgNrURL, oppdatertKontoNrUrl, MOCK_USERNAME, MOCK_PASSWORD,
                Integer.toString(randomPort()), "test.bankaccount.number.update");

        kafkaEnvironment = new KafkaEnvironment(1, Collections.singletonList(environmentConfig.bankaccountNumberChangedTopic), true, false, false);
        kafkaEnvironment.start();

        CXFNonSpringServlet soapServlet = new CXFNonSpringServlet();


        ServletContextHandler servletHandler = new ServletContextHandler();
        servletHandler.addServlet(new ServletHolder(soapServlet), "/ws/*");

        Server server = new Server(port);
        server.setHandler(servletHandler);
        server.start();

        BusFactory.setDefaultBus(soapServlet.getBus());
        Endpoint.publish("/arbeidsgiver", arbeidsgiver, inInterceptor(environmentConfig));
        Endpoint.publish("/behandleArbeidsgiver", behandleArbeidsgiver, inInterceptor(environmentConfig));

        Properties kafkaProperties = new Properties();
        kafkaProperties.load(new InputStreamReader(BankAccountNumberChannelIT.class.getResourceAsStream("/kafka_consumer.properties")));
        kafkaProperties.setProperty("bootstrap.servers", kafkaEnvironment.getBrokersURL());
        kafkaProperties.setProperty("schema.registry.url", kafkaEnvironment.getServerPark().getSchemaregistry().getUrl());
        kafkaProperties.setProperty("auto.offset.reset", "earliest");
        kafkaProperties.remove("security.protocol");

        Properties producerProperties = new Properties();
        producerProperties.load(new InputStreamReader(BankAccountNumberChannelIT.class.getResourceAsStream("/kafka_producer.properties")));
        producerProperties.setProperty("bootstrap.servers", kafkaEnvironment.getBrokersURL());
        producerProperties.setProperty("schema.registry.url", kafkaEnvironment.getServerPark().getSchemaregistry().getUrl());
        producerProperties.remove("security.protocol");
        kafkaProducer = new KafkaProducer<>(producerProperties);
        kafkaConsumer = spy(new KafkaConsumer<String, ExternalAttachment>(kafkaProperties));
        // Set up mocks

        bankAccountNumberChannel = new BankAccountNumberChannel();
        new Thread(() -> bankAccountNumberChannel.bootstrap(kafkaConsumer, environmentConfig)).start();
    }

    @Before
    public void shutdown() {
        reset(arbeidsgiver, behandleArbeidsgiver, kafkaConsumer);
    }

    private static AbstractFeature inInterceptor(EnvironmentConfig environmentConfig) {
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

    @AfterClass
    public static void tearDown() {
        bankAccountNumberChannel.shutdown();
        kafkaEnvironment.tearDown();
    }

    @Test
    public void test() throws Exception {
        when(arbeidsgiver.hentOrganisasjon(any())).thenReturn(defaultTestResponse());

        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef("TestArchRef")
                .setSc("a")
                .setSec("b")
                .setBatch(readXml("/xmlextractor/message.xml"))
                .build();

        kafkaProducer.send(new ProducerRecord<>(environmentConfig.bankaccountNumberChangedTopic, externalAttachment));

        verify(kafkaConsumer, timeout(30000).times(1)).commitSync();
        verify(behandleArbeidsgiver, timeout(30000).times(1)).oppdaterKontonummer(any());

        //System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(behandleArbeidsgiver.getRequests()));
    }

    @Test
    public void testInvalidOrgStrctureMissingUnderenhetInAAreg() throws Exception {
        when(arbeidsgiver.hentOrganisasjon(any())).thenReturn(defaultTestResponse());

        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef("TestArchRef")
                .setSc("a")
                .setSec("b")
                .setBatch(readXml("/xmlextractor/message_with_two_daughter_organizations.xml"))
                .build();

        kafkaProducer.send(new ProducerRecord<>(environmentConfig.bankaccountNumberChangedTopic, externalAttachment));

        verify(arbeidsgiver, timeout(30000).times(1)).hentOrganisasjon(any());
        verify(kafkaConsumer, timeout(30000).times(1)).commitSync();

        verify(behandleArbeidsgiver, never()).oppdaterKontonummer(any());
    }
}
