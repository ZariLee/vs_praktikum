package com.vs.starnet.star.controller;

import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
/**
 * newer version of the message controller
 */
@RestController
@RequestMapping("/vs/v2/messages")
public class MessageControllerV2 {
    private final MessageService messageService;

    // Connection to message service
    public MessageControllerV2(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * message object is passed to the message service method
     * @param message message Object containing all the details of the msg such as sender, star, content, etc
     * @return response entity containing message's details.
     */
    @PostMapping()
    public ResponseEntity<Map<String, String>> handleMessage(
            @RequestBody @Valid Message message) {
        return messageService.handleMessageV2(message);
    }
}
