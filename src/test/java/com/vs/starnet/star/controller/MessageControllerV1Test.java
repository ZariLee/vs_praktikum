package com.vs.starnet.star.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class MessageControllerV1Test {

    @InjectMocks
    private MessageControllerV1 messageControllerV1;

    @Mock
    private MessageService messageService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(messageControllerV1).build();
        objectMapper = new ObjectMapper();
    }

    /**
     * Testet die erfolgreiche Erstellung einer Nachricht über die {@link MessageControllerV1 createMessage(Message)}-Methode.
     * Dieser Test prüft, ob eine gültige Nachricht korrekt verarbeitet wird und eine erfolgreiche Antwort vom Controller zurückgegeben wird.
     * Eine Mock-Antwort des {@link MessageService} wird verwendet, um den Service-Aufruf zu simulieren und sicherzustellen, dass die
     * Nachricht mit der erwarteten "msg-id" im JSON-Response zurückgegeben wird.
     * Der Test stellt sicher, dass der HTTP-Statuscode 200 OK zurückgegeben wird und die `msg-id` korrekt in der Antwort enthalten ist.
     * Es wird außerdem überprüft, dass der Controller die Nachricht ordnungsgemäß an den Service übergibt.
     */
    @Test
    public void testCreateMessage_Valid() throws Exception {
        // Erstellen einer gültigen Nachricht
        Message message = Message.builder()
                .star("star-uuid-1")
                .sender("sender-uuid")
                .msgId("msg-uuid-123")
                .subject("Test Subject")
                .message("This is a test message")
                .status("active")
                .created(System.currentTimeMillis())
                .changed(System.currentTimeMillis())
                .build();

        // Mocking des Service-Aufrufs
        when(messageService.handleMessageV1(any(Message.class))).thenReturn(ResponseEntity.ok().body(Map.of("msg-id", message.getMsgId())));

        // Testen der Create-Message-Route
        mockMvc.perform(post("/vs/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(message)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg-id").value("msg-uuid-123"));
    }


    /**
     * Testet das erfolgreiche Löschen einer Nachricht über die {@link MessageControllerV1#deleteMessage(String, String)}-Methode.
     * Dieser Test prüft, ob die Anfrage zum Löschen einer Nachricht mit einer gültigen Nachricht-ID und STAR-UUID
     * korrekt verarbeitet wird.
     * Eine Mock-Antwort des {@link MessageService} wird verwendet, um das Löschen der Nachricht zu simulieren und
     * sicherzustellen, dass die Antwort den Status "200 ok" zurückgibt.
     * Der Test stellt sicher, dass der HTTP-Statuscode 200 OK zurückgegeben wird und der Inhalt der Antwort "200 ok" entspricht.
     * Es wird außerdem überprüft, dass die Delete-Route den richtigen Service-Aufruf ausführt.
     */
    @Test
    public void testDeleteMessage_Valid() throws Exception {
        // Mocking des Service-Aufrufs für das Löschen einer Nachricht
        when(messageService.deleteMessage("msg-uuid-123", "star-uuid-1"))
                .thenReturn(new ResponseEntity<>("200 ok", HttpStatus.OK));

        // Testen der Delete-Message-Route
        mockMvc.perform(delete("/vs/v1/messages/msg-uuid-123?star=star-uuid-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("200 ok"));
    }

    /**
     * Testet das Verhalten der {@link MessageControllerV1#deleteMessage(String, String)}-Methode,
     * wenn versucht wird, eine nicht vorhandene Nachricht zu löschen.
     * Dieser Test simuliert den Fall, dass der {@link MessageService} eine Antwort mit dem Status 404 NOT FOUND zurückgibt,
     * da die angegebene Nachricht-ID nicht existiert.
     * Es wird überprüft, ob die Delete-Route den HTTP-Statuscode 404 NOT FOUND zurückgibt und der Inhalt
     * der Antwort "404 does not exist" entspricht.
     * Der Test stellt sicher, dass das System korrekt auf den Fall einer nicht existierenden Nachricht reagiert.
     * Ziel ist es, die Robustheit und Fehlermeldungen der Löschfunktion zu validieren.
     */
    @Test
    public void testDeleteMessage_NotFound() throws Exception {
        // Mocking des Service-Aufrufs, wenn die Nachricht nicht gefunden wird
        when(messageService.deleteMessage("msg-uuid-999", "star-uuid-1"))
                .thenReturn(new ResponseEntity<>("404 does not exist", HttpStatus.NOT_FOUND));

        // Testen der Delete-Message-Route, wenn die Nachricht nicht existiert
        mockMvc.perform(delete("/vs/v1/messages/msg-uuid-999?star=star-uuid-1"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("404 does not exist"));
    }

}
