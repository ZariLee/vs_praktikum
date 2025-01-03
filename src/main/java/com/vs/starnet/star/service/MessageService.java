package com.vs.starnet.star.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.network.HttpHandler;
import com.vs.starnet.star.repository.SolRepository;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final Logger LOGGER = LogManager.getRootLogger();
    private static final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>();
    private static final AtomicInteger nonce = new AtomicInteger(1); // Counter for unique message IDs

    @Autowired
    private SolRepository solRepository;

    /**
     * Creates or forwards a message.
     *
     * @param message The message to process.
     * @return A ResponseEntity containing the response status and message ID.
     */
    public ResponseEntity<Map<String, String>> handleMessageV1(Message message) {
        if (!ApplicationState.getIsReady()) {
            LOGGER.warn("Service unavailable");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Received message: {}", message);

        // Validate the STAR-UUID
        if (!ApplicationState.getSolStarUuid().equals(message.getStar())) {
            LOGGER.warn("STAR-UUID mismatch: Received {}, expected {}", message.getStar(), ApplicationState.getSolStarUuid());
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.UNAUTHORIZED);
        }

        // Validate the "origin" field
        if (message.getOrigin() == null ||
                message.getOrigin().isEmpty() ||
                (!isComUuid(message.getOrigin()) && !isEmailAddress(message.getOrigin()))) {
            LOGGER.warn("Message origin is empty or invalid.");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.PRECONDITION_FAILED);
        }

        // Validate the "sender" field
        if (message.getSender() == null || message.getSender().isEmpty()) {
            LOGGER.warn("Message sender is empty or invalid.");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.PRECONDITION_FAILED);
        }

        // Validate the "subject" field
        if (message.getSubject() == null || message.getSubject().isEmpty()) {
            LOGGER.warn("Message subject is empty or invalid.");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.PRECONDITION_FAILED);
        }

        // Set the sender to the current component UUID
        message.setSender(ApplicationState.getComUuid());
        message.setVersion("1");

        // Process and clean up the subject
        String cleanedSubject = message.getSubject().replace("\r", "").split("\n")[0];
        message.setSubject(cleanedSubject);

        // Check for duplicate messages
        if (messages.containsKey(message.getMsgId())) {
            LOGGER.warn("Message with ID {} already exists.", message.getMsgId());
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.CONFLICT);
        }

        if (ApplicationState.getCurrentRole() == NodeRole.SOL) {
            return processMessageAsSol(message);
        } else if (ApplicationState.getCurrentRole() == NodeRole.COMPONENT) {
            return forwardMessageToSol(message, null);
        } else {
            LOGGER.error("Invalid application role '{}'.", ApplicationState.getCurrentRole());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected application role"));
        }
    }

    private ResponseEntity<Map<String, String>> forwardMessageToSol(Message message, Sol sol) {
        String endpointUrl;
        // If messages is from component
        if(sol==null){
            endpointUrl = "http://" + ApplicationState.getSolIp().getHostAddress() + ":" + ApplicationState.getSolPort() + "/vs/v2/messages";
        } else{
            endpointUrl = "http://" + sol.getComIp() + ":" + sol.getComPort() + "/vs/v2/messages";
        }

        try {
            String jsonMessage = HttpHandler.buildMessagePayload(message);
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Forwarding message payload to SOL: {}", jsonMessage);

            HttpResponse<String> response = HttpHandler.sendPostRequest(endpointUrl, jsonMessage, "application/json");

            if (response.statusCode() != 200) {
                LOGGER.error("SOL returned error: Status {}, Body '{}'.", response.statusCode(), response.body());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to forward message to SOL"));
            }

            // Parse SOL response for msg-id
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            String msgId = jsonResponse.get("msg-id").asText();
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Message received from SOL has ID '{}'.", msgId);
            return ResponseEntity.ok(Map.of("msg-id", msgId));
        } catch (Exception e) {
            LOGGER.error("Error forwarding message to SOL: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error while forwarding message"));
        }
    }

    private void getMessageCountsByOrigin() {
        Map<String, Long> originCounts = messages.values().stream()
                .collect(Collectors.groupingBy(Message::getOrigin, Collectors.counting()));
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Message counts by origin: {}", originCounts);
    }

    public void printAllMessages() {
        // Check if there are any messages
        if (messages.isEmpty()) {
            System.out.println("No messages available.");
            return;
        }

        // Print all messages
        LOGGER.log(Level.getLevel("STAR_DEBUG"),"Listing all messages:");
        messages.forEach((msgId, message) -> {
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Message ID: {}", msgId);
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Message Details: {}", message);
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "--------------");
        });
    }

    private ResponseEntity<Map<String, String>> handleMessageCreationAsSol(Message message) {
        // Generate message ID and set timestamps
        String msgUuid = nonce.getAndIncrement() + "@" + message.getOrigin();
        long currentTime = Instant.now().getEpochSecond();

        message.setMsgId(msgUuid);
        message.setCreated(currentTime);
        message.setChanged(currentTime);
        message.setStatus("active");

        // Store the message
        messages.put(msgUuid, message);
        getMessageCountsByOrigin();
        LOGGER.log(Level.getLevel("STAR_INFO"), "Message with ID '{}' created successfully.", msgUuid);
        return ResponseEntity.ok(Map.of("msg-id", msgUuid));
    }

    private boolean isComUuid(String comUuid) {
        return Integer.parseInt(comUuid) >= 1000 || Integer.parseInt(comUuid) <= 9999;
    }

    /**
     * Checks if a given value is a valid email address.
     *
     * @param address the email address to validate
     * @return true if the address is valid, false otherwise
     */
    private boolean isEmailAddress(String address) {
        EmailValidator validator = EmailValidator.getInstance();
        return validator.isValid(address);
    }

    public ResponseEntity<Map<String, String>> handleMessageV2(Message message){
        // Validate service readiness
        if (!ApplicationState.getIsReady()) {
            LOGGER.warn("Service unavailable");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Received v2 message: {}", message);

        // Validate the STAR-UUID
        if (!ApplicationState.getSolStarUuid().equals(message.getStar())) {
            LOGGER.warn("STAR-UUID mismatch: Received {}, expected {}", message.getStar(), ApplicationState.getSolStarUuid());
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.UNAUTHORIZED);
        }

        // Validate the "origin" field
        if (message.getOrigin() == null ||
                message.getOrigin().isEmpty() ||
                (!isComUuid(message.getOrigin().split(":")[0]) && !isEmailAddress(message.getOrigin().split(":")[0]))) {
            LOGGER.warn("Message origin is empty or invalid.");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.PRECONDITION_FAILED);
        }

        // Validate the "sender" and "subject" fields
        if (message.getSender() == null || message.getSender().isEmpty() ||
                message.getSubject() == null || message.getSubject().isEmpty()) {
            LOGGER.warn("Message sender or subject is invalid.");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.PRECONDITION_FAILED);
        }

        // Set the sender to the current component UUID thus overwrite anything which is set before
        message.setSender(ApplicationState.getComUuid());
        message.setVersion("1");

        // Process and clean up the subject
        String cleanedSubject = message.getSubject().replace("\r", "").split("\n")[0];
        message.setSubject(cleanedSubject);

        // Check for duplicate messages
        if (messages.containsKey(message.getMsgId())) {
            LOGGER.warn("Message with ID {} already exists.", message.getMsgId());
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.CONFLICT);
        }

        if (ApplicationState.getCurrentRole() == NodeRole.SOL) {
            return processMessageAsSol(message);
        } else if (ApplicationState.getCurrentRole() == NodeRole.COMPONENT) {
            return forwardMessageToSol(message, null);
        } else {
            LOGGER.error("Invalid application role '{}'.", ApplicationState.getCurrentRole());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected application role"));
        }
    }



    private String updateOrigin(String origin) {
        return origin + ":" + ApplicationState.getSolStarUuid();
    }

    private ResponseEntity<Map<String, String>> processMessageAsSol(Message message) {
        long currentTime = Instant.now().getEpochSecond();

        // Generate msgUuid if absent message comes from a component
        if (message.getMsgId() == null || message.getMsgId().isEmpty()) {
            message.setMsgId(generateMsgUuid(message));
        }

        // Check if msgUuid was set by the current server so a component sent the message
        if(message.getMsgId().split(":")[1].equals(ApplicationState.getSolStarUuid())){
            message.setCreated(currentTime);
            // Validate and update the origin field
            message.setOrigin(updateOrigin(message.getOrigin()));
        } else {
            // If the message was sent by a star then set fromStar and received
            message.setFromStar(message.getOrigin().split(":")[1]);
            message.setReceived(currentTime);
        }

        message.setChanged(currentTime);
        message.setStatus("active");

        // Store the message
        messages.put(message.getMsgId(), message);
        getMessageCountsByOrigin();
        printAllMessages();

        // Send message to all sols
        for (Sol sol : solRepository.findAll().values()) {
            // Skip sending the message to the origin star and to the own star
            if (!sol.getSolStarUuid().equals(ApplicationState.getSolStarUuid()) && !message.getOrigin().split(":")[1].equals(sol.getSolStarUuid())) {
                Message forwardedMessage = Message.builder()
                        .msgId(message.getMsgId())
                        .version(message.getVersion())
                        .status(message.getStatus())
                        .origin(message.getOrigin())
                        .sender(message.getSender())
                        .subject(message.getSubject())
                        .message(message.getMessage())
                        .created(message.getCreated())
                        .changed(message.getChanged())
                        .star(sol.getSolStarUuid())
                        .toStar(sol.getSolStarUuid())
                        .delivered(currentTime)
                        .build();
                forwardMessageToSol(forwardedMessage, sol);
            }
        }

        LOGGER.log(Level.getLevel("STAR_INFO"), "v2 Message with ID '{}' created successfully.", message.getMsgId());
        return ResponseEntity.ok(Map.of("msg-id", message.getMsgId()));
    }

    private String generateMsgUuid(Message message) {
        // Generate a unique message ID in the v2 format
        return nonce.getAndIncrement() + "@" + message.getOrigin() + ":" + ApplicationState.getSolStarUuid();
    }

    /**
     * Deletes a message identified by its ID.
     *
     * @param msgId The ID of the message to delete.
     * @param star  The STAR-UUID to validate the request.
     * @return A ResponseEntity indicating the status of the deletion.
     */
    public ResponseEntity<String> deleteMessage(String msgId, String star) {
        if (!ApplicationState.getIsReady()) {
            LOGGER.warn("Service unavailable");
            return new ResponseEntity<>("503 service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Received delete request for message ID: {}, STAR-UUID: {}", msgId, star);

        // Validate the STAR-UUID
        if (!ApplicationState.getSolStarUuid().equals(star)) {
            LOGGER.warn("STAR-UUID mismatch: Received {}, expected {}", star, ApplicationState.getSolStarUuid());
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Validate the MSG-UUID
        if (msgId == null || msgId.isEmpty()) {
            LOGGER.warn("Message ID is empty.");
            return new ResponseEntity<>("404 does not exist", HttpStatus.NOT_FOUND);
        }

        if (ApplicationState.getCurrentRole() == NodeRole.SOL) {
            return handleDeleteAsSol(msgId);
        } else if (ApplicationState.getCurrentRole() == NodeRole.COMPONENT) {
            return forwardDeleteToSol(msgId, star);
        } else {
            LOGGER.error("Invalid application role '{}'.", ApplicationState.getCurrentRole());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected application role");
        }
    }

    private ResponseEntity<String> handleDeleteAsSol(String msgId) {
        Message message = messages.get(msgId);

        // Check if the message exists
        if (message == null) {
            LOGGER.warn("Message with ID {} does not exist.", msgId);
            return new ResponseEntity<>("404 does not exist", HttpStatus.NOT_FOUND);
        }

        if (messages.get(msgId).getStatus().equals("deleted")) {
            LOGGER.warn("Message with ID {} is already deleted.", msgId);
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Update message status and timestamp
        long currentTime = Instant.now().getEpochSecond();
        message.setStatus("deleted");
        message.setChanged(currentTime);
        message.setMessage("");

        LOGGER.log(Level.getLevel("STAR_INFO"), "Message with ID '{}' marked as deleted.", msgId);
        return new ResponseEntity<>("200 ok", HttpStatus.OK);
    }

    private ResponseEntity<String> forwardDeleteToSol(String msgId, String star) {
        String endpointUrl = "http://" + ApplicationState.getSolIp().getHostAddress() + ":" + ApplicationState.getSolPort() + "/vs/v1/messages/" + msgId + "?star=" + star;

        try {
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Forwarding delete request for message ID {} to SOL.", msgId);

            HttpResponse<String> response = HttpHandler.sendDeleteRequest(endpointUrl, null, "text/plain");

            if (response.statusCode() == 200) {
                LOGGER.log(Level.getLevel("STAR_INFO"), "SOL confirmed deletion for message ID '{}'.", msgId);
                return new ResponseEntity<>("200 ok", HttpStatus.OK);
            } else if (response.statusCode() == 401) {
                LOGGER.warn("SOL rejected delete request for message ID '{}': Unauthorized.", msgId);
                return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
            } else if (response.statusCode() == 404) {
                LOGGER.warn("SOL reported message ID '{}' does not exist.", msgId);
                return new ResponseEntity<>("404 does not exist", HttpStatus.NOT_FOUND);
            } else {
                LOGGER.error("Unexpected response from SOL: Status {}, Body '{}'.", response.statusCode(), response.body());
                return new ResponseEntity<>("500 internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            LOGGER.error("Error forwarding delete request to SOL: {}", e.getMessage());
            return new ResponseEntity<>("500 internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<?> getMessages(String star, String scope, String view) {
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Received request to retrieve messages with scope: {}, view: {}", scope, view);

        // Validate the STAR-UUID
        if (!ApplicationState.getSolStarUuid().equals(star)) {
            LOGGER.warn("STAR-UUID mismatch: Received {}, expected {}", star, ApplicationState.getSolStarUuid());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "401 unauthorized"));
        }
        if (ApplicationState.getCurrentRole() == NodeRole.SOL) {
            return fetchMessagesFromLocal(star, scope, view);
        }

        return forwardRequestToSol(star, scope, view);

    }

    private ResponseEntity<?> fetchMessagesFromLocal(String star, String scope, String view) {
        // Logic to retrieve messages locally
        LOGGER.log(Level.getLevel("STAR_INFO"), "Fetching messages locally with scope={} and view={}", scope, view);

        // Determine the default values for scope and view
        String messageScope = (scope == null || scope.isEmpty()) ? "active" : scope;
        view = (view == null || view.isEmpty()) ? "id" : view;

        // Filter messages based on the scope
        List<Message> filteredMessages = messages.values().stream()
                .filter(message -> messageScope.equals("all") || "active".equals(message.getStatus()))
                .toList();

        // Prepare the response
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("star", star);
        response.put("totalResults", filteredMessages.size());
        response.put("scope", scope);
        response.put("view", view);

        // Construct the message list based on the view
        List<Map<String, Object>> messageList = new ArrayList<>();
        for (Message message : filteredMessages) {
            LinkedHashMap<String, Object> messageData = new LinkedHashMap<>();
            messageData.put("msg-id", message.getMsgId());
            messageData.put("status", message.getStatus());

            if ("header".equals(view)) {
                if (!"deleted".equalsIgnoreCase(message.getStatus())) {
                    messageData.put("version", message.getVersion());
                    messageData.put("origin", message.getOrigin());
                    messageData.put("created", message.getCreated());
                    messageData.put("changed", message.getChanged());
                    messageData.put("subject", message.getSubject());
                }
            }

            messageList.add(messageData);
        }

        response.put("messages", messageList);

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> forwardRequestToSol(String star, String scope, String view) {
        String solIp = ApplicationState.getSolIp().getHostAddress();
        int solPort = ApplicationState.getSolPort();
        String endpointUrl = String.format("http://%s:%d/vs/v1/messages?star=%s&scope=%s&view=%s",
                solIp, solPort, star, scope, view);

        try {
            LOGGER.log(Level.getLevel("STAR_INFO"), "Forwarding request to SOL: {}", endpointUrl);

            // Forward the request to SOL
            HttpResponse<String> response = HttpHandler.sendGetRequest(endpointUrl);

            // Parse and forward the SOL response
            if (response.statusCode() == 200) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                return new ResponseEntity<>(objectMapper.convertValue(jsonResponse, Map.class), HttpStatus.OK);
            }

            // Handle errors from SOL
            LOGGER.error("SOL returned error: Status {}, Body '{}'", response.statusCode(), response.body());
            return ResponseEntity.status(response.statusCode()).body(response.body());

        } catch (Exception e) {
            LOGGER.error("Error forwarding request to SOL: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to forward request to SOL"));
        }
    }

    public ResponseEntity<?> getMessage(String msgId, String star) {
        LOGGER.log(Level.getLevel("STAR_INFO"), "Fetching message with ID '{}' for star '{}'", msgId, star);

        // Validate the STAR-UUID
        if (!ApplicationState.getSolStarUuid().equals(star)) {
            LOGGER.warn("STAR-UUID mismatch: Received {}, expected {}", star, ApplicationState.getSolStarUuid());
            LinkedHashMap<String, Object> response = new LinkedHashMap<>();
            response.put("star", star);
            response.put("totalResults", 0);
            response.put("messages", List.of());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(response);
        }

        // Split logic based on role
        if (ApplicationState.getCurrentRole().equals(NodeRole.SOL)) {
            return handleRequestAsSol(msgId, star);
        } else {
            return forwardRequestToSol(msgId, star);
        }
    }

    /**
     * Handles the GET message request when the current component is SOL.
     *
     * @param msgId The message ID to fetch.
     * @param star  The STAR-UUID of the request.
     * @return ResponseEntity containing the message or an error response.
     */
    private ResponseEntity<?> handleRequestAsSol(String msgId, String star) {
        // Check if msgId is null or empty
        if (msgId == null || msgId.isEmpty()) {
            LOGGER.warn("No MSG-UUID provided");
            LinkedHashMap<String, Object> response = new LinkedHashMap<>();
            response.put("star", star);
            response.put("totalResults", 0);
            response.put("messages", List.of());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        // Fetch the message
        Message message = messages.get(msgId);

        // If message doesn't exist
        if (message == null) {
            LOGGER.warn("Message with ID '{}' not found", msgId);
            LinkedHashMap<String, Object> response = new LinkedHashMap<>();
            response.put("star", star);
            response.put("totalResults", 0);
            response.put("messages", List.of());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }

        // Prepare response for deleted message
        if ("deleted".equalsIgnoreCase(message.getStatus())) {
            LOGGER.log(Level.getLevel("STAR_INFO"),"Message with ID '{}' is marked as deleted", msgId);
            LinkedHashMap<String, Object> response = new LinkedHashMap<>();
            response.put("star", star);
            response.put("totalResults", 1);
            LinkedHashMap<String, Object> messageDetails = new LinkedHashMap<>();
            messageDetails.put("msg-id", message.getMsgId());
            messageDetails.put("status", message.getStatus());
            response.put("messages", List.of(messageDetails));
            return ResponseEntity.ok(response);
        }

        // Prepare response for active message
        LinkedHashMap<String, Object> messageDetails = new LinkedHashMap<>();
        messageDetails.put("msg-id", message.getMsgId());
        messageDetails.put("version", message.getVersion());
        messageDetails.put("status", message.getStatus());
        messageDetails.put("origin", message.getOrigin());
        messageDetails.put("created", message.getCreated());
        messageDetails.put("changed", message.getChanged());
        messageDetails.put("subject", message.getSubject());
        messageDetails.put("message", message.getMessage());

        LOGGER.log(Level.getLevel("STAR_INFO"),"Message with ID '{}' retrieved successfully", msgId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("star", star);
        response.put("totalResults", 1);
        response.put("messages", List.of(messageDetails));

        return ResponseEntity.ok(response);
    }

    /**
     * Forwards the GET message request to SOL if the current role is not SOL.
     *
     * @param msgId The message ID to fetch.
     * @param star  The STAR-UUID of the request.
     * @return ResponseEntity containing the forwarded response or an error response.
     */
    private ResponseEntity<?> forwardRequestToSol(String msgId, String star) {
        LOGGER.log(Level.getLevel("STAR_INFO"),"Current role is '{}'. Forwarding request to SOL.", ApplicationState.getCurrentRole());

        String solEndpointUrl = "http://" + ApplicationState.getSolIp().getHostAddress() + ":" + ApplicationState.getSolPort() + "/vs/v1/messages/" + msgId + "?star=" + star;

        try {
            HttpResponse<String> response = HttpHandler.sendGetRequest(solEndpointUrl);

            if (response.statusCode() != 200) {
                LOGGER.error("SOL returned error: Status {}, Body '{}'.", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode())
                        .body(Map.of("error", "Failed to retrieve message from SOL"));
            }

            // Parse and return the forwarded response
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            LOGGER.error("Error forwarding request to SOL: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to forward request to SOL"));
        }
    }
}


