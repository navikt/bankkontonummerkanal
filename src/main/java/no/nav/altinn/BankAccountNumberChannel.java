package no.nav.altinn;

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.DefaultExports;
import no.nav.altinn.config.EnvironmentConfig;
import no.nav.altinn.endpoints.SelfcheckHandler;
import no.nav.altinn.route.BankAccountNumberRoute;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.virksomhet.tjenester.arbeidsgiver.v2.Arbeidsgiver;
import no.nav.virksomhet.tjenester.behandlearbeidsgiver.v1.BehandleArbeidsgiver;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.CallbackHandler;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

public class BankAccountNumberChannel {
    private final static Logger log = LoggerFactory.getLogger(BankAccountNumberRoute.class);
    private Server server;
    private BankAccountNumberRoute route;


    public static void main(String[] args) throws IOException {
        DefaultExports.initialize();
        // Read config
        Properties kafkaProperties = new Properties();

        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        kafkaProperties.load(BankAccountNumberChannel.class.getResourceAsStream("/kafka_consumer.properties"));
        kafkaProperties.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=\"" + environmentConfig.srvbankkontonummerUsername + "\" " +
                "password=\"" + environmentConfig.srvbankkontonummerPassword + "\";");

        try (KafkaConsumer<String, ExternalAttachment> kafkaConsumer = new KafkaConsumer<>(kafkaProperties)) {
            new BankAccountNumberChannel().bootstrap(kafkaConsumer, environmentConfig);
        }
    }


    public void bootstrap(KafkaConsumer<String, ExternalAttachment> consumer, EnvironmentConfig environmentConfig) {
        server = createHTTPServer(environmentConfig.serverPort);
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Configure password callback handler
        CallbackHandler pwCallback = callbacks -> ((WSPasswordCallback)callbacks[0]).setPassword(environmentConfig.aaregWSPassword);

        // Configure WS security with username password
        HashMap<String, Object> interceptorProperties = new HashMap<>();
        interceptorProperties.put(WSHandlerConstants.USER, environmentConfig.aaregWSUsername);
        interceptorProperties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        interceptorProperties.put(WSHandlerConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
        interceptorProperties.put(WSHandlerConstants.PW_CALLBACK_REF, pwCallback);
        WSS4JOutInterceptor passwordOutInterceptor = new WSS4JOutInterceptor(interceptorProperties);

        // Configure the endpoint used for hentArbeidsgiver
        JaxWsProxyFactoryBean arbeidsGiverFactory = new JaxWsProxyFactoryBean();
        arbeidsGiverFactory.setAddress(environmentConfig.aaregHentOrganisasjonEndpointURL);
        arbeidsGiverFactory.getFeatures().add(new LoggingFeature());
        arbeidsGiverFactory.setOutInterceptors(Collections.singletonList(passwordOutInterceptor));
        arbeidsGiverFactory.setServiceClass(Arbeidsgiver.class);
        Arbeidsgiver employer = (Arbeidsgiver) arbeidsGiverFactory.create();
        configureTimeout(employer, environmentConfig.retryMaxWSConnectionTimeout, environmentConfig.retryMaxWSReceiveTimeout);

        // Configure then endpoint used for oppdaterKontonummer
        JaxWsProxyFactoryBean handleEmployerFactory = new JaxWsProxyFactoryBean();
        handleEmployerFactory.setAddress(environmentConfig.aaregOppdaterKontonummerEndpointURL);
        handleEmployerFactory.getFeatures().add(new LoggingFeature());
        handleEmployerFactory.setOutInterceptors(Collections.singletonList(passwordOutInterceptor));
        handleEmployerFactory.setServiceClass(BehandleArbeidsgiver.class);
        BehandleArbeidsgiver handleEmployer = (BehandleArbeidsgiver) handleEmployerFactory.create();
        configureTimeout(handleEmployer, environmentConfig.retryMaxWSConnectionTimeout, environmentConfig.retryMaxWSReceiveTimeout);

        consumer.subscribe(Collections.singletonList(environmentConfig.bankaccountNumberChangedTopic));
        route = new BankAccountNumberRoute(employer, handleEmployer, consumer, environmentConfig.retryInterval,
                environmentConfig.retryMaxRetries);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        route.run();
    }

    private void configureTimeout(Object service, long connectionTimeout, long receiveTimeout) {
        Client client = ClientProxy.getClient(service);
        HTTPConduit conduit = (HTTPConduit) client.getConduit();
        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        httpClientPolicy.setConnectionTimeout(connectionTimeout);
        httpClientPolicy.setReceiveTimeout(receiveTimeout);
        conduit.setClient(httpClientPolicy);
    }

    public Server createHTTPServer(int port) {
        Server server = new Server(port);

        HandlerCollection handlerCollection = new HandlerCollection();

        ServletContextHandler prometheusServletHandler = new ServletContextHandler();
        prometheusServletHandler.setContextPath("/prometheus");
        prometheusServletHandler.addServlet(MetricsServlet.class, "/");

        handlerCollection.setHandlers(new Handler[] { prometheusServletHandler, new SelfcheckHandler() });


        server.setHandler(handlerCollection);

        return server;
    }

    public void shutdown() {
        log.info("Doing a clean shutdown");
        route.stop();
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
