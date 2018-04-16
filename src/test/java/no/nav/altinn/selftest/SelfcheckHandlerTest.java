package no.nav.altinn.selftest;

import no.nav.altinn.BankAccountNumberChannel;
import no.nav.altinn.utils.TestUtil;
import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class SelfcheckHandlerTest {
    private static URL livenessURL;
    private static URL readynessURL;

    private static Server server;

    @BeforeClass
    public static void setupClass() throws Exception {
        int port = TestUtil.randomPort();

        server = new BankAccountNumberChannel().createHTTPServer(port);
        server.start();

        livenessURL = new URL("http://localhost:" + port + "/is_alive");
        readynessURL = new URL("http://localhost:" + port + "/is_ready");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        server.stop();
        server.join();
    }

    @Test
    public void returns200OnLivenessCheck() throws Exception {
        HttpURLConnection urlConnection = (HttpURLConnection) livenessURL.openConnection();
        urlConnection.connect();

        assertEquals(200, urlConnection.getResponseCode());
    }

    @Test
    public void returns200OnReadynessCheck() throws Exception {
        HttpURLConnection urlConnection = (HttpURLConnection) readynessURL.openConnection();
        urlConnection.connect();

        assertEquals(200, urlConnection.getResponseCode());
    }
}
