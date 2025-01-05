package com.vs.starnet.star.service;

import com.vs.starnet.star.model.Component;
import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.network.HttpHandler;
import com.vs.starnet.star.network.UdpHandler;
import com.vs.starnet.star.repository.SolRepository;
import com.vs.starnet.star.ui.CommandListener;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;

/**
 * manages interactions/operations of the component
 * interacts with sol via UDP / HTTP
 * service is responsible for discovering the sol,
 * registering the component, monitoring its status, and promoting the component to sol
 **/
@Service
public class ComponentService {
    static final Logger LOGGER = LogManager.getRootLogger();
    private static final int MAX_CONNECTION_RETRIES = 3; // Number of retries for connection to SOL
    private static final int RETRY_DELAY = 10000; // 10 seconds delay for first retry

    @Autowired
    private UdpHandler udpHandler;

    @Autowired
    private final SolRepository solRepository;

    public ComponentService(SolRepository solRepository) {
        this.solRepository = solRepository;
    }


    /**
     * Starts the component lifecycle by attempting to discover the sol
     * and initiating necessary monitoring or promotion processes.
     * Starts a UDP server thread to listen for SOL responses.
     * Attempts to discover the sol
     * registers the component with the discovered sol or promotes the component
     * to sol if none is found.
     */
    public void startComponent() {
        try {
            // Start UDP server to listen for responses
            Thread udpServerThread = new Thread(() -> {
                try {
                    udpHandler.start();

                } catch (InterruptedException e) {
                    LOGGER.error("UDP server thread interrupted", e);
                }
            });
            // Set the thread as a daemon to avoid blocking application shutdown
            udpServerThread.setDaemon(true);
            udpServerThread.start();
            boolean solDiscovered = waitForSolResponse();
            ApplicationState.getSolLock().lock();
            try {
                if (solDiscovered) {
                    LOGGER.log(Level.getLevel("STAR_INFO"), "SOL discovered. Registering with SOL...");
                    registerWithSol();
                    // Start the SOL monitoring thread
                    startSolMonitoring();
                } else {
                    LOGGER.log(Level.getLevel("STAR_INFO"), "No SOL discovered. Promoting to SOL...");
                    promoteToSol();
                    GalaxyService.discoverGalaxy();
                    solRepository.save(ApplicationState.getSolStarUuid(), Sol.builder()
                            .solStarUuid(ApplicationState.getSolStarUuid())
                            .solUuid(ApplicationState.getComUuid())
                            .comIp(ApplicationState.getIp().getHostAddress())
                            .comPort(ApplicationState.getPort())
                            .noCom(ApplicationState.getMaxComponents())
                            .status("200").build());
                    StarService.startHealthMonitoring();

                }
            } finally {
                ApplicationState.getSolLock().unlock();
            }
            ApplicationState.setIsReady(true);
        } catch (Exception e) {
            LOGGER.error("Error starting component", e);
        }
    }

    /**
     * Sends a UDP broadcast to discover the SOL and waits for a response.
     * The method sends an initial broadcast, waits 10 seconds, sends the second broadcast,
     * waits another 10 seconds, and sends the third broadcast. The method checks for a response
     * after each broadcast. If a SOL is discovered.
     *
     * @return bool for sol discovery.
     * @throws Exception if an error occurs while sending the broadcast or waiting for a response.
     */
    private boolean waitForSolResponse() throws Exception {
        LOGGER.log(Level.getLevel("STAR_INFO"), "Sending initial HELLO? broadcast...");
        UdpHandler.sendBroadcast("HELLO?", ApplicationState.getPort());

        // First check after the initial broadcast
        if (UdpHandler.isSolDiscovered()) {
            return true;
        }

        // Wait for 10 seconds and then send the second broadcast
        LOGGER.log(Level.getLevel("STAR_INFO"), "Waiting for 10 seconds before retrying...");
        Thread.sleep(RETRY_DELAY); // 10 seconds delay
        LOGGER.log(Level.getLevel("STAR_INFO"), "Sending second HELLO? broadcast...");
        UdpHandler.sendBroadcast("HELLO?", ApplicationState.getPort());

        // Check again after the second broadcast
        if (UdpHandler.isSolDiscovered()) {
            return true;
        }

        // Wait for another 10 seconds and then send the third broadcast
        LOGGER.log(Level.getLevel("STAR_INFO"), "Waiting for 10 seconds before final retry...");
        Thread.sleep(RETRY_DELAY); // 10 seconds delay
        LOGGER.log(Level.getLevel("STAR_INFO"), "Sending third HELLO? broadcast...");
        UdpHandler.sendBroadcast("HELLO?", ApplicationState.getPort());

        // Final check after the third broadcast
        if (UdpHandler.isSolDiscovered()) {
            return true;
        }

        // No SOL discovered after three broadcasts and checks
        LOGGER.log(Level.getLevel("STAR_INFO"), "No SOL discovered after three broadcasts.");
        return false;
    }

