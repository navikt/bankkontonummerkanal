package no.nav.altinn;

import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.utils.DomainObjectUtils;
import no.nav.altinn.utils.TestUtil;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.common.KafkaEnvironment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.cxf.BusFactory;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.xml.ws.Endpoint;
import java.util.Collections;

import static no.nav.altinn.utils.DomainObjectUtils.*;
import static no.nav.altinn.utils.XmlUtils.*;
import static org.mockito.Mockito.*;

public class BankAccountNumberChannelIT {
    private static final String MOCK_USERNAME = "mockUsername";
    private static final String MOCK_PASSWORD = "mockPassword";

    private static final Arbeidsgiver arbeidsgiver = mock(Arbeidsgiver.class);
    private static final BehandleArbeidsgiver behandleArbeidsgiver = mock(BehandleArbeidsgiver.class);

    private static int mockPort;
    private static Server server;
    private static EnvironmentConfig environmentConfig;
    private static KafkaEnvironment kafkaEnvironment;
    private static KafkaConsumer<String, ExternalAttachment> kafkaConsumer;
    private static KafkaProducer<String, ExternalAttachment> kafkaProducer;
    private static BankAccountNumberChannel bankAccountNumberChannel;

    @BeforeClass
    public static void setUpClass() throws Exception {
        mockPort = TestUtil.randomPort();
        String hentOrgNrURL = "http://localhost:" + mockPort + "/ws/arbeidsgiver/hentOrganisasjon";
        String oppdatertKontoNrUrl = "http://localhost:" + mockPort + "/ws/behandleArbeidsgiver/oppdaterKontonummer";
        environmentConfig = new EnvironmentConfig(hentOrgNrURL, oppdatertKontoNrUrl, MOCK_USERNAME, MOCK_PASSWORD,
                TestUtil.randomPort(), "test.bankaccount.number.update", 5000, 5, 100, 200, "testuser", "testpass");

        kafkaEnvironment = new KafkaEnvironment(1, Collections.singletonList(environmentConfig.bankaccountNumberChangedTopic), true, false, false);
        kafkaEnvironment.start();

        createJettyServer();

        kafkaConsumer = spy(TestUtil.createConsumer(kafkaEnvironment));

        kafkaProducer = TestUtil.createProducer(kafkaEnvironment);
        // Set up mocks

        bankAccountNumberChannel = new BankAccountNumberChannel();
        new Thread(() -> bankAccountNumberChannel.bootstrap(kafkaConsumer, environmentConfig)).start();
    }

    private static void createJettyServer() throws Exception {
        CXFNonSpringServlet soapServlet = new CXFNonSpringServlet();


        ServletContextHandler servletHandler = new ServletContextHandler();
        servletHandler.addServlet(new ServletHolder(soapServlet), "/ws/*");

        server = new Server(mockPort);
        server.setHandler(servletHandler);
        server.start();

        BusFactory.setDefaultBus(soapServlet.getBus());
        Endpoint.publish("/arbeidsgiver", arbeidsgiver, TestUtil.inInterceptor(environmentConfig));
        Endpoint.publish("/behandleArbeidsgiver", behandleArbeidsgiver, TestUtil.inInterceptor(environmentConfig));
    }

    @Before
    public void setup() {
        reset(arbeidsgiver, behandleArbeidsgiver, kafkaConsumer);
    }

    @AfterClass
    public static void tearDown() {
        bankAccountNumberChannel.shutdown();
        kafkaEnvironment.tearDown();
    }

    @Test
    public void testValidBankAccounntNumberUpdate() throws Exception {
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
    }

    @Test
    public void testInvalidOrgStructureMissingUnderenhetInAAreg() throws Exception {
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

    @Test
    public void verifySoapFaultCausesNoRetries() throws Exception {
        when(arbeidsgiver.hentOrganisasjon(any())).thenThrow(new RuntimeException("Invalid modulus check"));

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

    @Test
    public void testRetriesDuringConnectionTimeout() throws Exception {
        when(arbeidsgiver.hentOrganisasjon(any()))
                .then((i) -> {
                    Thread.sleep(1000);
                    return DomainObjectUtils.defaultTestResponse();
                })
                .thenReturn(DomainObjectUtils.defaultTestResponse());

        kafkaProducer.send(new ProducerRecord<>(environmentConfig.bankaccountNumberChangedTopic, TestUtil.defaultExternalAttachment()));

        verify(arbeidsgiver, timeout(30000).times(2)).hentOrganisasjon(any());
        verify(kafkaConsumer, timeout(30000).times(1)).commitSync();

        verify(behandleArbeidsgiver, timeout(30000).times(1)).oppdaterKontonummer(any());
    }

    @Test
    public void testRetriesDuringDowntime() throws Exception {
        server.stop();
        server.join();

        when(arbeidsgiver.hentOrganisasjon(any())).thenReturn(defaultTestResponse());

        kafkaProducer.send(new ProducerRecord<>(environmentConfig.bankaccountNumberChangedTopic, TestUtil.defaultExternalAttachment()));

        verify(kafkaConsumer, timeout(30000).atLeast(2)).poll(anyLong());

        createJettyServer();

        verify(behandleArbeidsgiver, timeout(30000).times(1)).oppdaterKontonummer(any());
    }
}
