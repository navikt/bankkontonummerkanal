package no.nav.altinn.config;

public class EnvironmentConfig {
    public static final String AAREG_HENT_ORGANISASJON_URL = "AAREG_HENT_ORGANISASJON_ENDPOINTURL";
    public static final String AAREG_OPPDATER_KONTONUMMER_URL = "AAREG_OPPDATER_KONTONUMMER_ENDPOINTURL";
    public static final String AAREGUSER_USERNAME = "AAREGPOLICYUSER_USERNAME";
    public static final String AAREGUSER_PASSWORD = "AAREGPOLICYUSER_PASSWORD";
    public static final String SERVER_PORT = "SERVER_PORT";
    private static final String BANKACCOUNTNUMBER_CHANGED_TOPIC = "BANKACCOUNTNUMBER_CHANGED_TOPIC";

    public final String aaregHentOrganisasjonEndpointURL;
    public final String aaregOppdaterKontonummerEndpointURL;
    public final String aaregWSUsername;
    public final String aaregWSPassword;
    public final String serverPort;
    public final String bankaccountNumberChangedTopic;

    public EnvironmentConfig() {
        this.aaregHentOrganisasjonEndpointURL = getVariable(AAREG_HENT_ORGANISASJON_URL);
        this.aaregOppdaterKontonummerEndpointURL = getVariable(AAREG_OPPDATER_KONTONUMMER_URL);
        this.aaregWSUsername = getVariable(AAREGUSER_USERNAME);
        this.aaregWSPassword = getVariable(AAREGUSER_PASSWORD);
        this.serverPort = getVariable(SERVER_PORT);
        this.bankaccountNumberChangedTopic = getVariable(BANKACCOUNTNUMBER_CHANGED_TOPIC);
    }

    public EnvironmentConfig(String hentOrgNrUrl, String oppdaterKontnrUrl, String username, String password, String serverPort, String bankaccountNumberChangedTopic) {
        this.aaregHentOrganisasjonEndpointURL = hentOrgNrUrl;
        this.aaregOppdaterKontonummerEndpointURL = oppdaterKontnrUrl;
        this.aaregWSUsername = username;
        this.aaregWSPassword = password;
        this.serverPort = serverPort;
        this.bankaccountNumberChangedTopic = bankaccountNumberChangedTopic;
    }

    public String getVariable(String envVariable, String defaultVariable) {
        String var = System.getenv(envVariable);
        return var == null ? defaultVariable : var;
    }

    public String getVariable(String envVariable) {
        String var = System.getenv(envVariable);
        if (var == null)
            throw new RuntimeException("Missing environment variable: \"" + envVariable + "\"");
        return var;
    }
}
