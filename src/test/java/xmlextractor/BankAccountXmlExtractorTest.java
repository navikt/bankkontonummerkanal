package xmlextractor;

import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.apache.cxf.helpers.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.StringReader;

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

    /*
    @Test
    public void shouldtestApply() throws Exception{

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        String xmlMessage = IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessage.xml")));

        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef("77424064")
                .setBatch(xmlMessage)
                .setSc("2896")
                .setSec("96").build();

        IncomingMessage incomingMessage = new IncomingMessage(xmlMessage,externalAttachment);

        ExtractedMessage extractedMessage = bankAccountXmlExtractor.apply(incomingMessage);
        OppdaterKontonummerRequest oppdaterKontonummerRequest = (OppdaterKontonummerRequest) extractedMessage.updateRequest;

        Assert.assertEquals("Check if a FNR is correctly extracted from incomming message","1231344122", oppdaterKontonummerRequest.getSporingsdetalj().getFnr() );
    }
    */

}
