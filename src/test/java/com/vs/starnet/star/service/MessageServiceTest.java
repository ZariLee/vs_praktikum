package com.vs.starnet.star.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.network.HttpHandler;
import com.vs.starnet.star.repository.SolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.InetAddress;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Beispiel-Tests für MessageService mit Lombok-Builder und Mockito
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    // todo in dieser Testklasse hab ich mich bisher nur etwas ausgetobt, daher nichts fix

    @Mock
    private SolRepository solRepository;

    @InjectMocks
    private MessageService messageService;

    @BeforeEach
    void setUp() throws Exception {
        // Beispielhafte Initialisierung des ApplicationState
        ApplicationState.setIsReady(true);
        ApplicationState.setCurrentRole(NodeRole.SOL);           // Standard: SOL
        ApplicationState.setSolStarUuid("star-1111");            // "eigene" Star-UUID
        ApplicationState.setComUuid("com-1234");
        // IP/Port braucht man, falls Code z.B. forwardet
        ApplicationState.setIp(InetAddress.getLocalHost());
        ApplicationState.setPort(8080);
    }

    /**
     * Testfall: Happy Path, wenn die Rolle = SOL ist
     * und die übergebene Message eine zur Star-UUID passende star hat.
     */
    @Test
    void testHandleMessageV1AsSolOk() {
        // Arrange
        // Star, das zur "star-1111" im ApplicationState passt
        // => dadurch keine 401
        // Außerdem "origin" >= 1000 => isComUuid OK
        // "subject" nicht leer => keine PRECONDITION_FAILED
        Message message = Message.builder()
                .star("star-1111")  // passt zu ApplicationState
                .origin("1000")
                .sender("ignored")  // wird eh überschrieben durch com-1234
                .subject("Test subject")
                .message("Hello world")
                .msgId("1@1000:star-1111")
                .build();

        // Act
        ResponseEntity<Map<String, String>> response = messageService.handleMessageV1(message);

        // Assert
        // Bei SOL sollte es durch processMessageAsSol -> (neue) msg-id -> 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("msg-id"),
                "Response body should contain 'msg-id' key");
    }

    /**
     * Testfall: Rolle = SOL, aber message.star stimmt nicht
     * => erwarte 401 UNAUTHORIZED.
     */
    @Test
    void testHandleMessageV1InvalidStarUuid() {
        // Arrange
        Message message = Message.builder()
                .star("star-9999")   // != star-1111 => mismatch
                .origin("1000")
                .sender("dummy")
                .subject("Subject")
                .message("Body")
                .build();

        // Act
        ResponseEntity<Map<String, String>> response =
                messageService.handleMessageV1(message);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Expected 401 because star mismatch");
    }

    /**
     * Testfall: Rolle = COMPONENT => Nachricht sollte an SOL "weitergeleitet" werden.
     * -> Wir mocken HttpHandler.sendPostRequest(...)
     */
    @Test
    void testHandleMessageV1ForwardToSol() throws Exception {
        // 1) Rolle = COMPONENT
        ApplicationState.setCurrentRole(NodeRole.COMPONENT);

        // 2) Demo: setze SOL-IP & Port, die du ansprechen willst
        ApplicationState.setSolIp(InetAddress.getByName("127.0.0.1"));
        ApplicationState.setSolPort(9999);

        Message message = Message.builder()
                .star("star-1111")
                .origin("1000")
                .sender("placeholder")
                .subject("Forward test")
                .message("Hello, from a component")
                .msgId("1@1000:star-1111")
                .build();

        // Mock HttpHandler
        try (MockedStatic<HttpHandler> httpHandlerMock = Mockito.mockStatic(HttpHandler.class)) {
            // Dein Stubbing...
            HttpResponse<String> mockResponse = mock(HttpResponse.class);
            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn("{\"msg-id\": \"123@1000:star-1111\"}");

            httpHandlerMock.when(() ->
                    HttpHandler.sendPostRequest(anyString(), anyString(), anyString())
            ).thenReturn(mockResponse);

            // Act
            ResponseEntity<Map<String, String>> response = messageService.handleMessageV1(message);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            // ...
        }
    }

}
