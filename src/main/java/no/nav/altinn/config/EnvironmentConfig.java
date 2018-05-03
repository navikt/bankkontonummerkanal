package no.nav.altinn.config;

import static no.nav.altinn.config.EnvironmentConfig.Key.*;

public class EnvironmentConfig {
    public final String aaregHentOrganisasjonEndpointURL;
    public final String aaregOppdaterKontonummerEndpointURL;
    public final String aaregWSUsername;
    public final String aaregWSPassword;
    public final int serverPort;
    public final String bankaccountNumberChangedTopic;
    public final int retryInterval;
    public final int retryMaxRetries;
    public final long retryMaxWSConnectionTimeout;
    public final long retryMaxWSReceiveTimeout;
    public final String srvbankkontonummerUsername;
    public final String srvbankkontonummerPassword;

    public EnvironmentConfig() {
        this.aaregHentOrganisasjonEndpointURL = getVariable(AAREG_HENT_ORGANISASJON_ENDPOINTURL);
        this.aaregOppdaterKontonummerEndpointURL = getVariable(AAREG_OPPDATER_KONTONUMMER_ENDPOINTURL);
        this.aaregWSUsername = getVariable(AAREGPOLICYUSER_USERNAME);
        this.aaregWSPassword = getVariable(AAREGPOLICYUSER_PASSWORD);
        this.serverPort = Integer.parseInt(getVariable(SERVER_PORT));
        this.bankaccountNumberChangedTopic = getVariable(BANKACCOUNTNUMBER_CHANGED_TOPIC);
        this.retryInterval = Integer.parseInt(getVariable(RETRY_INTERVAL));
        this.retryMaxRetries = Integer.parseInt(getVariable(RETRY_MAX_RETRIES));
        this.retryMaxWSConnectionTimeout = Long.parseLong(getVariable(RETRY_MAX_WS_CONNECTION_TIMEOUT));
        this.retryMaxWSReceiveTimeout = Integer.parseInt(getVariable(RETRY_MAX_WS_RECEIVE_TIMEOUT));
        this.srvbankkontonummerUsername = getVariable(SRVBANKKONTONUMMERKANAL_USERNAME);
        this.srvbankkontonummerPassword = getVariable(SRVBANKKONTONUMMERKANAL_PASSWORD);
    }

    public EnvironmentConfig(String hentOrgNrUrl, String oppdaterKontnrUrl, String username, String password,
                             int serverPort, String bankaccountNumberChangedTopic, int retryInterval,
                             int retryMaxRetries, int retryMaxWSConnectionTimeout, int retryMaxWSReceiveTimeout,
                             String srvbankkontonummerUsername, String srvbankkontonummerPassword) {
        this.aaregHentOrganisasjonEndpointURL = hentOrgNrUrl;
        this.aaregOppdaterKontonummerEndpointURL = oppdaterKontnrUrl;
        this.aaregWSUsername = username;
        this.aaregWSPassword = password;
        this.serverPort = serverPort;
        this.bankaccountNumberChangedTopic = bankaccountNumberChangedTopic;
        this.retryInterval = retryInterval;
        this.retryMaxRetries = retryMaxRetries;
        this.retryMaxWSConnectionTimeout = retryMaxWSConnectionTimeout;
        this.retryMaxWSReceiveTimeout = retryMaxWSReceiveTimeout;
        this.srvbankkontonummerUsername = srvbankkontonummerUsername;
        this.srvbankkontonummerPassword = srvbankkontonummerPassword;
    }

    public String getVariable(Key key) {
        String var = System.getenv(key.name());
        if (var != null)
            return var;
        if (key.defaultValue != null) {
            return key.defaultValue;
        }
        throw new RuntimeException("Missing environment variable: \"" + key + "\"");
    }

    public enum Key {
        SRVBANKKONTONUMMERKANAL_USERNAME,
        SRVBANKKONTONUMMERKANAL_PASSWORD,
        AAREG_HENT_ORGANISASJON_ENDPOINTURL,
        AAREG_OPPDATER_KONTONUMMER_ENDPOINTURL,
        AAREGPOLICYUSER_USERNAME,
        AAREGPOLICYUSER_PASSWORD,
        SERVER_PORT("8080"),
        BANKACCOUNTNUMBER_CHANGED_TOPIC,
        RETRY_INTERVAL("5000"),
        RETRY_MAX_RETRIES("5"),
        RETRY_MAX_WS_CONNECTION_TIMEOUT("30000"),
        RETRY_MAX_WS_RECEIVE_TIMEOUT("60000");
        public final String defaultValue;

        Key() {
            this(null);
        }

        Key(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}
