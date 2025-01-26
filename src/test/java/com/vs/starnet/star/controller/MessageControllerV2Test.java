package com.vs.starnet.star.controller;

import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MessageControllerV2Test {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageControllerV2 messageController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initialize mocks
    }

    /**
     * Testklasse für die Methoden des {@link MessageControllerV2}. Dieser Test prüft das Verhalten der
     * {@link MessageControllerV2#handleMessage(Message)}-Methode, die eine Nachricht verarbeitet und eine Antwort
     * vom {@link MessageService} zurückgibt.
     *
     * Der Test simuliert die Interaktion zwischen dem Controller und dem Service, um sicherzustellen, dass der Controller
     * die Nachricht korrekt verarbeitet und die erwartete Antwort zurückgibt.
     *
     * Im Test wird folgendes geprüft:
     * <ol>
     *     <li>Erstellen eines {@link Message}-Objekts mit einem gültigen Datensatz.</li>
     *     <li>Simulieren einer erfolgreichen Antwort des {@link MessageService} mit einem Status "success" und einer
     *         "messageId".</li>
     *     <li>Überprüfen, ob die zurückgegebene HTTP-Antwort den Statuscode 200 OK und den richtigen Inhalt enthält.</li>
     *     <li>Verifizieren, dass die Methode {@link MessageService#handleMessageV2(Message)} genau einmal aufgerufen wurde.</li>
     * </ol>
     * @see MessageControllerV2
     * @see MessageService
     * @see Message
     */

    @Test
    void testHandleMessage() {
        // Create a test Message object using the builder
        Message message = Message.builder()
                .star("star-uuid")
                .origin("origin-uuid")
                .sender("sender-uuid")
                .msgId("message-uuid")
                .version("1.0")
                .created(System.currentTimeMillis())
                .changed(System.currentTimeMillis())
                .subject("Test Subject")
                .message("This is a test message.")
                .status("active")
                .fromStar("from-star-uuid")
                .received(System.currentTimeMillis())
                .toStar("to-star-uuid")
                .delivered(System.currentTimeMillis())
                .build();

        // Mock the service response
        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("status", "success");
        responseMap.put("messageId", "message-uuid");

        when(messageService.handleMessageV2(message)).thenReturn(ResponseEntity.ok(responseMap));

        // Call the controller method
        ResponseEntity<Map<String, String>> response = messageController.handleMessage(message);

        // Verify the results
        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", response.getBody().get("status"));
        assertEquals("message-uuid", response.getBody().get("messageId"));

        // Verify that the service method was called exactly once
        verify(messageService, times(1)).handleMessageV2(message);
    }
}
