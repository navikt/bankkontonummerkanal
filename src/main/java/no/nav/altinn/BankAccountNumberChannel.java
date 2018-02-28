package no.nav.altinn;

import io.prometheus.client.exporter.MetricsServlet;
import no.nav.altinn.config.ConfigurationFields;
import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.endpoints.SelfcheckHandler;
import no.nav.altinn.route.BankAccountNumberRoute;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.io.IOException;
import java.util.Properties;

public class BankAccountNumberChannel {
    private Server server;

    public static void main(String[] args) throws IOException {
        new BankAccountNumberChannel().start(args);
    }

    private void start(String[] args) throws IOException {
        // Read configs
        Properties applicationProperties = new Properties();
        applicationProperties.load(getClass().getResourceAsStream("/application.properties"));

        Properties kafkaConsumerProperties = new Properties();
        kafkaConsumerProperties.load(getClass().getResourceAsStream("/kafka_consumer.properties"));
        Properties kafkaProducerProperties = new Properties();
        kafkaProducerProperties.load(getClass().getResourceAsStream("/kafka_producer.properties"));

        server = new Server(8080);

        HandlerCollection handlerCollection = new HandlerCollection();

        ServletContextHandler prometheusServletHandler = new ServletContextHandler();
        prometheusServletHandler.setContextPath("/metrics");
        prometheusServletHandler.addServlet(MetricsServlet.class, "/");

        handlerCollection.setHandlers(new Handler[] { prometheusServletHandler, new SelfcheckHandler() });


        server.setHandler(handlerCollection);

        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String bankAccountChangeTopic = applicationProperties.getProperty(ConfigurationFields.BANKACCOUNT_NUMBER_CHANGED_TOPIC);
        String backoutTopic = applicationProperties.getProperty(ConfigurationFields.BACKOUT_TOPIC);

        BankAccountNumberRoute route = new BankAccountNumberRoute(bankAccountChangeTopic, backoutTopic,
                kafkaConsumerProperties, kafkaProducerProperties, new EnvironmentConfig());
        route.run();
    }
}
