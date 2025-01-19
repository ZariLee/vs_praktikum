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

/**
 * Testklasse für den StarService
 */
class StarServiceTest {

    // todo Testklasse noch nicht fertig

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
        // Wir verändern initial den State etwas, damit wir prüfen können, ob initializeAsSOL() ihn richtig setzt.
        ApplicationState.setCurrentRole(NodeRole.COMPONENT);
        ApplicationState.setStarUuid(null);
        ApplicationState.setComUuid("temp-com-uuid");
        ApplicationState.setIp(InetAddress.getLoopbackAddress());

        // Aufruf
        StarService.initializeAsSOL();

        // Assertions
        assertEquals(NodeRole.SOL, ApplicationState.getCurrentRole());
        assertNotNull(ApplicationState.getStarUuid());
        assertNotNull(ApplicationState.getSolStarUuid());
//        assertTrue(StarService.components.containsKey(ApplicationState.getComUuid()),
//                "Nach initializeAsSOL() sollte das 'SOL'-Eigene Component in components liegen");
    }

    @Test
    void testRegisterComponent_Success() {
        // Bereits vorhandenes SOL-Component eintragen (manuell oder via initializeAsSOL)
        StarService.initializeAsSOL(); // So ist ein "eigenes" SOL-Component bereits drin

        // Neues Component, das registriert werden soll:
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())    // Muss matchen
                .solComUuid(ApplicationState.getComUuid())      // Muss matchen
                .comUuid("new-com-uuid")                        // Eindeutig neu
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        ResponseEntity<String> response = starService.registerComponent(newComp);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("200 ok", response.getBody());
//        assertTrue(StarService.components.containsKey("new-com-uuid"));
    }

    @Test
    void testRegisterComponent_ServiceUnavailable() {
        // Wir tun so, als sei das System nicht bereit
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
        // Kein Eintrag in components
//        assertFalse(StarService.components.containsKey("new-com-uuid"));
    }

    @Test
    void testRegisterComponent_StarUuidMismatch() {
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
        // Setze maxComponents auf 1, damit wir schnell "full" erreichen
        ApplicationState.setMaxComponents(1);

        // Initialisiere SOL (damit 1 Eintrag in components liegt = das SOL selbst)
        StarService.initializeAsSOL();

        // Versuche, noch ein weiteres Component zu registrieren
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("new-com-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("200")
                .build();

        // Nun sollte es abgewiesen werden
        ResponseEntity<String> response = starService.registerComponent(newComp);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("403 no room left", response.getBody());
    }

    @Test
    void testRegisterComponent_Conflict() {
        // StarService als SOL initialisieren
        StarService.initializeAsSOL();

        // Component mit comUuid == "test-com-uuid" (also das SOL selbst) - ist schon registriert
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
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Registriere eine Komponente
        Component newComp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-1")
                .comIp("127.0.0.2")
                .comPort(9001)
                .status("200")
                .build();
        starService.registerComponent(newComp);

        // Update: wir ändern aber nur LastInteractionTime via Aufruf, die IP & Port sollen matchen
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

        // Die LastInteractionTime sollte jetzt aktualisiert sein
//        assertNotNull(StarService.components.get("comp-1").getLastInteractionTime());
    }

    @Test
    void testUpdateComponent_NotFound() {
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Versuch Update ohne vorheriges Register
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
        // Initialisiere
        StarService.initializeAsSOL();

        // Registriere eine Komponente
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-123")
                .comIp("127.0.0.2")
                .comPort(9001)
                .status("200")
                .build();
        starService.registerComponent(comp);

        // Update mit falschem star
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
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Registriere eine Komponente
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-2")
                .comIp("127.0.0.1") // muss gleich dem Requester sein
                .comPort(9002)
                .status("200")
                .lastInteractionTime(new AtomicReference<>(Instant.now()))
                .build();
        starService.registerComponent(comp);

        // HttpServletRequest mocken
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Aufruf
        ResponseEntity<String> response = starService.deregisterComponent("comp-2", ApplicationState.getSolStarUuid(), request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("200 ok", response.getBody());

        // Inaktive Komponenten checken
//        assertFalse(StarService.components.containsKey("comp-2"));
//        assertTrue(StarService.inactiveComponents.containsKey("comp-2"));
    }

    @Test
    void testDeregisterComponent_UnauthorizedIp() {
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Registriere Komponente
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-3")
                .comIp("127.0.0.1")
                .status("200")
                .build();
        starService.registerComponent(comp);

        // HttpServletRequest mocken -> anderes IP
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.123.1");

        ResponseEntity<String> response = starService.deregisterComponent("comp-3", ApplicationState.getSolStarUuid(), request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("401 unauthorized", response.getBody());

        // Noch aktiv?
//        assertTrue(StarService.components.containsKey("comp-3"));
    }

    @Test
    void testGetComponentStatus_Success() {
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Registriere Komponente
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
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Registriere Komponente
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-status2")
                .comIp("127.0.0.2")
                .comPort(9004)
                .status("200")
                .build();
        starService.registerComponent(comp);

        // Falscher star-Parameter
        ResponseEntity<Map<String, String>> response = starService.getComponentStatus("comp-status2", "wrong-star");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testGetComponentStatus_NotFound() {
        // StarService initialisieren, aber keine weitere Komponente registrieren
        StarService.initializeAsSOL();

        // Abfrage, die nicht existiert
        ResponseEntity<Map<String, String>> response = starService.getComponentStatus("unknown-comp", ApplicationState.getStarUuid());
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @Disabled("Da deregisterComponents() ein System.exit(1) aufruft, nur als Beispiel disabled.")
    void testDeregisterComponents_ExitsProcess() {
        // StarService initialisieren
        StarService.initializeAsSOL();

        // Registriere eine Komponente
        Component comp = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid("comp-to-exit")
                .comIp("127.0.0.2")
                .comPort(9005)
                .status("200")
                .build();
        starService.registerComponent(comp);

        // Würde System.exit(1) auslösen
        // starService.deregisterComponents();
    }
}
