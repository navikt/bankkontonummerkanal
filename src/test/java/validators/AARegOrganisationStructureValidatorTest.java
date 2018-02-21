package validators;

import no.nav.altinn.validators.AARegOrganisationStructureValidator;
import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.virksomhet.part.arbeidsgiver.v1.Status;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.RelatertOrganisasjonSammendrag;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.apache.cxf.helpers.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.StringReader;

public class AARegOrganisationStructureValidatorTest {

    @Test
    public void shouldValidateOrganizationStructureToTrue() throws Exception{

        HentOrganisasjonResponse hentOrganisasjonResponse1= new HentOrganisasjonResponse();
        OppdaterKontonummerRequest oppdaterKontonummerRequest= new OppdaterKontonummerRequest();

        Boolean validateOrganizationStructure = AARegOrganisationStructureValidator.validateOrganizationStructure(hentOrganisasjonResponse1, oppdaterKontonummerRequest);

        Assert.assertTrue(validateOrganizationStructure);
    }

    @Test
    public void shouldValidateOrganizationStructureToFalseWhenOrgNrIsNull () throws Exception{

        HentOrganisasjonResponse hentOrganisasjonResponse= new HentOrganisasjonResponse();

        BankAccountXmlExtractor bankAccountXmlExtractor = new BankAccountXmlExtractor();

        OppdaterKontonummerRequest oppdaterKontonummerRequest= bankAccountXmlExtractor.buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageNoOrgNrUnderEnhet.xml")))));

        Boolean validateOrganizationStructure = AARegOrganisationStructureValidator.validateOrganizationStructure(hentOrganisasjonResponse, oppdaterKontonummerRequest);

        Assert.assertFalse(validateOrganizationStructure);
    }

    @Test
    public void shouldValidateOrganizationStructureToFalseWhenDaughterOrganizationIsPresent() throws Exception{

        OppdaterKontonummerRequest oppdaterKontonummerRequest= new BankAccountXmlExtractor().buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageWithTwoUnderenheter.xml")))));

        Boolean validateOrganizationStructure = AARegOrganisationStructureValidator.validateOrganizationStructure(
                new HentOrganisasjonResponse(), oppdaterKontonummerRequest);

        Assert.assertFalse(validateOrganizationStructure);
    }


    @Test
    public void shouldValidateOrganizationStructureToFalseWhenDaughterOrganizationHasStatusKode0() throws Exception{

        HentOrganisasjonResponse hentOrganisasjonResponse= new HentOrganisasjonResponse();

        RelatertOrganisasjonSammendrag datterOrganisasjon = new RelatertOrganisasjonSammendrag();
        datterOrganisasjon.setOrgNr("987654322");
        Status datterStatus = new Status();
        datterStatus.setKode("0");
        datterOrganisasjon.setStatus(datterStatus);

        hentOrganisasjonResponse.getBarneorganisasjonListe().add(datterOrganisasjon);

        OppdaterKontonummerRequest oppdaterKontonummerRequest= new BankAccountXmlExtractor().buildSoapRequestFromAltinnPayload(
                new StringReader(IOUtils.toString(new InputStreamReader(getClass().getResourceAsStream("/xmlextractor/xmlMessageDaughterOrganizationStatusKode1.xml")))));

        Boolean validateOrganizationStructure = AARegOrganisationStructureValidator.validateOrganizationStructure(hentOrganisasjonResponse, oppdaterKontonummerRequest);

        Assert.assertFalse(validateOrganizationStructure);
    }


}
