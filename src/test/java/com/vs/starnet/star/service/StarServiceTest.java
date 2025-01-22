package com.vs.starnet.star.service;

import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Component;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StarServiceTest {

    private StarService starService;

    @BeforeEach
    void setUp() throws Exception {
        // “Re-create” the StarService before each test (because of the non-static methods)
        starService = new StarService();

        // ApplicationState vorbereiten
        ApplicationState.setIsReady(true);
        ApplicationState.setCurrentRole(NodeRole.SOL);
        ApplicationState.setIp(InetAddress.getLoopbackAddress());
        ApplicationState.setPort(8080);
        ApplicationState.setComUuid("test-com-uuid");
        ApplicationState.setStarUuid("test-star-uuid");
        ApplicationState.setSolStarUuid("test-star-uuid");  // Same value as StarUuid
        ApplicationState.setSolComUuid("test-com-uuid");
        ApplicationState.setGalaxyPort(9090);
        ApplicationState.setGroupId("test-group");
        ApplicationState.setMaxComponents(3);

        // Reset static maps
        // This is how we make sure that the tests don't collide with "old" entries
        StarService.resetForTests();
    }

    @Test
    void testInitializeAsSOL() {
        // We initially change the state slightly so that we can check whether initializeAsSOL() sets it correctly.
        // CurrentRole is set to COMPONENT -> later SOL
        ApplicationState.setCurrentRole(NodeRole.COMPONENT);
        // StarUuid is set to null -> will be generated later
        ApplicationState.setStarUuid(null);
        // Placeholder value
        ApplicationState.setComUuid("temp-com-uuid");
        // IP address is set to 127.0.0.1
        ApplicationState.setIp(InetAddress.getLoopbackAddress());

        StarService.initializeAsSOL();

        assertEquals(NodeRole.SOL, ApplicationState.getCurrentRole()); // SOL?
        assertNotNull(ApplicationState.getStarUuid());
        assertNotNull(ApplicationState.getSolStarUuid());
        // Has the SOL node entered itself as an active component in the components map?
        assertTrue(StarService.getComponents().containsKey(ApplicationState.getComUuid()),
                "Nach initializeAsSOL() sollte das 'SOL'-Eigene Component in components liegen");
    }
    
    @Test
    void testRegisterComponent_Success() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // new component to be registered
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("new-com-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(newComp);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("200 ok", response.getBody());
        assertTrue(StarService.getComponents().containsKey("new-com-uuid"));
    }

    @Test
    void testRegisterComponent_ServiceUnavailable() {
        // We act as if the system is not ready
        ApplicationState.setIsReady(false);

        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("new-com-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(newComp);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("503 service unavailable", response.getBody());
        // No entry in components
        assertFalse(StarService.getComponents().containsKey("new-com-uuid"));
    }

    @Test
    void testRegisterComponent_StarUuidMismatch() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // STAR-UUID mismatch
        Component newComp = Component.builder()
                .solStarUuid("wrong-star-uuid")
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("new-com-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(newComp);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("401 unauthorized", response.getBody());
    }

    @Test
    void testRegisterComponent_ComUuidMismatch() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // COM-UUID mismatch
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid("some-other-com-uuid")
                .comUuid("new-com-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(newComp);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("401 unauthorized", response.getBody());
    }

    @Test
    void testRegisterComponent_MaxComponentsReached() {
        // Set maxComponents to 1 so we can reach "full" quickly
        ApplicationState.setMaxComponents(1);

        // Initialize SOL (so that there is 1 entry in components = the SOL itself)
        StarService.initializeAsSOL();

        // Try to register another component
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("new-com-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(newComp);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("403 no room left", response.getBody());
    }

    @Test
    void testRegisterComponent_Conflict() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // Component with comUuid == "test-com-uuid" (the SOL itself) - is already registered
        Component conflictComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid(ApplicationState.getComUuid())   // already present
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(conflictComp);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("409 conflict", response.getBody());
    }

    @Test
    void testUpdateComponent_Success() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // register a component
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-1")
                .comIp("127.0.0.2")
                .comPort(9001)
                .status("200")
                .build();
        starService.registerComponent(newComp);

        // Update: new LastInteractionTime via call -> all values remain the same
        Component updatedComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-1")
                .comIp("127.0.0.2")
                .comPort(9001)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.updateComponent("comp-1", updatedComp);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("200 ok", response.getBody());

        // // The LastInteractionTime should now be updated
        assertNotNull(StarService.getComponents().get("comp-1").getLastInteractionTime());
    }

    @Test
    void testUpdateComponent_NotFound() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // Attempt update without previous register
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("unknown-comp")
                .comIp("127.0.0.5")
                .comPort(9001)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.updateComponent("unknown-comp", comp);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("404 does not exist", response.getBody());
    }

    @Test
    void testUpdateComponent_ServiceUnavailable() {
        // We act as if the system is not ready
        ApplicationState.setIsReady(false);

        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-1")
                .comIp("127.0.0.1")
                .comPort(9001)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.updateComponent("comp-1", comp);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("503 service unavailable", response.getBody());
    }

    @Test
    void testUpdateComponent_StarUuidMismatch() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // register a component
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-123")
                .comIp("127.0.0.2")
                .comPort(9001)
                .status("200")
                .build();
        starService.registerComponent(comp);

        // update with wrong star
        Component wrongStarComp = Component.builder()
                .solStarUuid("wrong-star")
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-123")
                .comIp("127.0.0.2")
                .comPort(9001)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.updateComponent("comp-123", wrongStarComp);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("401 unauthorized", response.getBody());
    }

    @Test
    void testDeregisterComponent_Success() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // register a component
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-2")
                .comIp("127.0.0.1")
                .comPort(9002)
                .status("200")
                .lastInteractionTime(new AtomicReference<>(Instant.now()))
                .build();
        starService.registerComponent(comp);

        // mock HttpServletRequest
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<String> response = starService.deregisterComponent("comp-2", ApplicationState.getSolStarUuid(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("200 ok", response.getBody());

        // check inactive components
        assertFalse(StarService.getComponents().containsKey("comp-2"));
        assertTrue(StarService.getInactiveComponents().containsKey("comp-2"));
    }

    @Test
    void testDeregisterComponent_UnauthorizedIp() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // register a component
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-3")
                .comIp("127.0.0.1")
                .status("200")
                .build();
        starService.registerComponent(comp);

        // mock HttpServletRequest -> another IP
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.123.1");

        ResponseEntity<String> response = starService.deregisterComponent("comp-3", ApplicationState.getSolStarUuid(), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("401 unauthorized", response.getBody());

        // still active?
        assertTrue(StarService.getComponents().containsKey("comp-3"));
    }

    @Test
    void testGetComponentStatus_Success() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // register component
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-status")
                .comIp("127.0.0.2")
                .comPort(9003)
                .status("200")
                .build();
        starService.registerComponent(comp);

        ResponseEntity<Map<String, String>> response = starService.getComponentStatus("comp-status", ApplicationState.getStarUuid());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("200", response.getBody().get("status"));
        assertEquals("comp-status", response.getBody().get("component"));
    }

    @Test
    void testGetComponentStatus_StarUuidMismatch() {
        // enter a SOL component
        StarService.initializeAsSOL();

        // register component
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-status2")
                .comIp("127.0.0.2")
                .comPort(9004)
                .status("200")
                .build();
        starService.registerComponent(comp);

        // Incorrect star parameter
        ResponseEntity<Map<String, String>> response = starService.getComponentStatus("comp-status2", "wrong-star");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testGetComponentStatus_NotFound() {
        // enter a SOL component, but do not register any further components
        StarService.initializeAsSOL();

        //Query that doesn't exist
        ResponseEntity<Map<String, String>> response = starService.getComponentStatus("unknown-comp", ApplicationState.getStarUuid());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }
}
