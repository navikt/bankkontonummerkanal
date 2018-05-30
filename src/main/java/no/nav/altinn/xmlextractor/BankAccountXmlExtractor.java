package no.nav.altinn.xmlextractor;

import no.nav.altinn.route.BankAccountNumberRoute;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.Sporingsdetalj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.io.StringReader;
import java.util.GregorianCalendar;

public class BankAccountXmlExtractor {
    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

    private static final String FORM_DATA_FIELD = "FormData";

    private final static String NEW_ACCOUNT_NUMBER_FIELD = "nyttBankkontonummer";
    private final static String ORGANISATION_NUMBER_FIELD = "organisasjonsnummer";
    private final static String SISTER_ORGANISATION_FIELD = "underEnhet";
    private final static String PERSON_NUMBER_FIELD = "personidentifikator";

    private final static Logger log = LoggerFactory.getLogger(BankAccountXmlExtractor.class);

    public OppdaterKontonummerRequest buildSoapRequestFromAltinnPayload(Reader xmlReader) throws XMLStreamException {
        KontonummerOppdatering bankAccountUpdate = new KontonummerOppdatering();
        Sporingsdetalj trackingDetail = new Sporingsdetalj();

        OppdaterKontonummerRequest updateRequest = new OppdaterKontonummerRequest();
        updateRequest.setOverordnetEnhet(bankAccountUpdate);
        updateRequest.setSporingsdetalj(trackingDetail);

        String formData = extractFormData(xmlReader);
        if (log.isDebugEnabled()) {
            log.debug("Extracted FormData: {}", formData.replaceAll(BankAccountNumberRoute.ATTACHMENTS_REGEX,
                    BankAccountNumberRoute.ATTACHMENTS_REGEX));
        }

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
                        trackingDetail.setFnr(getText(reader));
                        break;

                    case SISTER_ORGANISATION_FIELD:
                        // Since we might get an empty list of sister companies we count fields
                        int fieldsAdded = 0;
                        KontonummerOppdatering sisterBankAccountUpdate = new KontonummerOppdatering();
                        while (reader.hasNext()) {
                            event = reader.next();
                            if (event == XMLEvent.END_ELEMENT && reader.getLocalName().equals(SISTER_ORGANISATION_FIELD))
                                break;

                            if (event == XMLEvent.START_ELEMENT) {
                                switch (reader.getLocalName()) {
                                    case NEW_ACCOUNT_NUMBER_FIELD:
                                        sisterBankAccountUpdate.setKontonummer(getText(reader));
                                        break;
                                    case ORGANISATION_NUMBER_FIELD:
                                        sisterBankAccountUpdate.setOrgNr(getText(reader));
                                        break;
                                }
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
        if (!reader.hasNext() || reader.next() != XMLEvent.CHARACTERS)
            return null;
        return reader.getText();
    }

    private String extractFormData(Reader xml) throws XMLStreamException {
        XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(xml);
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLEvent.START_ELEMENT) {
                    if (FORM_DATA_FIELD.equals(reader.getLocalName())) {
                        return reader.getElementText();
                    }
                }
            }
            throw new RuntimeException("Could not find field FormData");
        } finally {
            reader.close();
        }
    }

    public OppdaterKontonummerRequest extract(ExternalAttachment externalAttachment) throws XMLStreamException, DatatypeConfigurationException {
        OppdaterKontonummerRequest updateBankAccountRequest = buildSoapRequestFromAltinnPayload(new StringReader(externalAttachment.getBatch()));

        updateBankAccountRequest.getSporingsdetalj().setTransaksjonsId(externalAttachment.getArchiveReference());
        updateBankAccountRequest.getSporingsdetalj().setInnsendtTidspunkt(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()));
        return updateBankAccountRequest;
    }
}
