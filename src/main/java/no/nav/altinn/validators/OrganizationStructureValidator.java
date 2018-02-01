package no.nav.altinn.validators;

import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.RelatertOrganisasjonSammendrag;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;

import java.util.List;
import java.util.Optional;

public class OrganizationStructureValidator {

    public static boolean validateOrganizationStructure(HentOrganisasjonResponse aaregResponse, OppdaterKontonummerRequest update) {

        for (KontonummerOppdatering bankAccountUpdate : update.getUnderliggendeBedriftListe()) {
            if (bankAccountUpdate.getOrgNr() == null || bankAccountUpdate.getOrgNr().trim().isEmpty())
                return false;
            Optional<RelatertOrganisasjonSammendrag> daughterOrganization = findDaughterOrganization(aaregResponse.getBarneorganisasjonListe(), bankAccountUpdate.getOrgNr());
            if (!daughterOrganization.isPresent() || !daughterOrganization.get().getStatus().getKode().equals("1"))
                return false;
        }
        return true;

    }

    private static Optional<RelatertOrganisasjonSammendrag> findDaughterOrganization(List<RelatertOrganisasjonSammendrag> daughterOrganizations, String orgNumber) {
        return daughterOrganizations.stream()
                .filter(org -> org.getOrgNr().equals(orgNumber))
                .findFirst();
    }
}