    /**
     * Registers the component with the discovered sol using an HTTP POST request.
     * This method prepares a registration payload containing the component's details
     * and sends it to the SOL. If the registration fails (non-200 response),
     * the component shuts down.
     */
    private void registerWithSol() {
        try {
            // Prepare registration data
            Component currentComponent = Component.builder()
                    .solStarUuid(ApplicationState.getSolStarUuid())
                    .solComUuid(ApplicationState.getSolComUuid())
                    .comUuid(ApplicationState.getComUuid())
                    .comIp(ApplicationState.getIp().getHostAddress())
                    .comPort(ApplicationState.getPort())
                    .status("200")
                    .build();
            // Get the SOL IP and port
            String solIp = ApplicationState.getSolIp().getHostAddress();
            int solPort = ApplicationState.getSolPort();
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Registering with SOL: {}:{}", solIp, solPort);

            // Build the registration payload
            String registrationPayload = HttpHandler.buildComponentPayload(currentComponent);
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Payload {}", registrationPayload);

            // Set endpoint URL
            String endpointUrl = "http://" + solIp + ":" + solPort + "/vs/v1/system";

            // Send registration request via TCP
            HttpResponse<String> response = HttpHandler.sendPostRequest(endpointUrl, registrationPayload, "application/json");

            if (response.statusCode() != 200) {
                LOGGER.error("Registration failed with status: {}", response.statusCode());
                shutdownComponent();
            }
        } catch (Exception e) {
            LOGGER.error("Error registering with SOL", e);
            shutdownComponent();
        }
        LOGGER.log(Level.getLevel("STAR_INFO"), "Component registered with remote SOL: {}:{}", ApplicationState.getSolIp(), ApplicationState.getSolPort());
    }

    /**
     * Periodically monitors the sol by sending HTTP PATCH requests every 30 seconds.
     * The method runs in a separate thread to ensure non-blocking operation.
     */
    private void startSolMonitoring() {
        // Create a new thread to periodically send the PATCH request to the SOL
        new Thread(() -> {
            while (true) {
                try {
                    // Every 30 seconds, send a PATCH request to SOL
                    Thread.sleep(30000);
                    updateComponentStatus();
                } catch (InterruptedException e) {
                    LOGGER.error("Error in SOL monitoring thread", e);
                }
            }
        }).start();
    }

