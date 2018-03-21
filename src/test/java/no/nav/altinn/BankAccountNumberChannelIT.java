package no.nav.altinn;

import no.nav.altinn.config.ApplicationProperties;
import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.route.BankAccountNumberRoute;
import org.junit.BeforeClass;

import java.util.Properties;

public class BankAccountNumberChannelIT {
    private static final String MOCK_USERNAME = "mockUsername";
    private static final String MOCK_PASSWORD = "mockPassword";



    private static BankAccountNumberRoute bankAccountNumberRoute;

    @BeforeClass
    public static void setup() {
        // Set up mocks
        String hentOrgNrURL = "";
        String oppdatertKontoNrUrl = "";

        Properties application = new Properties();
        ApplicationProperties applicationProperties = new ApplicationProperties(application);
        Properties kafkaProperties = new Properties();
        EnvironmentConfig environmentConfig = new EnvironmentConfig(hentOrgNrURL, oppdatertKontoNrUrl, MOCK_USERNAME, MOCK_PASSWORD);

        // TODO: Remove all messages that starts with xmlMessage from the reflog
        //bankAccountNumberRoute = new BankAccountNumberRoute(applicationProperties, kafkaProperties, new EnvironmentConfig());
    }
}
