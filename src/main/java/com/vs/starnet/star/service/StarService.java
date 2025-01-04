package com.vs.starnet.star.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Component;
import com.vs.starnet.star.model.Message;
import com.vs.starnet.star.network.HttpHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 */
@Service
public class StarService {
    static final Logger LOGGER = LogManager.getRootLogger();
    private static final int STATUS_CHECK_INTERVAL = 60000; // 60 seconds to check component status if no response
    private static final int TIMEOUT_THRESHOLD = 60; // 60 seconds (to trigger GET request)
    private static final ConcurrentHashMap<String, Component> components = new ConcurrentHashMap<>(); // Active components
    private static final ConcurrentHashMap<String, Component> inactiveComponents = new ConcurrentHashMap<>(); // Inactive components
    private static final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>(); // Stored messages
    private static final AtomicInteger nonce = new AtomicInteger(1); // Counter for generating MSG-UUID

    /**
     * Initialize the service as SOL.
     */
    public static void initializeAsSOL() {
        // Set the current role to SOL
        ApplicationState.setCurrentRole(NodeRole.SOL);

        try {
            // Generate star UUID based on the SOL address, COM-UUID, and group ID
            ApplicationState.setStarUuid(generateStarUuid(ApplicationState.getIp(), ApplicationState.getComUuid(), ApplicationState.getGroupId()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate STAR-UUID", e);
        }

        // Set the SOL details
        ApplicationState.setSolIp(ApplicationState.getIp());
        ApplicationState.setSolPort(ApplicationState.getPort());
        ApplicationState.setSolStarUuid(ApplicationState.getStarUuid());
        ApplicationState.setSolComUuid(ApplicationState.getComUuid());

        Component component = Component.builder()
                .solStarUuid(ApplicationState.getStarUuid())
                .solComUuid(ApplicationState.getComUuid())
                .comUuid(ApplicationState.getComUuid())
                .comIp(ApplicationState.getIp().getHostAddress())
                .comPort(ApplicationState.getPort())
                .status("200")
                .lastInteractionTime(new AtomicReference<>(Instant.now()))
                .integrationTime(Instant.now())
                .build();

        // Register the SOL component to the own registry
        components.put(ApplicationState.getComUuid(), component);

        // Log the SOL component
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "SOL component registered:");
        LOGGER.log(Level.getLevel("STAR_DEBUG"), components.get(component.getComUuid()));

        // Print SOL details
        printSolDetails();
    }

    public static void printSolDetails() {
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "SOL details:");
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "STAR-UUID: {}", ApplicationState.getStarUuid());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "COM-UUID: {}", ApplicationState.getComUuid());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "IP: {}", ApplicationState.getIp());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "StarPort: {}", ApplicationState.getPort());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "GalaxyPort: {}", ApplicationState.getGalaxyPort());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Group ID: {}", ApplicationState.getGroupId());
    }

    /**
     * Register a new component.
     *
     * @param component the component to register
     * @return the status of the registration
     */
    public ResponseEntity<String> registerComponent(Component component) {
        if (!ApplicationState.getIsReady() || ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.warn("Service unavailable");
            return new ResponseEntity<>("503 service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "registerComponent request {}", component);
        // Validate the STAR-UUID
        if (!ApplicationState.getStarUuid().equals(component.getSolStarUuid())) {
            LOGGER.warn("STAR-UUID not matching");
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Validate the COM-UUID
        if (!ApplicationState.getComUuid().equals(component.getSolComUuid())) {
            LOGGER.warn("COM-UUID not matching");
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Check if the SOL has room for more components
        if (components.size() >= ApplicationState.getMaxComponents()) {
            LOGGER.warn("Max components reached - no room left");
            return new ResponseEntity<>("403 no room left", HttpStatus.FORBIDDEN);
        }

        // Check if the COM-UUID is already registered
        if (components.containsKey(component.getComUuid())) {
            LOGGER.warn("COM-UUID already registered");
            return new ResponseEntity<>("409 conflict", HttpStatus.CONFLICT);
        }

        // Add the component to the registry
        component.setIntegrationTime(Instant.now());
        component.setLastInteractionTime(new AtomicReference<>(Instant.now()));

        // Register the component
        components.put(component.getComUuid(), component);
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Component registered: {}", components.get(component.getComUuid()));
        LOGGER.log(Level.getLevel("STAR_INFO"), "Component registered: {}", component.getComUuid());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "New component size: {}", components.size());

        return new ResponseEntity<>("200 ok", HttpStatus.OK);
    }


    /**
     * Update component status.
     *
     * @param comUuid   the UUID of the component
     * @param component the updated component data
     * @return the status of the update
     */
    public ResponseEntity<String> updateComponent(String comUuid, Component component) {
        if (!ApplicationState.getIsReady()) {
            LOGGER.warn("Service unavailable");
            return new ResponseEntity<>("503 service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "updateComponent request {}", component);
        // Check if the component exists
        if (!components.containsKey(comUuid)) {
            LOGGER.warn("Component {} does not exist.", comUuid);
            return new ResponseEntity<>("404 does not exist", HttpStatus.NOT_FOUND);
        }

        // Validate the STAR-UUID
        if (!ApplicationState.getStarUuid().equals(component.getSolStarUuid())) {
            LOGGER.warn("STAR-UUID not matching");
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Validate the COM-UUID
        if (!ApplicationState.getComUuid().equals(component.getSolComUuid())) {
            LOGGER.warn("COM-UUID not matching");
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Validate the component data IP and port
        if (!components.get(component.getComUuid()).getComIp().equals(component.getComIp()) || components.get(component.getComUuid()).getComPort() != component.getComPort() || !component.getStatus().equals("200")) {
            LOGGER.warn("Component data not matching");
            return new ResponseEntity<>("409 conflict", HttpStatus.CONFLICT);
        }

        components.get(comUuid).setLastInteractionTime(new AtomicReference<>(Instant.now()));

        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Component updated: {}", components.get(component.getComUuid()));

        return new ResponseEntity<>("200 ok", HttpStatus.OK);
    }


    /**
     * Deregister a component.
     *
     * @param comUuid the UUID of the component to deregister
     * @return the status of the deregistration
     */
    public ResponseEntity<String> deregisterComponent(String comUuid, String star, HttpServletRequest request) {
        String senderIp = request.getRemoteAddr();
        int senderPort = request.getRemotePort(); // unused atm as source port which the request came from can't be set by the sender with high level libraries like HttpClient or Spring

        if (!ApplicationState.getIsReady()) {
            LOGGER.warn("Service unavailable");
            return new ResponseEntity<>("503 service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "deregisterComponent request {}", comUuid);

        if (comUuid == null || comUuid.equals("null") || comUuid.isEmpty()) {
            LOGGER.warn("COM-UUID is missing");
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        // Validate the STAR-UUID
        if (!ApplicationState.getSolStarUuid().equals(star)) {
            LOGGER.warn("STAR-UUID not matching");
            return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
        }

        if (ApplicationState.getCurrentRole() == NodeRole.SOL) { // When the SOL is receiving a deregister request from a component

            // Check if the component exists
            if (!components.containsKey(comUuid)) {
                LOGGER.warn("Component not found");
                return new ResponseEntity<>("404 not found", HttpStatus.NOT_FOUND);
            }

            // Validate ip
            if (!components.get(comUuid).getComIp().equals(senderIp)) {
                LOGGER.warn("IP not matching");
                return new ResponseEntity<>("401 unauthorized", HttpStatus.UNAUTHORIZED);
            }

            components.get(comUuid).setLastInteractionTime(new AtomicReference<>(Instant.now()));
            components.get(comUuid).setStatus("left");
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Component status updated to left");

            // Deregister the component and put it into the inactive components map
            moveComponentToInactive(comUuid);

        } else if (ApplicationState.getCurrentRole() == NodeRole.COMPONENT) { // When the component is receiving a deregister request from SOL
            // If remote is SOL and component is receiving a deregister request check if starUuid, ip and comUuid match if so terminate the program
            if (ApplicationState.getSolStarUuid().equals(star) && ApplicationState.getSolIp().getHostAddress().equals(senderIp) && ApplicationState.getComUuid().equals(comUuid)) {
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                        System.exit(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }

        return new ResponseEntity<>("200 ok", HttpStatus.OK);
    }

    public ResponseEntity<Map<String, String>> getComponentStatus(String comUuid, String star) {
        if (!ApplicationState.getIsReady()) {
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.SERVICE_UNAVAILABLE);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "getComponentStatus request {}", comUuid);
        // Validate the STAR-UUID
        if (!ApplicationState.getStarUuid().equals(star)) {
            LOGGER.warn("STAR-UUID not matching");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.UNAUTHORIZED);
        }

        if (comUuid == null || comUuid.equals("null") || comUuid.isEmpty()) {
            LOGGER.warn("COM-UUID is missing");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.UNAUTHORIZED);
        }

        Component component = components.get(comUuid);

        // Validate that the component exists
        if (component == null) {
            LOGGER.warn("Component not found");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.CONFLICT);
        }

        // Validate that the provided data matches the component's stored details
        if (!component.getComUuid().equals(comUuid)) {
            LOGGER.warn("COM-UUID not matching");
            return new ResponseEntity<>(Map.of("", ""), HttpStatus.CONFLICT);
        }

        // Create the response map that contains both the component data and request status
        Map<String, String> response = Map.of(
                "star", component.getSolStarUuid(),
                "sol", ApplicationState.getComUuid(),
                "component", component.getComUuid(),
                "com-ip", component.getComIp(),
                "com-tcp", String.valueOf(component.getComPort()),
                "status", component.getStatus()
        );

        // Return the response as JSON with HTTP status 200 OK
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public static void startHealthMonitoring() {
        new Thread(() -> {
            while (true) {
                performHealthCheck();
            }
        }).start();
    }

    private static void performHealthCheck() {
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            Instant currentTime = Instant.now();
            AtomicReference<Instant> lastInteractionTime = entry.getValue().getLastInteractionTime();
            String status = entry.getValue().getStatus();

            if (lastInteractionTime != null) {
                // Check if the component's last interaction time is more than 60 seconds ago
                // and the status is not "200" or "left" or "disconnected"
                if (currentTime.minusSeconds(TIMEOUT_THRESHOLD).isAfter(lastInteractionTime.get()) && !status.equals("left") && !status.equals("disconnected")) {
                    LOGGER.log(
                            Level.getLevel("STAR_INFO"),
                            "Component {}{} has not interacted for 60 seconds. Performing health check...",
                            entry.getKey(),
                            entry.getKey().equals(ApplicationState.getSolComUuid())
                                    ? " (This is the SOL Component)"
                                    : ""
                    );

                    // Send a GET request to SOL to check if the component is still active
                    checkComponentHealth(entry.getValue());
                }
            }
        }
    }

    public static void checkComponentHealth(Component component) {
        try {
            // Prepare the endpoint URL for checking the component status
            String endpointUrl = "http://" + component.getComIp() + ":" +
                    component.getComPort() + "/vs/v1/system/" + component.getComUuid() +
                    "?star=" + ApplicationState.getSolStarUuid();

            // Attempt to send a GET request to check the status of the component
            HttpResponse<String> response = HttpHandler.sendGetRequest(endpointUrl); // null since no payload is needed

            // Parse the JSON response
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseJson = objectMapper.readTree(response.body());

            // Extract the response status and component status
            // unused atm
            String componentStar = responseJson.path("star").asText();
            String componentSol = responseJson.path("sol").asText();
            String componentUuid = responseJson.path("component").asText();
            String componentIp = responseJson.path("com-ip").asText();
            int componentPort = responseJson.path("com-tcp").asInt();
            String componentStatus = responseJson.path("status").asText();


            // Check if the response status is 200 OK
            if (response.statusCode() == 200) {
                LOGGER.log(Level.getLevel("STAR_INFO"), "Component {} is still active.", component.getComUuid());
                // Update the component status
                component.setLastInteractionTime(new AtomicReference<>(Instant.now()));
                LOGGER.log(Level.getLevel("STAR_INFO"), "Component {} last interaction time updated.", component.getComUuid());
            } else if (response.statusCode() == 401) {
                LOGGER.error("Unauthorized access to component {}.", component.getComUuid());
            } else if (response.statusCode() == 409) {
                LOGGER.error("Conflict detected with component {}.", component.getComUuid());
            } else {
                LOGGER.error("Unexpected response from component {}: {}.", component.getComUuid(), response);
            }
        } catch (Exception e) {
            // If remote component is not reachable, mark it as disconnected
            LOGGER.error("Error checking component status as component is not reachable.", e);
            component.setStatus("disconnected");
            // Deregister the component and put it into the inactive components map
            moveComponentToInactive(component.getComUuid());
            LOGGER.log(Level.getLevel("STAR_INFO"), "Component {} marked as disconnected.", component.getComUuid());
        }
    }

    public void deregisterComponents() {
        LOGGER.log(Level.getLevel("STAR_INFO"), "Deregistering all active components...");

        // Iterate over all active components in the star
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            // Skip if the component has left or is already disconnected or is the SOL itself
            if (entry.getValue().getStatus().equals("left") || entry.getValue().getStatus().equals("disconnected") || entry.getValue().getComUuid().equals(ApplicationState.getComUuid())) {
                continue;
            }

            Component component = entry.getValue();

            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Attempting to deregister component: {}", component.getComUuid());

            // Try deregistering the component with retries
            boolean success = attemptDeregisterComponent(component);

            if (success) {
                // Update the component's status to disconnected
                component.setStatus("disconnected");
                LOGGER.log(Level.getLevel("STAR_DEBUG"), "Component {} successfully deregistered and marked as disconnected.", component.getComUuid());
            } else {
                LOGGER.error("Failed to deregister component {} after multiple attempts.", component.getComUuid());
            }
        }
        LOGGER.log(Level.getLevel("STAR_INFO"), "All active components have been processed. Shutting down SOL...");
        System.exit(1);
    }

    /**
     * Attempts to deregister a component with retries.
     *
     * @param component The component to deregister.
     * @return {@code true} if the component was successfully deregistered; otherwise, {@code false}.
     */
    private boolean attemptDeregisterComponent(Component component) {
        String endpointUrl = "http://" + component.getComIp() + ":" + component.getComPort()
                + "/vs/v1/system/" + component.getComUuid()
                + "?star=" + ApplicationState.getSolStarUuid();

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> response = HttpHandler.sendDeleteRequest(endpointUrl, null, "text/plain");

                if (response.statusCode() == 200) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Component {} responded with 200 OK on attempt {}.", component.getComUuid(), attempt);
                    return true; // Success
                } else if (response.statusCode() == 401) {
                    LOGGER.error("Component {} responded with 401 Unauthorized. Ignoring further attempts.", component.getComUuid());
                    return false; // Unauthorized, stop further attempts
                } else {
                    LOGGER.error("Unexpected response from component {}: {}. Attempt {}.", component.getComUuid(), response, attempt);
                }
            } catch (Exception e) {
                LOGGER.error("Error during deregistration of component {} on attempt {}: {}", component.getComUuid(), attempt, e.getMessage());
            }

            // Sleep before retrying (only for the first two attempts)
            if (attempt < 3) {
                try {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Sleeping for 10 seconds before next attempt.");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    LOGGER.error("Retry sleep interrupted.", e);
                }
            }
        }

        return false; // Failed after all attempts
    }

    /**
     * Move a component to the inactive components map.
     *
     * @param comUuid the UUID of the component
     */
    private static void moveComponentToInactive(String comUuid) {
        Component component = components.remove(comUuid);
        if(component != null) {
            inactiveComponents.put(comUuid, component);
        }
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Component deregistered: {}", inactiveComponents.get(comUuid));
        System.out.println(""+components);
        LOGGER.log(Level.getLevel("STAR_INFO"), "Component deregistered: {}", comUuid);
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "New component size: {}", components.size());
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "New inactive component size: {}", inactiveComponents.size());
    }

    /**
     * Generate a STAR-UUID as MD5 hash.
     *
     * @param solAddress the address of the SOL
     * @param comUuid    the UUID of the component
     * @param groupId    the group ID
     * @return the generated STAR-UUID as MD5 hash
     * @throws NoSuchAlgorithmException if the MD5 algorithm is not available
     */
    private static String generateStarUuid(InetAddress solAddress, String comUuid, String groupId) throws NoSuchAlgorithmException {
        String input = solAddress.getHostAddress() + groupId + comUuid;
        return hash(input);
    }

    /**
     * Generate a hash from the input string.
     *
     * @param input the input string
     * @return the hash as a hex string
     * @throws NoSuchAlgorithmException if the MD5 algorithm is not available
     */
    private static String hash(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest(input.getBytes());
        return convertToHex(hashBytes);
    }

    /**
     * Convert a byte array to a hex string.
     *
     * @param bytes the byte array
     * @return the hex string
     */
    private static String convertToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
