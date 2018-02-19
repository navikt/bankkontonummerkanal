package no.nav.altinn.messages;

import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;

public class IncomingMessage {
    public final String xmlMessage;
    public final ExternalAttachment externalAttachment;

    public IncomingMessage(String xmlMessage, ExternalAttachment externalAttachment) {
        this.xmlMessage = xmlMessage;
        this.externalAttachment = externalAttachment;
    }
}
