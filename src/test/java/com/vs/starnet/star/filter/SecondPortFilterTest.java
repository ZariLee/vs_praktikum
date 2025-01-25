package com.vs.starnet.star.filter;

import com.vs.starnet.star.service.ApplicationState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.io.IOException;

import static org.mockito.Mockito.*;

class SecondPortFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private SecondPortFilter filter;

    @BeforeAll
    static void beforeAll() {
        // set static ports
        ApplicationState.setPort(8080); // primary port
        ApplicationState.setGalaxyPort(9090); // secondary port
    }

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testAllowedOnSecondaryPort_StarEndpoint_GET() throws IOException, ServletException {
        // simulate an incoming HTTP-Request-Object
        when(request.getLocalPort()).thenReturn(ApplicationState.getGalaxyPort()); // secondary port
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/vs/v1/star");

        filter.doFilter(request, response, chain);

        // Filter should allow request
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testForbiddenOnPrimaryPort_StarEndpoint_GET() throws IOException, ServletException {
        when(request.getLocalPort()).thenReturn(ApplicationState.getPort()); // primary port
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/vs/v1/star");

        filter.doFilter(request, response, chain);

        // Filter should block with 403
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void testMessagesOnlyOnPrimaryPort() throws IOException, ServletException {
        // Example: /vs/v2/messages is only allowed on the primary port
        when(request.getLocalPort()).thenReturn(ApplicationState.getGalaxyPort()); // secondary port
        when(request.getRequestURI()).thenReturn("/vs/v2/messages");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        // Filter should block with 403 (/vs/v2/messages is only allowed on the primary port)
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void testMessagesOnPrimaryPortAllowed() throws IOException, ServletException {
        when(request.getLocalPort()).thenReturn(ApplicationState.getPort()); // primary port
        when(request.getRequestURI()).thenReturn("/vs/v2/messages");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        // Filter should allow request
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testPatchStarOnSecondaryPort_Allowed() throws IOException, ServletException {
        when(request.getLocalPort()).thenReturn(ApplicationState.getGalaxyPort()); // secondary port
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getRequestURI()).thenReturn("/vs/v1/star/123");

        filter.doFilter(request, response, chain);

        // Filter should allow request
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void testDeleteStarOnSecondaryPort_Allowed() throws IOException, ServletException {
        when(request.getLocalPort()).thenReturn(ApplicationState.getGalaxyPort()); // primary port
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/vs/v1/star/123");

        filter.doFilter(request, response, chain);

        // Request can be forwarded
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

}
