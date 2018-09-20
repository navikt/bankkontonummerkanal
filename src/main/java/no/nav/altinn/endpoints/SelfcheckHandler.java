package no.nav.altinn.endpoints;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class SelfcheckHandler extends AbstractHandler {


    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/is_alive".equals(target)) {
            handleIsAlive(response);
            baseRequest.setHandled(true);
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
