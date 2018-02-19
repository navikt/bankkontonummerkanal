package no.nav.altinn.xmlextractor;

import io.reactivex.functions.Function;
import no.nav.altinn.messages.ExtractedMessage;
import no.nav.altinn.messages.IncomingMessage;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.Sporingsdetalj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.io.StringReader;
import java.util.GregorianCalendar;

public class BankAccountXmlExtractor implements Function<IncomingMessage, ExtractedMessage<OppdaterKontonummerRequest>> {
    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

    private static final String FORM_DATA_FIELD = "FormData";

    private final static String NEW_ACCOUNT_NUMBER_FIELD = "nyttBankkontonummer";
    private final static String ORGANISATION_NUMBER_FIELD = "organisasjonsnummer";
    private final static String SISTER_ORGANISATION_FIELD = "underenhet";
    private final static String PERSON_NUMBER_FIELD = "personidentifikator";

    private final static Logger log = LoggerFactory.getLogger(BankAccountXmlExtractor.class);

    public OppdaterKontonummerRequest buildSoapRequestFromAltinnPayload(Reader xmlReader) throws XMLStreamException {
        KontonummerOppdatering bankAccountUpdate = new KontonummerOppdatering();
        Sporingsdetalj trackingDetail = new Sporingsdetalj();

        OppdaterKontonummerRequest updateRequest = new OppdaterKontonummerRequest();
        updateRequest.setOverordnetEnhet(bankAccountUpdate);
        updateRequest.setSporingsdetalj(trackingDetail);

        String formData = extractFormData(xmlReader);
        log.debug("Extracted FormData: {}", formData);

        XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(formData));

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLEvent.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case NEW_ACCOUNT_NUMBER_FIELD:
                        bankAccountUpdate.setKontonummer(getText(reader));
                        break;

                    case ORGANISATION_NUMBER_FIELD:
                        bankAccountUpdate.setOrgNr(getText(reader));
                        break;

                    case PERSON_NUMBER_FIELD:
                        if (reader.hasText()) {
                            trackingDetail.setFnr(getText(reader));
                        }
                        break;

                    case SISTER_ORGANISATION_FIELD:
                        // Since we might get an empty list of sister companies we count fields
                        int fieldsAdded = 0;
                        KontonummerOppdatering sisterBankAccountUpdate = new KontonummerOppdatering();
                        while (reader.hasNext()) {
                            event = reader.next();
                            if (event == XMLEvent.END_ELEMENT && reader.getLocalName().equals(SISTER_ORGANISATION_FIELD))
                                break;

                            switch (reader.getLocalName()) {
                                case NEW_ACCOUNT_NUMBER_FIELD:
                                    sisterBankAccountUpdate.setKontonummer(getText(reader));
                                    break;
                                case ORGANISATION_NUMBER_FIELD:
                                    sisterBankAccountUpdate.setOrgNr(getText(reader));
                                    break;
                            }
                            fieldsAdded ++;
                        }
                        if (fieldsAdded > 0) {
                            updateRequest.getUnderliggendeBedriftListe().add(sisterBankAccountUpdate);
                        }

                        break;
                }
            }
        }

        return updateRequest;
    }

    private String getText(XMLStreamReader reader) throws XMLStreamException {
        int event = -1;
        if (!reader.hasNext() || (event = reader.next()) != XMLEvent.CHARACTERS)
            throw new RuntimeException("Expected CHARACTERS, got " + event);
        return reader.getText();
    }

    private String extractFormData(Reader xml) throws XMLStreamException {
        XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(xml);
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLEvent.START_ELEMENT) {
                    if (FORM_DATA_FIELD.equals(reader.getLocalName())) {
                        getText(reader);
                        int event = -1;
                        if (!reader.hasNext() || (event = reader.next()) != XMLEvent.CDATA)
                            throw new RuntimeException("Expected CDATA, got " + event);
                        return reader.getText();
                    }
                }
            }
            throw new RuntimeException("Could not find field FormData");
        } finally {
            System.out.println(reader.next());
            reader.close();
        }
    }

    @Override
    public ExtractedMessage<OppdaterKontonummerRequest> apply(IncomingMessage incomingMessage) throws Exception {
        OppdaterKontonummerRequest updateBankAccountRequest = buildSoapRequestFromAltinnPayload(new StringReader(incomingMessage.xmlMessage));
        DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();

        updateBankAccountRequest.getSporingsdetalj().setTransaksjonsId(incomingMessage.externalAttachment.getArchRef());
        updateBankAccountRequest.getSporingsdetalj().setInnsendtTidspunkt(datatypeFactory.newXMLGregorianCalendar(new GregorianCalendar()));
        return new ExtractedMessage<>(incomingMessage, updateBankAccountRequest);
    }
}
