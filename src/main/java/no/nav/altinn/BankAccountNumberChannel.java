package no.nav.altinn;

import io.prometheus.client.exporter.MetricsServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class BankAccountNumberChannel {
    private Server server;

    public static void main(String[] args) {
        new BankAccountNumberChannel().start(args);
    }

    private void start(String[] args) {
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
    }
}
