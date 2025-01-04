package com.vs.starnet.star.controller;

import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Rest-Controller to manage messages
 */
@RestController
@RequestMapping("/vs/v1/messages")
public class MessageControllerV1 {

    private final MessageService messageService;

    // Connection to message service
    public MessageControllerV1(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * handles a new message
     * @param message message-object in a json format
     * @return entity containing status and message id
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> handleMessageV1(@RequestBody @Valid Message message) {
        return messageService.handleMessageV1(message);
    }

    /**
     * deletes message via id
     * @param msgUuid id to specify message to be deleted
     * @param star sta UUID for validation
     * @return UUID of the message, star UUID
     */
    @DeleteMapping({"/{msgUuid}", "/"})
    public ResponseEntity<String> deleteMessage(@PathVariable String msgUuid, @RequestParam String star) {
        return messageService.deleteMessage(msgUuid, star);
    }

    /**
     * get messages
     * @param star star uuid for validation
     * @param scope filter such as "all", "active", etc
     * @param view specific view
     * @return response entity with json
     */
    @GetMapping
    public ResponseEntity<?> getMessages(
            @RequestParam String star,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String view) {
        return messageService.getMessages(star, scope, view);
    }

    /**
     * gets details of a specific message
     * @param msgUuid UUID to specify msg
     * @param star star UUID for validation
     * @return msg details
     */
    @GetMapping("/{msgUuid}")
    public ResponseEntity<?> getMessage(@PathVariable String msgUuid, @RequestParam String star) {
        return messageService.getMessage(msgUuid, star);
    }
}
