package no.nav.altinn.endpoints;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class SelfcheckHandler extends AbstractHandler {

    private final static Logger log = LoggerFactory.getLogger(SelfcheckHandler.class);

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/is_alive".equals(target)) {
            handleIsAlive(response);
            baseRequest.setHandled(true);
            log.info("Eg er i live, fra demo flokk");
        }

        if ("/is_ready".equals(target)) {
            handleIsReady(response);
            baseRequest.setHandled(true);
        }
    }

    private void handleIsAlive(HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);

        PrintWriter out = response.getWriter();
        out.println("I'm alive!");
    }

    private void handleIsReady(HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);

        PrintWriter out = response.getWriter();
        out.println("I'm ready!");
    }
}
