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
@RequestMapping("/vs/v2/messages")
public class MessageControllerV2 {
    private final MessageService messageService;

    public MessageControllerV2(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping()
    public ResponseEntity<Map<String, String>> handleMessage(
            @RequestBody @Valid Message message) {
        return messageService.handleMessageV2(message);
    }
}
