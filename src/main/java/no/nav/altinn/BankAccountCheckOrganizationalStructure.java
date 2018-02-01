package no.nav.altinn;

import no.nav.virksomhet.part.arbeidsgiver.v1.Organisasjon;
import no.nav.virksomhet.tjenester.arbeidsgiver.meldinger.v2.HentOrganisasjonResponse;

public class BankAccountCheckOrganizationalStructure {

    private boolean valdigOrganizationalStructure(HentOrganisasjonResponse hentOrganisasjonResponse ) {

        boolean gyldig = false;

        Organisasjon org = hentOrganisasjonResponse.getOrganisasjon();
        int antUnderEnhAAreg = org.getAntallUnderliggendeEnheter();





        int antUnderenh = ul != null? ul.size(): 0;

        //Ingen kontroll av bedrifter uten underbedrifter.
        if (antUnderenh == 0) return true;

        for (int i = 0; i < antUnderenh; i++) {
            Element el = (Element)ul.get(i);
            String orgnrUnderenh = null;
            for (Object object : el.getChildren()) {
                if (((Element) object).getName().equalsIgnoreCase("organisasjonsnummer")) {
                    orgnrUnderenh = ((Element) object).getText();
                }
            }

            for (int y = 0; y < antUnderEnhAAreg; y++) {
                if ( orgnrUnderenh != null &&
                        aaRegOrgStruktur.getBarneorganisasjonListeArray(y).getOrgNr().equalsIgnoreCase(orgnrUnderenh) &&
                        aaRegOrgStruktur.getBarneorganisasjonListeArray(y).getStatus().getKode().equalsIgnoreCase("1")) {
                    gyldig = true;
                    break;
                } else {
                    gyldig = false;
                }
            }
        }

        return gyldig;

    }
}
