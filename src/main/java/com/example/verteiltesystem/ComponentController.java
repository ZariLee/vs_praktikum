package com.example.verteiltesystem;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping(path="/component") // GET request later for Postman
public class ComponentController {
    private final Component component;

    public ComponentController(Component component) {
        this.component = component;
    }

    // we espect these information  by selection GET in Postman
    @GetMapping("/uuid")
    public String getComUUID() {
        return "Component UUID: " + component.getComUUID();
    }

    @GetMapping("/ip")
    public String getIpAdresse() {
        return "IP Address: " + component.getIpAdresse().getHostAddress();
    }
    @GetMapping("/status")
    public String getStatus() {
        return component.isSol() ? "Component is SOL" : "Component is not SOL";
    }
    @GetMapping("/sendMessage/{message}")
    public String sendMessage(@PathVariable String message) throws IOException {
        component.sendMessage(message);
        return "Message Sent: " + message;
    }
    @GetMapping("/receiveMessage")
    public String receiveMessage() throws IOException {
        return "Receiving messages (Check console)...";
    }

}
