package no.nav.altinn;

import io.prometheus.client.exporter.MetricsServlet;
import no.nav.altinn.config.ApplicationProperties;
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

    public static void main(String[] args) throws IOException {
        new BankAccountNumberChannel().start(args);
    }

    private void start(String[] args) throws IOException {
        // Read configs
        Properties propertyFile = new Properties();
        propertyFile.load(getClass().getResourceAsStream("/application.properties"));
        ApplicationProperties applicationProperties = new ApplicationProperties(propertyFile);


        Properties kafkaProperties = new Properties();
        kafkaProperties.load(getClass().getResourceAsStream("/kafka_consumer.properties"));

        Server server = createHTTPServer(8080);

        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        BankAccountNumberRoute route = new BankAccountNumberRoute(applicationProperties, kafkaProperties, new EnvironmentConfig());
        route.run();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            route.stop();
            try {
                server.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    public Server createHTTPServer(int port) {
        Server server = new Server(port);

        HandlerCollection handlerCollection = new HandlerCollection();

        ServletContextHandler prometheusServletHandler = new ServletContextHandler();
        prometheusServletHandler.setContextPath("/metrics");
        prometheusServletHandler.addServlet(MetricsServlet.class, "/");

        handlerCollection.setHandlers(new Handler[] { prometheusServletHandler, new SelfcheckHandler() });


        server.setHandler(handlerCollection);

        return server;
    }
}
