package no.nav.altinn.xmlextractor;

import no.nav.altinn.messages.ExtractedMessage;
import no.nav.altinn.messages.IncomingMessage;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.junit.Assert;
import org.junit.Test;

import static no.nav.altinn.utils.XmlUtils.*;

public class BankAccountXmlExtractorTest {

    @Test
    public void verifyExtractsBankAccountNumberIncomingMessage() throws Exception{
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message.xml");

        Assert.assertEquals("Check if a kontonummer is correctly extracted from incomming message","12345678912", oppdaterKontonummerRequest.getOverordnetEnhet().getKontonummer() );
    }

    @Test
    public void shouldSetOrgNrToOppdaterKontonummerRequest() throws Exception{
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message.xml");

        Assert.assertEquals("987654321", oppdaterKontonummerRequest.getOverordnetEnhet().getOrgNr());
    }


    @Test
    public void verifyPersonindentifikatorMappedToSporingsdetaljsFnr() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message.xml");

        Assert.assertEquals("12345678912", oppdaterKontonummerRequest.getSporingsdetalj().getFnr());
    }

    @Test
    public void verifyEmptyUnderliggendeBedriftListeWhenMissing() throws Exception{
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message_without_daughter_organizations.xml");

        Assert.assertEquals(0, oppdaterKontonummerRequest.getUnderliggendeBedriftListe().size() );
    }

    @Test
    public void verifyUnderenhetsNyttBankkontonummerMappedToUnderliggendeBedriftsKontonummer() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message.xml");

        Assert.assertEquals("12345678913", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(0).getKontonummer() );
    }

    @Test
    public void verifyUnderenhetsNyttBankkontonummerMappedToUnderliggendeBedriftsKontonummerWithDaughterOrganization() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message_with_two_daughter_organizations.xml");

        Assert.assertEquals("12345678914", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(1).getKontonummer() );
    }

    @Test
    public void verifyUnderenhetsOrganisasjonsnummerMappedToUnderliggendeBedriftListesOrgNr() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message.xml");

        Assert.assertEquals("987654322", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(0).getOrgNr() );
    }

    @Test
    public void verifyUnderenhetsOrganisasjonsnummerMappedToUnderliggendeBedriftListesOrgNrWithDaughterOrganization() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message_with_two_daughter_organizations.xml");

        Assert.assertEquals("987654323", oppdaterKontonummerRequest.getUnderliggendeBedriftListe().get(1).getOrgNr() );
    }

    @Test
    public void verifyTwoUnderenheterMappedToTwoUnderliggendeBedriftListe() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message_with_two_daughter_organizations.xml");

        Assert.assertEquals(2, oppdaterKontonummerRequest.getUnderliggendeBedriftListe().size() );
    }


    @Test
    public void verifyTransaksjonsIdIsSet() throws Exception {
        String xmlMessage = readXml("/xmlextractor/message.xml");

        String archRef= "77424064";
        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef(archRef)
                .setBatch(xmlMessage)
                .setSc("2896")
                .setSec("87").build();

        IncomingMessage incomingMessage = new IncomingMessage(xmlMessage,externalAttachment);

        ExtractedMessage extractedMessage = XML_EXTRACTOR.apply(incomingMessage);
        OppdaterKontonummerRequest oppdaterKontonummerRequest = (OppdaterKontonummerRequest) extractedMessage.updateRequest;

        Assert.assertEquals("Check if ArchRef is transfered to TransaksjonsId", archRef, oppdaterKontonummerRequest.getSporingsdetalj().getTransaksjonsId());
    }

    @Test
    public void verifySporingsdetaljsInnsendtTidspunktIsSet() throws Exception {
        String xmlMessage = readXml("/xmlextractor/message.xml");

        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setArchRef("77424064")
                .setBatch(xmlMessage)
                .setSc("2896")
                .setSec("87").build();

        IncomingMessage incomingMessage = new IncomingMessage(xmlMessage,externalAttachment);

        ExtractedMessage extractedMessage = XML_EXTRACTOR.apply(incomingMessage);
        OppdaterKontonummerRequest oppdaterKontonummerRequest = (OppdaterKontonummerRequest) extractedMessage.updateRequest;

        Assert.assertNotNull("Check if InnsendtTidspunkt is correct, we compare on the minute",
                oppdaterKontonummerRequest.getSporingsdetalj().getInnsendtTidspunkt());
    }

    @Test
    public void verifyMissingOverordnetEnhetsKontonummerMappedToNull() throws Exception {
        OppdaterKontonummerRequest oppdaterKontonummerRequest = unmarshallXML("/xmlextractor/message_with_missing_bank_account_number.xml");

        Assert.assertEquals(null, oppdaterKontonummerRequest.getOverordnetEnhet().getKontonummer());
    }
}
