package no.nav.altinn.validators;

import io.prometheus.client.Gauge;
import io.reactivex.functions.Predicate;
import no.nav.altinn.messages.ExtractedMessage;
import no.nav.altinn.route.BankAccountNumberRoute;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonRequest;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.RelatertOrganisasjonSammendrag;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class AARegOrganisationStructureValidator implements Predicate<ExtractedMessage<OppdaterKontonummerRequest>> {
    private static final Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final Arbeidsgiver employer;
    public AARegOrganisationStructureValidator(Arbeidsgiver employer) {
        this.employer = employer;
    }

    public static Result validateOrganizationStructure(HentOrganisasjonResponse aaregResponse, OppdaterKontonummerRequest update) {
        if (update.getOverordnetEnhet().getOrgNr() == null || update.getOverordnetEnhet().getOrgNr().isEmpty()) {
            return Result.MissingHovedenhetOrgNr;
        }
        if (update.getOverordnetEnhet().getKontonummer() == null || update.getOverordnetEnhet().getKontonummer().isEmpty()) {
            return Result.MissingHovedenhetKontonummer;
        }

        for (KontonummerOppdatering bankAccountUpdate : update.getUnderliggendeBedriftListe()) {
            if (bankAccountUpdate.getOrgNr() == null || bankAccountUpdate.getOrgNr().trim().isEmpty()) {
                return Result.MissingUnderenhetOrgNr;
            }
            if (bankAccountUpdate.getKontonummer() == null || bankAccountUpdate.getKontonummer().isEmpty()) {
                return Result.MissingUnderenhetKontonummer;
            }

            Optional<RelatertOrganisasjonSammendrag> daughterOrganization = findDaughterOrganization(aaregResponse.getBarneorganisasjonListe(), bankAccountUpdate.getOrgNr());

            if (!daughterOrganization.isPresent())
                return Result.InvalidStructure;
            if (!daughterOrganization.get().getStatus().getKode().equals("1"))
                return Result.StatusCodeIs0;
        }
        return Result.Ok;

    }

    private static Optional<RelatertOrganisasjonSammendrag> findDaughterOrganization(List<RelatertOrganisasjonSammendrag> daughterOrganizations, String orgNumber) {
        return daughterOrganizations.stream()
                .filter(org -> org.getOrgNr().equals(orgNumber))
                .findFirst();
    }

    @Override
    public boolean test(ExtractedMessage<OppdaterKontonummerRequest> extractedMessage) throws Exception {
        OppdaterKontonummerRequest updateBankAccountRequest = extractedMessage.updateRequest;
        log.debug("Update bank account request {}", updateBankAccountRequest);
        log.debug("Parent company {}", updateBankAccountRequest.getOverordnetEnhet());

        try (Gauge.Timer queryTimer = BankAccountNumberRoute.AAREG_QUERY_TIMER.startTimer()) {
            HentOrganisasjonRequest getOrganisationRequest = new HentOrganisasjonRequest();
            getOrganisationRequest.setOrgNr(updateBankAccountRequest.getOverordnetEnhet().getOrgNr());
            getOrganisationRequest.setHentRelaterteOrganisasjoner(true);

            HentOrganisasjonResponse organisationResponse = employer.hentOrganisasjon(getOrganisationRequest);
            return validateOrganizationStructure(organisationResponse, updateBankAccountRequest) == Result.Ok;
        }
    }

    public enum Result {
        Ok,
        InvalidStructure,
        MissingHovedenhetKontonummer,
        MissingUnderenhetKontonummer,
        MissingHovedenhetOrgNr,
        MissingUnderenhetOrgNr,
        StatusCodeIs0
    }
}
