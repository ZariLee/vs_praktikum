package com.vs.starnet.star.filter;

import com.vs.starnet.star.service.ApplicationState;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * custom filter to restrict or allow HTTP requests based on the port of receiving and the request paths
 */
public class SecondPortFilter implements Filter {

    static final Logger LOGGER = LogManager.getRootLogger();

    // Requests allowed only on secondary port (Galaxy Port)
    private static final List<String> SECONDARY_PORT_ALLOWED_PATHS = Arrays.asList(
            "/vs/v1/star",
            "/vs/v1/star/*"
    );

    /**
     * inspects request and either allows or blocks
     * @param request http request by client
     * @param response response that will be send
     * @param chain chain of filters in app
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        int localPort = httpRequest.getLocalPort();
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();

        LOGGER.debug("Request received on port: {}", localPort);
        LOGGER.debug("Request method: {}", method);
        LOGGER.debug("Request URI: {}", uri);

        String normalizedUri = uri.split("\\?")[0]; // Normalize URI (strip query parameters)

        // Allow `/vs/v2/messages` only on the primary port, not on the secondary
        if (normalizedUri.equals("/vs/v2/messages")) {
            if (localPort == ApplicationState.getGalaxyPort()) {
                LOGGER.debug("Request to /vs/v2/messages is not allowed on the secondary port.");
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied on this port.");
                return;
            } else {
                LOGGER.debug("Request to /vs/v2/messages allowed on primary port.");
                chain.doFilter(request, response);
                return;
            }
        }

        if (localPort == ApplicationState.getGalaxyPort()) {
            // Restrict requests not allowed on the secondary port
            boolean isAllowed = isAllowedOnSecondaryPort(method, normalizedUri);
            LOGGER.debug("Is this request allowed on secondary port? {}", isAllowed);

            if (!isAllowed) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied on this port.");
                return;
            }
        } else if (localPort == ApplicationState.getPort()) {
            // Restrict requests only allowed on the secondary port
            boolean isAllowed = !isAllowedOnSecondaryPort(method, normalizedUri);
            LOGGER.debug("Is this request allowed on primary port? {}", isAllowed);

            if (!isAllowed) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied on this port.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * determines if message is allowed ojn secondary port
     * Matches either exact paths (/vs/v1/star)
     * or pattern-based paths (/vs/v1/star/{id} for specific methods like CRUD
     * @param method method to be matched against PATCH/GET/DELETE
     * @param uri path to be matched against regex
     * @return boolean
     */
    private boolean isAllowedOnSecondaryPort(String method, String uri) {
        // Exact matches for certain endpoints
        if (SECONDARY_PORT_ALLOWED_PATHS.contains(uri)) {
            return true;
        }

        // Pattern-based matches
        if (uri.matches("/vs/v1/star/[^/]+") && (
                method.equals("PATCH") || method.equals("GET") || method.equals("DELETE")
        )) {
            return true;
        }

        if (uri.matches("/vs/v2/messages/[^/]+") && (
                method.equals("POST") || method.equals("DELETE")
        )) {
            return true;
        }

        return false;
    }
}
