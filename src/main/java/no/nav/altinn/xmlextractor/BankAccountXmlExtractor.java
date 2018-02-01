package no.nav.altinn.xmlextractor;

import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.Sporingsdetalj;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;

public class BankAccountXmlExtractor {
    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    private final static String NEW_ACCOUNT_NUMBER_FIELD = "nyttBankkontonummer";
    private final static String ORGANISATION_NUMBER_FIELD = "organisasjonsnummer";
    private final static String SISTER_ORGANISATION_FIELD = "underenhet";
    private final static String PERSON_NUMBER_FIELD = "personidentifikator";

    public OppdaterKontonummerRequest buildSoapRequestFromAltinnPayload(String xml) throws XMLStreamException {
        KontonummerOppdatering bankAccountUpdate = new KontonummerOppdatering();
        Sporingsdetalj trackingDetail = new Sporingsdetalj();

        OppdaterKontonummerRequest updateRequest = new OppdaterKontonummerRequest();
        updateRequest.setOverordnetEnhet(bankAccountUpdate);
        updateRequest.setSporingsdetalj(trackingDetail);

        XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(xml));

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLEvent.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case NEW_ACCOUNT_NUMBER_FIELD:
                        bankAccountUpdate.setKontonummer(reader.getText());
                        break;
                    case ORGANISATION_NUMBER_FIELD:
                        bankAccountUpdate.setOrgNr(reader.getText());
                        break;
                    case PERSON_NUMBER_FIELD:
                        trackingDetail.setFnr(reader.getText());

                    case SISTER_ORGANISATION_FIELD:
                        KontonummerOppdatering sisterBankAccountUpdate = new KontonummerOppdatering();
                        while (reader.hasNext()) {
                            event = reader.next();


                            switch (reader.getLocalName()) {
                                case NEW_ACCOUNT_NUMBER_FIELD:
                                    sisterBankAccountUpdate.setKontonummer(reader.getText());
                                    break;
                                case ORGANISATION_NUMBER_FIELD:
                                    sisterBankAccountUpdate.setOrgNr(reader.getText());
                                    break;
                            }

                            if (event == XMLEvent.END_ELEMENT && reader.getLocalName().equals("underenhet"))
                                break;
                        }
                        updateRequest.getUnderliggendeBedriftListe().add(sisterBankAccountUpdate);

                        break;
                }
            }
        }

        return updateRequest;
    }

    public static class BankAccountUpdate {
        private String accountNumber;
        private String organisationNumber;

        public void setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
        }

        public void setOrganisationNumber(String organisationNumber) {
            this.organisationNumber = organisationNumber;
        }

        public String getAccountNumber() {
            return accountNumber;
        }

        public String getOrganisationNumber() {
            return organisationNumber;
        }
    }
}
