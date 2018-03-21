package no.nav.altinn.utils;

import no.nav.virksomhet.part.arbeidsgiver.v1.Kontonummer;
import no.nav.virksomhet.part.arbeidsgiver.v1.Organisasjon;
import no.nav.virksomhet.part.arbeidsgiver.v1.Status;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.RelatertOrganisasjonSammendrag;

import java.util.Arrays;

public class DomainObjectUtils {
    public static Organisasjon buildOrganization(String orgNr, String bankAccountNumber, String statusCode) {
        Kontonummer kontonummer = new Kontonummer();
        kontonummer.setKontonummer(bankAccountNumber);

        Status status = new Status();
        status.setKode(statusCode);

        Organisasjon organization = new Organisasjon();
        organization.setOrgNr(orgNr);
        organization.setBankkontonr(kontonummer);
        organization.setStatus(status);
        return organization;
    }

    public static RelatertOrganisasjonSammendrag buildRelatedOrganization(String orgNr, String bankAccountNumber, String statusCode) {
        Kontonummer kontonummer = new Kontonummer();
        kontonummer.setKontonummer(bankAccountNumber);

        Status status = new Status();
        status.setKode(statusCode);

        RelatertOrganisasjonSammendrag organization = new RelatertOrganisasjonSammendrag();
        organization.setOrgNr(orgNr);
        organization.setBankkontonr(kontonummer);
        organization.setStatus(status);
        return organization;
    }

    public static HentOrganisasjonResponse buildResponse(Organisasjon organization, RelatertOrganisasjonSammendrag... daughterOrganizations) {
        HentOrganisasjonResponse response = new HentOrganisasjonResponse();
        response.setOrganisasjon(organization);
        response.getBarneorganisasjonListe().addAll(Arrays.asList(daughterOrganizations));

        return response;
    }

    public static HentOrganisasjonResponse defaultTestResponse() {
        return buildResponse(
                buildOrganization("987654321", "123", "1"),
                buildRelatedOrganization("987654322", "234", "1"));
    }
}
