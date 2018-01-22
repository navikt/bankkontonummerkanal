package no.nav.altinn;

import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.KontonummerOppdatering;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.OppdaterKontonummer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;

public class BankAccountXmlExtractor {
    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

    public KontonummerOppdatering extractFromXml(String xml) throws XMLStreamException {
        KontonummerOppdatering bankAccountUpdate = new KontonummerOppdatering();
        XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(xml));
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLEvent.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "nyttBankkontonummer":
                        bankAccountUpdate.setKontonummer(reader.getText());
                        break;
                    case "organisasjonsnummer":
                        bankAccountUpdate.setOrgNr(reader.getText());
                        break;
                }

                if (bankAccountUpdate.getKontonummer() != null && bankAccountUpdate.getOrgNr() != null)
                    return bankAccountUpdate;
            }
        }

        throw new RuntimeException("Unable to find org number or bank account number in payload");
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
