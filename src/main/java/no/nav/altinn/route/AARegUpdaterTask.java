package no.nav.altinn.route;

import io.prometheus.client.Counter;
import io.reactivex.functions.Consumer;
import no.nav.altinn.messages.ExtractedMessage;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AARegUpdaterTask implements Consumer<ExtractedMessage<OppdaterKontonummerRequest>> {
    private final BehandleArbeidsgiver handleEmployer;
    private static final Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private final static Counter SUCCESSFUL_MESSAGE_COUNTER = Counter.build().name("successful_message_count")
            .help("Counts the number of successful messages").register();

    public AARegUpdaterTask(BehandleArbeidsgiver handleEmployer) {
        this.handleEmployer = handleEmployer;
    }

    @Override
    public void accept(ExtractedMessage<OppdaterKontonummerRequest> extractedMessage) throws Exception {
        OppdaterKontonummerRequest updateBankAccountRequest = extractedMessage.updateRequest;
        handleEmployer.oppdaterKontonummer(updateBankAccountRequest);
        log.info("Successfully updated the account number for: {}", updateBankAccountRequest.getOverordnetEnhet().getOrgNr());
        SUCCESSFUL_MESSAGE_COUNTER.inc();
    }
}