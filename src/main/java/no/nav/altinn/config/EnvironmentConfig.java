package no.nav.altinn.config;

public class EnvironmentConfig {
    public static final String AAREG_HENT_ORGANISASJON_URL = "AAREG_HENT_ORGANISASJON_ENDPOINTURL";
    public static final String AAREG_OPPDATER_KONTONUMMER_URL = "AAREG_OPPDATER_KONTONUMMER_ENDPOINTURL";
    public static final String AAREGUSER_USERNAME = "AAREGPOLICYUSER_USERNAME";
    public static final String AAREGUSER_PASSWORD = "AAREGPOLICYUSER_PASSWORD";

    public final String aaregHentOrganisasjonEndpointURL;
    public final String aaregOppdaterKontonummerEndpointURL;
    public final String aaregWSUsername;
    public final String aaregWSPassword;

    public EnvironmentConfig() {
        aaregHentOrganisasjonEndpointURL = getVariable(AAREG_HENT_ORGANISASJON_URL);
        aaregOppdaterKontonummerEndpointURL = getVariable(AAREG_OPPDATER_KONTONUMMER_URL);
        aaregWSUsername = getVariable(AAREGUSER_USERNAME);
        aaregWSPassword = getVariable(AAREGUSER_PASSWORD);
    }

    public EnvironmentConfig(String hentOrgNrUrl, String oppdaterKontnrUrl, String username, String password) {
        aaregHentOrganisasjonEndpointURL = hentOrgNrUrl;
        aaregOppdaterKontonummerEndpointURL = oppdaterKontnrUrl;
        this.aaregWSUsername = username;
        this.aaregWSPassword = password;
    }

    public String getVariable(String envVariable) {
        String var = System.getenv(envVariable);
        if (var == null)
            throw new RuntimeException("Missing environment variable: \"" + envVariable + "\"");
        return var;
    }
}
