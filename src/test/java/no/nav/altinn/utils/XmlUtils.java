package no.nav.altinn.utils;

import no.nav.altinn.xmlextractor.BankAccountXmlExtractor;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.meldinger.v1.OppdaterKontonummerRequest;
import org.apache.cxf.helpers.IOUtils;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStreamReader;

public class XmlUtils {
    public static final BankAccountXmlExtractor XML_EXTRACTOR = new BankAccountXmlExtractor();

    public static OppdaterKontonummerRequest unmarshallXML(String resource) throws XMLStreamException {
        return XML_EXTRACTOR.buildSoapRequestFromAltinnPayload(new InputStreamReader(XmlUtils.class.getResourceAsStream(resource)));
    }

    public static String readXml(String resource) throws IOException {
        return IOUtils.readStringFromStream(XmlUtils.class.getResourceAsStream(resource));
    }
}
