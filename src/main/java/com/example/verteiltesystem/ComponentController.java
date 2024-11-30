package com.example.verteiltesystem;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/component")
public class ComponentController {
    private final Component component;
    private final RestTemplate restTemplate;

    // Map to store active components: UUID -> (IP, Port)
    private final Map<String, String> activeComponents = new ConcurrentHashMap<>();

    public ComponentController(Component component) {
        this.component = component;
        this.restTemplate = new RestTemplate();
    }

    // GETTERS
    @GetMapping("/uuid")
    public String getComUUID() {
        return "Component UUID: " + component.getComUUID();
    }

    @GetMapping("/ip")
    public String getIpAddress() {
        return "IP Address: " + component.getIpAdresse().getHostAddress();
    }

    @GetMapping("/status")
    public String getStatus() {
        return component.isSol() ? "Component is SOL" : "Component is not SOL";
    }

    // MESSAGE HANDLING

    // Regular components send messages to SOL
    @PostMapping("/sendMessage")
    public String sendMessage(@RequestParam String solIp, @RequestParam int solPort,
                              @RequestParam String targetComUUID, @RequestBody String message) throws IOException {
        // Build the target URL for the SOL component
        String solUrl = "http://" + solIp + ":" + solPort + "/component/forwardMessage";

        // Prepare the payload for SOL
        MessagePayload payload = new MessagePayload(targetComUUID, message);

        // Send the message to SOL
        ResponseEntity<String> response = restTemplate.postForEntity(solUrl, payload, String.class);

        return "Message sent to SOL: " + response.getBody();
    }

    // SOL forwards messages to target components
    @PostMapping("/forwardMessage")
    public String forwardMessage(@RequestBody MessagePayload payload) throws IOException {
        if (!component.isSol()) {
            return "Error: Only SOL can forward messages.";
        }

        String targetComUUID = payload.getTargetComUUID();
        String message = payload.getMessage();

        // Find the target component's address (IP:Port)
        String targetAddress = activeComponents.get(targetComUUID);
        if (targetAddress == null) {
            return "Error: Target component not found for UUID " + targetComUUID;
        }

        // Forward the message to the target component
        String targetUrl = "http://" + targetAddress + "/component/receiveMessage";
        ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, message, String.class);

        return "Message forwarded to " + targetComUUID + ": " + response.getBody();
    }

    // Components receive messages
    @PostMapping("/receiveMessage")
    public String receiveMessage(@RequestBody String message) {
        return "Message received: " + message;
    }

    // Register a component (for demonstration and SOL management purposes)
    @PostMapping("/registerComponent")
    public String registerComponent(@RequestParam String comUUID, @RequestParam String ip, @RequestParam int port) {
        if (!component.isSol()) {
            return "Error: Only SOL can register components.";
        }

        String address = ip + ":" + port;
        activeComponents.put(comUUID, address);
        return "Component " + comUUID + " registered with address " + address;
    }
}
