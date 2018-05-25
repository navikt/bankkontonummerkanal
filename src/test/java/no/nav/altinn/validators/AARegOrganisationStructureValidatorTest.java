package no.nav.altinn.validators;

import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.junit.Test;

import static no.nav.altinn.utils.DomainObjectUtils.*;
import static no.nav.altinn.utils.XmlUtils.*;
import static no.nav.altinn.validators.AARegOrganisationStructureValidator.*;
import static org.junit.Assert.*;

public class AARegOrganisationStructureValidatorTest {

    @Test
    public void testValidOrganizationStructure() throws Exception {
        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message.xml");

        HentOrganisasjonResponse response = defaultTestResponse();

        assertEquals(Result.Ok, validateOrganizationStructure(response, request));
    }

    @Test
    public void testReturnsInvalidStructureWhenAARegIsMissingDaughterOrganization() throws Exception{

        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message_with_two_daughter_organizations.xml");

        HentOrganisasjonResponse response = defaultTestResponse();

        assertEquals(Result.InvalidStructure, validateOrganizationStructure(response, request));
    }


    @Test
    public void testReturnsStatusCodedIs0WhenDaughterOrganizationHasStatusCode0() throws Exception {
        HentOrganisasjonResponse response = buildResponse(
                buildOrganization("987654321", "123", "1"),
                buildRelatedOrganization("987654322", "234", "0"));

        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message_with_daughter_organization_status_code1.xml");
        assertEquals(Result.InvalidStructure, validateOrganizationStructure(response, request));
    }

    @Test
    public void testReturnsInvalidStructureOnInvalidDaughterOrganization() throws Exception {
        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message.xml");

        HentOrganisasjonResponse response = buildResponse(
                buildOrganization("987654321", "123", "1"),
                buildRelatedOrganization("987654321", "234", "1"));

        assertEquals(Result.InvalidStructure, validateOrganizationStructure(response, request));
    }

    @Test
    public void testReturnsMissingHovedenhetKontonummer() throws Exception {
        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message_with_missing_bank_account_number.xml");

        HentOrganisasjonResponse response = defaultTestResponse();

        assertEquals(Result.MissingHovedenhetKontonummer, validateOrganizationStructure(response, request));
    }

    @Test
    public void testReturnsMissingHovedenhetOrganizationNumber() throws Exception {
        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message_with_missing_organization_number.xml");

        HentOrganisasjonResponse response = defaultTestResponse();

        assertEquals(Result.MissingHovedenhetOrgNr, validateOrganizationStructure(response, request));
    }

    @Test
    public void testReturnsMissingUnderenhetKontonummer() throws Exception {
        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message_with_missing_bank_account_number_in_daughter_organization.xml");

        HentOrganisasjonResponse response = defaultTestResponse();

        assertEquals(Result.Ok, validateOrganizationStructure(response, request));
    }

    @Test
    public void testReturnsMissingUnderenhetOrganisasjonsnummer() throws Exception {
        OppdaterKontonummerRequest request = unmarshallXML("/xmlextractor/message_with_missing_organization_number_in_daughter_organization.xml");

        HentOrganisasjonResponse response = defaultTestResponse();

        assertEquals(Result.MissingUnderenhetOrgNr, validateOrganizationStructure(response, request));
    }
}
