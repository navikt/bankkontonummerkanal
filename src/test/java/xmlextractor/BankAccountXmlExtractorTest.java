package xmlextractor;

import no.nav.altinn.messages.ExtractedMessage;
import no.nav.altinn.messages.IncomingMessage;
import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.apache.cxf.helpers.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.GregorianCalendar;

public class BankAccountXmlExtractorTest {

    @Test
    public void shouldSetKontonummerToOppdaterKontonummerRequest() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")))));

        Assert.assertEquals("Check if a kontonummer is correctly extracted from incomming message","54130612345", oppdaterKontonummerRequest.getOverordnetEnhet().getKontonummer() );
    }

    @Test
    public void shouldSetOrgNrToOppdaterKontonummerRequest() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")))));

        Assert.assertEquals("Check if a orgNr is correctly extracted from incomming message", "987654321", oppdaterKontonummerRequest.getOverordnetEnhet().getOrgNr());
    }


    @Test
    public void shouldSetFNRToOppdaterKontonummerRequest() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")))));

        Assert.assertEquals("Check if a FNR is correctly extracted from incomming message","1231344122", oppdaterKontonummerRequest.getSporingsdetalj().getFnr() );
    }


    @Test
    public void shouldSetUnderenhetToOppdaterKontonummerRequestWhenNoUnderenhet() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")))));

        Assert.assertEquals(0, oppdaterKontonummerRequest.getUnderliggendeBedriftListe().size() );
    }

    @Test
    public void shouldSetUnderenhetAndNyttBankkontonummerToOppdaterKontonummerRequestWhenNoUnderenhet() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")))));

        Assert.assertEquals(0, oppdaterKontonummerRequest.getUnderliggendeBedriftListe().size() );
    }

    @Test
    public void shouldSetUnderenhetsNyttBankkontonummerToOppdaterKontonummerRequestWhenKontonrIs54130612346() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithTwoUnderenheter.xml")))));

        Assert.assertEquals("54130612346", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(0).getKontonummer() );
    }

    @Test
    public void shouldSetUnderenhetsNyttBankkontonummerToOppdaterKontonummerRequestWhenKontonrIs54130612347()throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithTwoUnderenheter.xml")))));

        Assert.assertEquals("54130612347", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(1).getKontonummer() );
    }

    @Test
    public void shouldSetUnderenhetsOrganisasjonsnummerToOppdaterKontonummerRequestWhenOrgNrIs987654322()throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithTwoUnderenheter.xml")))));

        Assert.assertEquals("987654321", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(0).getOrgNr() );
    }

    @Test
    public void shouldSetUnderenhetsOrganisasjonsnummerToOppdaterKontonummerRequestWhenOrgNrIs987654323()throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithTwoUnderenheter.xml")))));

        Assert.assertEquals("987654323", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(1).getOrgNr() );
    }

    @Test
    public void shouldSetTwoUnderliggendeBedriftListeToOppdaterKontonummerRequestWhenTwoUnderenhet() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithTwoUnderenheter.xml")))));

        Assert.assertEquals(2, oppdaterKontonummerRequest.getUnderliggendeBedriftListe().size() );
    }


    @Test
    public void shouldSetTransaksjonsId() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        String xmlMessage = IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")));
        String archRef= "77424064";
        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef(archRef)
                .setBatch(xmlMessage)
                .setSc("2896")
                .setSec("87").build();

        IncomingMessage incomingMessage = new IncomingMessage(xmlMessage,externalAttachment);

        ExtractedMessage extractedMessage = bankAccountXmlExtractor.apply(incomingMessage);
        OppdaterKontonummerRequest oppdaterKontonummerRequest = (OppdaterKontonummerRequest) extractedMessage.updateRequest;

        Assert.assertEquals("Check if ArchRef is transfered to TransaksjonsId", archRef, oppdaterKontonummerRequest.getSporingsdetalj().getTransaksjonsId());
    }

    @Test
    public void shouldSetInnsendtTidspunkt() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        String xmlMessage = IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")));

        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef("77424064")
                .setBatch(xmlMessage)
                .setSc("2896")
                .setSec("87").build();

        IncomingMessage incomingMessage = new IncomingMessage(xmlMessage,externalAttachment);

        ExtractedMessage extractedMessage = bankAccountXmlExtractor.apply(incomingMessage);
        OppdaterKontonummerRequest oppdaterKontonummerRequest = (OppdaterKontonummerRequest) extractedMessage.updateRequest;

        XMLGregorianCalendar xmlGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());

        Assert.assertEquals("Check if InnsendtTidspunkt is correct, we compare on the minute",
                xmlGregorianCalendar.getMinute(), oppdaterKontonummerRequest.getSporingsdetalj().getInnsendtTidspunkt().getMinute());
    }

    @Test
    public void shouldSetNyttBankkontonummerToNull() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest = bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithnyttBankkontonummerNull.xml")))));

        Assert.assertEquals(null, oppdaterKontonummerRequest.getOverordnetEnhet().getKontonummer());
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionWhenNoCDATA() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

       bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
               new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageNoCDATA.xml")))));

    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionWhenNoFormdata() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithNoFormData.xml")))));

    }


}
