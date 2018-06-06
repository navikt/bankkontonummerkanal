package no.nav.altinn.route;

import no.nav.altinn.utils.DomainObjectUtils;
import no.nav.altinn.utils.TestUtil;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.common.KafkaEnvironment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.*;

import javax.xml.soap.SOAPFactory;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import java.net.ConnectException;
import java.util.Collections;

import static no.nav.altinn.utils.XmlUtils.readXml;
import static org.mockito.Mockito.*;

public class BankAccountNumberRouteTest {
    private static final String TOPIC = "bank.account.test.topic";

    private static KafkaEnvironment kafkaEnvironment;
    private static BankAccountNumberRoute route;
    private static Arbeidsgiver employer;
    private static BehandleArbeidsgiver handleEmployer;
    private static KafkaConsumer<String, ExternalAttachment> consumer;
    private static KafkaProducer<String, ExternalAttachment> producer;
    private static SOAPFactory soapFactory;

    @BeforeClass
    public static void setupClass() throws Exception {
        soapFactory = SOAPFactory.newInstance();
        kafkaEnvironment = new KafkaEnvironment(1, Collections.singletonList(TOPIC), true, false, false);
        kafkaEnvironment.start();

        employer = mock(Arbeidsgiver.class);
        handleEmployer = mock(BehandleArbeidsgiver.class);

        consumer = spy(TestUtil.createConsumer(kafkaEnvironment));
        producer = TestUtil.createProducer(kafkaEnvironment);

        consumer.subscribe(Collections.singletonList(TOPIC));

        route = spy(new BankAccountNumberRoute(employer, handleEmployer, consumer, 500, 5));
        new Thread(route).start();
    }

    @AfterClass
    public static void tearDownClass() {
        route.stop();
    }

    @Before
    public void setup() {
        reset(employer, handleEmployer, route, consumer);
    }

    @Test
    public void testPollsMessages() {
        verify(consumer, timeout(5000).times(1)).poll(anyLong());
    }

    @Test
    public void testSoapFaultCausesCommitAndLogMessage() throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, TestUtil.defaultExternalAttachment()));

        when(employer.hentOrganisasjon(any())).thenThrow(new SOAPFaultException(soapFactory.createFault()));

        verify(consumer, timeout(10000).times(1)).commitSync();
        verify(route, timeout(10000).times(1)).logFailedMessage(any(), anyString(), any(), any());
    }

    @Test
    public void testRetriesOnRuntimeException() throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, TestUtil.defaultExternalAttachment()));

        when(employer.hentOrganisasjon(any()))
                .thenThrow(new RuntimeException("Retry test 1"))
                .thenThrow(new RuntimeException("Retry test 2"))
                .thenReturn(DomainObjectUtils.defaultTestResponse());

        verify(consumer, timeout(10000).times(1)).commitSync();
        verify(consumer, timeout(10000).atLeast(3)).poll(anyLong());
        verify(handleEmployer, timeout(10000).times(1)).oppdaterKontonummer(any());
    }

    @Test
    public void testBacksOffLoggingMessageAfter5Retries() throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, TestUtil.defaultExternalAttachment()));

        when(employer.hentOrganisasjon(any()))
                .thenThrow(new RuntimeException("Retry test 1"))
                .thenThrow(new RuntimeException("Retry test 2"))
                .thenThrow(new RuntimeException("Retry test 3"))
                .thenThrow(new RuntimeException("Retry test 4"))
                .thenThrow(new RuntimeException("Retry test 5"));

        verify(consumer, timeout(10000).times(5)).poll(anyLong());
        verify(consumer, timeout(10000).times(1)).commitSync();
        verify(route, timeout(10000)).logFailedMessage(any(), anyString(), any(), any());
    }

    @Test
    public void testRetriesWhenServerIsTemporaryDown() throws Exception {
        producer.send(new ProducerRecord<>(TOPIC, TestUtil.defaultExternalAttachment()));

        when(employer.hentOrganisasjon(any()))
                .thenThrow(new WebServiceException(new ConnectException("Connection refused (Connection refused)")))
                .thenReturn(DomainObjectUtils.defaultTestResponse());

        verify(consumer, timeout(10000).times(1)).commitSync();
        verify(consumer, timeout(10000).atLeast(2)).poll(anyLong());
        verify(handleEmployer, timeout(10000)).oppdaterKontonummer(any());
    }

    @Test
    public void testReturnsEarlyOnMissingNewBankAccountNumber() throws Exception {
        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchiveReference("TestArchRef")
                .setServiceCode("a")
                .setServiceEditionCode("b")
                .setBatch(readXml("/xmlextractor/message_no_new_bank_account_number.xml"))
                .build();

        route.handleMessage(new ConsumerRecord<>("testTopic", 0, 0, null, externalAttachment));

        verify(employer, never()).hentOrganisasjon(any());
        verify(handleEmployer, never()).oppdaterKontonummer(any());
    }
}
