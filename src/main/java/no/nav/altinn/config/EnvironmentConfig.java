package no.nav.altinn.config;

public class EnvironmentConfig {
    public final String aaregHentOrganisasjonEndpointURL;
    public final String aaregOppdaterKontonummerEndpointURL;
    public final String aaregWSUsername;
    public final String aaregWSPassword;

    public EnvironmentConfig() {
        aaregHentOrganisasjonEndpointURL = getVariable("AAREG_HENT_ORGANISASJON_ENDPOINTURL");
        aaregOppdaterKontonummerEndpointURL = getVariable("AAREG_OPPDATER_KONTONUMMER_ENDPOINTURL");
        aaregWSUsername = getVariable("AAREGPOLICYUSER_USERNAME");
        aaregWSPassword = getVariable("AAREGPOLICYUSER_PASSWORD");
    }

    public String getVariable(String envVariable) {
        String var = System.getenv(envVariable);
        if (var == null)
            throw new RuntimeException("Missing environment variable: \"" + envVariable + "\"");
        return var;
    }
}