    /**
     * Sends an HTTP PATCH request to the SOL to update the component's status.
     * The method retries up to MAX_CONNECTION_RETRIES times with a 10-second delay
     * between attempts if the update fails. If the SOL responds with specific failure codes
     * (eg 40x), the component shuts down immediately.
     * Response handling:
     * 200: Status successfully updated.
     * 401: Unauthorized; component shuts down.
     * 404: Component not found; component shuts down.
     * 409: Conflict; component shuts down.
     */
    private void updateComponentStatus() {
        int retries = 0;

        while (retries < MAX_CONNECTION_RETRIES) {
            try {
                // Prepare the status update payload
                Component currentComponent = Component.builder()
                        .solStarUuid(ApplicationState.getSolStarUuid())
                        .solComUuid(ApplicationState.getSolComUuid())
                        .comUuid(ApplicationState.getComUuid())
                        .comIp(ApplicationState.getIp().getHostAddress())
                        .comPort(ApplicationState.getPort())
                        .status("200")
                        .build();

                // Get the SOL IP and port
                String solIp = ApplicationState.getSolIp().getHostAddress();
                int solPort = ApplicationState.getSolPort();

                // Build the status update payload
                String statusUpdatePayload = HttpHandler.buildComponentPayload(currentComponent);

                // Send PATCH request to SOL
                String endpointUrl = "http://" + solIp + ":" + solPort + "/vs/v1/system/" + ApplicationState.getComUuid();

                // Send the PATCH request
                HttpResponse<String> response = HttpHandler.sendPatchRequest(endpointUrl, statusUpdatePayload, "application/json");

                LOGGER.log(Level.getLevel("STAR_DEBUG"), "Response from SOL on status update: {}", response);

                // Check the response for status codes (200, 401, 404, 409)
                if (response.statusCode() == 200) {
                    LOGGER.log(Level.getLevel("STAR_INFO"), "Component status successfully updated with remote SOL: {}:{}", solIp, solPort);
                    return;
                } else if (response.statusCode() == 401) {
                    LOGGER.error("Unauthorized request to SOL.");
                    shutdownComponent();
                } else if (response.statusCode() == 404) {
                    LOGGER.error("Component not found in SOL.");
                    shutdownComponent();
                } else if (response.statusCode() == 409) {
                    LOGGER.error("Conflict error with component status.");
                    shutdownComponent();
                } else {
                    LOGGER.error("Unexpected response from SOL: {}", response.statusCode());
                    shutdownComponent();
                }
            } catch (Exception e) {
                // When remote SOL is unreachable
                LOGGER.error("SOL is not reachable ", e);
            }

            try {
                LOGGER.log(Level.getLevel("STAR_DEBUG"), "Retrying in 10 seconds...");
                Thread.sleep(10000); // 10 seconds delay
            } catch (InterruptedException e) {
                LOGGER.error("Sleep interrupted during retry.", e);
            }
            retries++;
        }
        LOGGER.error("Failed to update component status after retries. Shutting down component.");
        shutdownComponent();
    }

    /**
     * Promotes the component to sol by initializing it in SOL mode.
     * This method is invoked when no sol is discovered during the initial setup phase.
     */
    private void promoteToSol() {
        StarService.initializeAsSOL();
    }

    /**
     * Shuts down the component by logging the event and terminating the application.
     * This method is called when critical errors occur, such as:
     * Failure to register or update the component status with the sol.
     * Unauthorized or conflict errors during communication with the sol.
     */
    private void shutdownComponent() {
        LOGGER.log(Level.getLevel("STAR_INFO"), "Component is shutting down...");
        System.exit(1); // Exit the application with a non-zero status to indicate failure
    }

    public void deregisterComponent() {
        // Get the sol IP and port
        String solIp = ApplicationState.getSolIp().getHostAddress();
        int solPort = ApplicationState.getSolPort();

        String endpointUrl = "http://" + solIp + ":" + solPort + "/vs/v1/system/" + ApplicationState.getComUuid() + "?star=" + ApplicationState.getSolStarUuid();

        // Try deregistering with retries
        for (int i = 0; i < 3; i++) { // Retry 3 times
            try {
                HttpResponse<String> response = HttpHandler.sendDeleteRequest(endpointUrl, null, "text/plain");

                if (response.statusCode() == 200) {
                    LOGGER.log(Level.getLevel("STAR_INFO"), "Component deregistered successfully.");
                    System.exit(1);
                } else if (response.statusCode() == 401) {
                    LOGGER.error("Unauthorized request to SOL.");
                    System.exit(1);
                } else if (response.statusCode() == 404) {
                    LOGGER.error("Component not found in SOL.");
                    System.exit(1);
                } else {
                    LOGGER.error("Unexpected response from SOL: {}", response);
                    System.exit(1);
                }
            } catch (Exception e) {
                LOGGER.error("Error deregistering component, attempt {}/3", i + 1, e);
                if (i < 2) {
                    sleepBeforeRetry();
                } else {
                    LOGGER.error("Failed to deregister component after 3 attempts.");
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Pauses the execution for 10 seconds.
     */
    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            throw new RuntimeException("Interrupted while sleeping before retry", e);
        }
    }

}
