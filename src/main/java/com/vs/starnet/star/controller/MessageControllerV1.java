package com.vs.starnet.star.controller;

import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author itakurah (Niklas Hoefflin)
 * GitHub: <a href="https://github.com/itakurah">itakurah</a>
 * LinkedIn: <a href="https://www.linkedin.com/in/niklashoefflin">Niklas Hoefflin</a>
 */
@RestController
@RequestMapping("/vs/v1/messages")
public class MessageControllerV1 {

    private final MessageService messageService;

    public MessageControllerV1(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> handleMessageV1(@RequestBody @Valid Message message) {
        return messageService.handleMessageV1(message);
    }

    @DeleteMapping({"/{msgUuid}", "/"})
    public ResponseEntity<String> deleteMessage(@PathVariable String msgUuid, @RequestParam String star) {
        return messageService.deleteMessage(msgUuid, star);
    }

    @GetMapping
    public ResponseEntity<?> getMessages(
            @RequestParam String star,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String view) {
        return messageService.getMessages(star, scope, view);
    }

    @GetMapping("/{msgUuid}")
    public ResponseEntity<?> getMessage(@PathVariable String msgUuid, @RequestParam String star) {
        return messageService.getMessage(msgUuid, star);
    }
}
