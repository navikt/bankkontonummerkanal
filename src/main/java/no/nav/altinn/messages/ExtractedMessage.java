package no.nav.altinn.messages;

public class ExtractedMessage<T> {
    public final IncomingMessage incomingMessage;
    public final T updateRequest;

    public ExtractedMessage(IncomingMessage incomingMessage, T updateRequest) {
        this.incomingMessage = incomingMessage;
        this.updateRequest = updateRequest;
    }
}
