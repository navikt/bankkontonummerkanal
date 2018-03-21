package no.nav.altinn.config;

import java.util.Properties;

public class ApplicationProperties {
    private static final String BANKACCOUNTNUMBER_CHANGED_TOPIC = "bankaccountnumber.changed.topic";

    private final String bankAccountChangedTopic;

    public ApplicationProperties(String bankAccountChangedTopic) {
        this.bankAccountChangedTopic = bankAccountChangedTopic;
    }

    public ApplicationProperties(Properties properties) {
        this(properties.getProperty(BANKACCOUNTNUMBER_CHANGED_TOPIC));
    }

    public String getBankAccountChangedTopic() {
        return bankAccountChangedTopic;
    }
}
