package no.nav.altinn.validators;

import io.prometheus.client.Summary;
import no.nav.altinn.route.BankAccountNumberRoute;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonRequest;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.RelatertOrganisasjonSammendrag;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.HentOrganisasjonOrganisasjonIkkeFunnet;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

public class AARegOrganisationStructureValidator {
    private static final Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final Arbeidsgiver employer;
    public AARegOrganisationStructureValidator(Arbeidsgiver employer) {
        this.employer = employer;
    }

    public static Result validateOrganizationStructure(HentOrganisasjonResponse aaregResponse,
                                                       OppdaterKontonummerRequest update, String archiveReference) {
        if (update.getOverordnetEnhet().getOrgNr() == null || update.getOverordnetEnhet().getOrgNr().isEmpty()) {
            return Result.MissingHovedenhetOrgNr;
        }

        for (KontonummerOppdatering bankAccountUpdate : update.getUnderliggendeBedriftListe()) {
            if (bankAccountUpdate.getOrgNr() == null || bankAccountUpdate.getOrgNr().trim().isEmpty()) {
                return Result.MissingUnderenhetOrgNr;
            }

            Optional<RelatertOrganisasjonSammendrag> daughterOrganization =
                    findDaughterOrganization(aaregResponse.getBarneorganisasjonListe(), bankAccountUpdate.getOrgNr());

            if (!daughterOrganization.isPresent()) {
                log.warn("Daughter organization with organization number {} not found in the parent organization {} {}",
                        keyValue("daughterOrgNr", bankAccountUpdate.getOrgNr()),
                        keyValue("orgNr", update.getOverordnetEnhet().getOrgNr()),
                        keyValue("archRef", archiveReference));
                return Result.InvalidStructure;
            }
        }
        return Result.Ok;

    }

    private static Optional<RelatertOrganisasjonSammendrag> findDaughterOrganization(
            List<RelatertOrganisasjonSammendrag> daughterOrganizations, String orgNumber) {
        return daughterOrganizations.stream()
                .filter(org -> org.getOrgNr().equals(orgNumber))
                .filter(org -> org.getStatus().getKode().equals("1"))
                .findFirst();
    }

    public Result validate(OppdaterKontonummerRequest updateBankAccountRequest, String archiveReference)
            throws HentOrganisasjonOrganisasjonIkkeFunnet {
        log.debug("Update bank account request {}", updateBankAccountRequest);
        log.debug("Parent company {}", updateBankAccountRequest.getOverordnetEnhet());

        try (Summary.Timer aaregQueryTimer = BankAccountNumberRoute.AAREG_QUERY_TIMER.startTimer()) {
            HentOrganisasjonRequest getOrganisationRequest = new HentOrganisasjonRequest();
            getOrganisationRequest.setOrgNr(updateBankAccountRequest.getOverordnetEnhet().getOrgNr());
            getOrganisationRequest.setHentRelaterteOrganisasjoner(true);

            HentOrganisasjonResponse organisationResponse = employer.hentOrganisasjon(getOrganisationRequest);
            aaregQueryTimer.observeDuration();
            return validateOrganizationStructure(organisationResponse, updateBankAccountRequest, archiveReference);
        }
    }

    public enum Result {
        Ok,
        InvalidStructure,
        MissingHovedenhetOrgNr,
        MissingUnderenhetOrgNr
    }
}
