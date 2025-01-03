package com.vs.starnet.star.network;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.repository.SolRepository;
import com.vs.starnet.star.service.ApplicationState;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class UdpHandler {
    private static final Logger LOGGER = LogManager.getRootLogger();
    private static final AtomicBoolean solDiscovered = new AtomicBoolean(false);
    private final BlockingQueue<DatagramPacket> messageQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    @Autowired
    private final SolRepository solRepository;

    @Autowired
    public UdpHandler(SolRepository solRepository) {
        this.solRepository = solRepository;
    }

    /**
     * Starts the UDP server thread to handle Star and Galaxy requests.
     * This method initializes the UDP server and binds it to the specified ports
     * (serverPort and galaxyPort). It also starts a separate thread to process incoming
     * UDP messages asynchronously.
     *
     * @throws InterruptedException if the thread is interrupted during operation.
     */
    public void start() throws InterruptedException {
        // Use the same eventLoopGroup to process messages instead of creating a new thread
        eventLoopGroup.execute(this::processMessages);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                                    enqueueMessage(packet);
                                }
                            });
                        }
                    });

            // Bind to both ports using the same group and channel.
            bootstrap.bind(ApplicationState.getPort()).sync();
            bootstrap.bind(ApplicationState.getGalaxyPort()).sync();
            LOGGER.log(Level.INFO, "UDP server started on ports {} (Star) and {} (Galaxy)", ApplicationState.getPort(), ApplicationState.getGalaxyPort());

            eventLoopGroup.terminationFuture().sync();
        } finally {
            running = false;
        }
    }

    /**
     * Enqueues incoming UDP requests to the processing queue.
     * This method takes a UDP packet, extracts its content, and enqueues it for
     * further processing in a thread-safe manner. It ensures that the original
     * packet's data and sender/recipient details are safely preserved.
     *
     * @param packet the UDP packet to be enqueued.
     */
    private void enqueueMessage(DatagramPacket packet) {
        try {
            // Convert the content of the DatagramPacket to a string and trim null terminator and spaces
            String messageContent = packet.content().toString(CharsetUtil.UTF_8).replaceAll("\0", "").trim();

            InetSocketAddress sender = packet.sender();
            InetSocketAddress recipient = packet.recipient();

            // Create a new DatagramPacket with the processed messageContent
            DatagramPacket safePacket = new DatagramPacket(
                    Unpooled.copiedBuffer(messageContent, CharsetUtil.UTF_8),
                    recipient, sender);

            messageQueue.offer(safePacket);
        } catch (Exception e) {
            LOGGER.error("Failed to enqueue UDP message: {}", e.getMessage());
        }
    }

    /**
     * Processes messages from the queue.
     * This method runs in a separate thread, continuously polling and processing
     * messages from the queue. Each message is passed to the appropriate handler
     * based on the UDP port it was received on.
     */
    private void processMessages() {
        while (running) {
            try {
                DatagramPacket packet = messageQueue.take();  // Blocks if queue is empty
                handleRequest(packet);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Handles incoming UDP requests and routes them based on the port.
     * This method checks the port of the incoming UDP packet and forwards the message
     * to either the `handleStarRequest` or `handleGalaxyRequest` methods.
     *
     * @param packet the UDP packet containing the message to be processed.
     */
    private void handleRequest(DatagramPacket packet) {
        String receivedMessage = packet.content().toString(CharsetUtil.UTF_8).trim();
        int receivedPort = packet.recipient().getPort();

        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Received UDP message on port {}: {}", receivedPort, receivedMessage);

        if (receivedPort == ApplicationState.getPort()) {
            handleStarRequest(receivedMessage, packet.sender());
        } else if (receivedPort == ApplicationState.getGalaxyPort()) {
            handleGalaxyRequest(receivedMessage, packet.sender());
        } else {
            LOGGER.warn("Unknown port {} for incoming UDP request", receivedPort);
        }
    }

    /**
     * Handles Star-level UDP requests (e.g., HELLO?).
     * This method processes Star-related requests, such as the "HELLO?" message,
     * and sends a corresponding response if needed. It can also handle SOL discovery
     * messages if the node is in the COMPONENT role.
     *
     * @param message the message received from the sender.
     * @param sender  the address of the sender.
     */
    private void handleStarRequest(String message, InetSocketAddress sender) {
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Handling Star request from {}: {}", sender, message);
        ApplicationState.getSolLock().lock();
        try {
            if ("HELLO?".equals(message)) {
                if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Ignoring HELLO? as component is not SOL.");
                    return;
                }
                if(sender.getAddress().getHostAddress().equals(ApplicationState.getIp().getHostAddress())){
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Ignoring own HELLO? request.");
                    return;
                }
                try {
                    String response = createStarHelloResponse();
                    sendResponse(response, sender, ApplicationState.getPort());
                } catch (Exception e) {
                    LOGGER.error("Error handling Star HELLO_RESPONSE: {}", e.getMessage());
                }
            } else if (message.startsWith("{") && message.contains("\"star\"")) {
                if (ApplicationState.getCurrentRole() != NodeRole.COMPONENT) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Ignoring SOL discovery as component is not in COMPONENT role.");
                    return;
                }
                // Prevent SOL discovery if already discovered
                if (isSolDiscovered()) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Ignoring SOL discovery as SOL is already discovered.");
                    return;
                }
                // Parse the message as JSON
                try {
                    HelloResponse parsedMessage = objectMapper.readValue(message, HelloResponse.class);
                    ApplicationState.setSolStarUuid(parsedMessage.star);
                    ApplicationState.setSolComUuid(parsedMessage.sol);
                    ApplicationState.setSolIp(InetAddress.getByName(parsedMessage.sol_ip));
                    ApplicationState.setSolPort(parsedMessage.sol_tcp);
                    setSolDiscovered(true);

                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "SOL discovered: {}", parsedMessage);
                } catch (JsonProcessingException | UnknownHostException e) {
                    LOGGER.error("Error parsing JSON message: {}", e.getMessage());
                }
            }
        } finally {
            ApplicationState.getSolLock().unlock();
        }
    }

    /**
     * Handles Galaxy-level UDP requests (e.g., HELLO? I AM <STAR-UUID>).
     * This method processes Galaxy-related requests, such as the "HELLO? I AM" message,
     * and checks if a response is necessary based on the current STAR's UUID.
     *
     * @param message the message received from the sender.
     * @param sender  the address of the sender.
     */
    private void handleGalaxyRequest(String message, InetSocketAddress sender) {
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "Handling Galaxy request from {}: {}", sender, message);

        if (message.startsWith("HELLO? I AM")) {
            if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
                LOGGER.log(Level.getLevel("STAR_DEBUG"), "Ignoring Galaxy HELLO as component is not STAR.");
                return;
            }
            // Extract the STAR-UUID from the message
            String starUuid = message.replace("HELLO? I AM", "").trim();

            if (starUuid.equals(ApplicationState.getStarUuid())) {
                LOGGER.log(Level.getLevel("STAR_DEBUG"), "Ignoring own Galaxy HELLO request.");
                return;
            }

            // Get the endpoint URL for the SOL
            String endpointUrl = "http://" + sender.getAddress().getHostAddress() + ":" + ApplicationState.getGalaxyPort() + "/vs/v1/star";

            // Build the SOL payload
            Sol sol = Sol.builder()
                    .solStarUuid(ApplicationState.getStarUuid())
                    .solUuid(ApplicationState.getComUuid())
                    .comIp(ApplicationState.getIp().getHostAddress())
                    .comPort(ApplicationState.getPort())
                    .noCom(ApplicationState.getMaxComponents())
                    .status("200")
                    .build();
            String payload = null;
            try {
                payload = HttpHandler.buildSolPayload(sol);
            } catch (Exception e) {
                LOGGER.error("Error building SOL payload: {}", e.getMessage());
                throw new RuntimeException(e);
            }
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Processing Galaxy HELLO for STAR-UUID: {}", starUuid);
            // Check if this STAR-UUID is already known
            if (solRepository.existsById(starUuid)) {
                // Check if the IP address matches
                if (solRepository.findById(starUuid).getComIp().equals(sender.getAddress().getHostAddress())) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Galaxy HELLO already present in the map, sending PATCH");
                    // Send a PATCH request to update the star information
                    try {
                        HttpHandler.sendPatchRequest(endpointUrl + "/" + ApplicationState.getStarUuid(), payload, "application/json");
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "IP address mismatch for STAR-UUID: {}", starUuid);
                }
                return;
            }

            // If the STAR-UUID is not known, register it via a POST request
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "New STAR-UUID detected: {}", starUuid);

            try {
                HttpResponse<String> response = HttpHandler.sendPostRequest(endpointUrl, payload, "application/json");

                if (response.statusCode() == 200) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"), "Galaxy HELLO response: {}", response.body());
                    ObjectMapper objectMapper = new ObjectMapper();
                    // Deserialize JSON response into Sol object
                    Sol solReceived = objectMapper.readValue(response.body(), Sol.class);
                    // Ensure the response is for the correct STAR-UUID
                    if(solReceived.getSolStarUuid().equals(starUuid)){
                        solRepository.save(starUuid, solReceived);
                    } else {
                        LOGGER.error("Response STAR-UUID does not match the request: {}", response.body());
                    }
                } else {
                    LOGGER.error("Error registering STAR-UUID: {}", response.body());
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error sending Galaxy HELLO response: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * Creates the JSON response for Star HELLO? requests.
     * This method constructs a JSON response for a "HELLO?" message, containing the
     * current STAR UUID, COM UUID, IP address, port, and an empty string as a component.
     *
     * @return the JSON response as a string.
     * @throws Exception if an error occurs during JSON serialization.
     */
    private String createStarHelloResponse() throws Exception {
        return objectMapper.writeValueAsString(new HelloResponse(
                ApplicationState.getStarUuid(),
                ApplicationState.getComUuid(),
                ApplicationState.getIp().getHostAddress(),
                ApplicationState.getPort(),
                "empty"
        ));
    }

    /**
     * Sends a JSON response back to the sender.
     * This method sends a response to the sender using UDP, encapsulating the response
     * message in a DatagramPacket and transmitting it via Netty's UDP handler.
     *
     * @param response the JSON response to be sent.
     * @param sender   the address of the sender.
     * @param port     the port to which the response should be sent.
     * @throws InterruptedException if an error occurs during the response sending process.
     */
    private void sendResponse(String response, InetSocketAddress sender, int port) throws InterruptedException {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInboundHandlerAdapter());

            Channel channel = bootstrap.bind(0).sync().channel();
            channel.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer(response, CharsetUtil.UTF_8),
                    new InetSocketAddress(sender.getAddress(), port)
            )).sync();
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "Response sent to {}: {}", sender, response);
        } catch (InterruptedException e) {
            LOGGER.error("Error sending response: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends a UDP broadcast message.
     * This method broadcasts a message to all devices on the network using the
     * UDP protocol. The message is sent to the specified port on the broadcast address.
     *
     * @param message the message to be broadcast.
     * @param port    the port on which the message should be broadcast.
     * @throws Exception if the message length exceeds the limit or if broadcasting fails.
     */
    public static void sendBroadcast(String message, int port) throws Exception {
        if (message.length() > 1024) {
            throw new IllegalArgumentException("Message length exceeds the 1024 character limit.");
        }

        message += "\0"; // Null-terminate the message

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInboundHandlerAdapter());

        Channel channel = bootstrap.bind(0).sync().channel();
        channel.writeAndFlush(new DatagramPacket(
                Unpooled.copiedBuffer(message, CharsetUtil.UTF_8),
                new InetSocketAddress("255.255.255.255", port)
        )).sync();
        LOGGER.log(Level.getLevel("STAR_INFO"), "Broadcast message sent: {}", message);
    }

    public static boolean isSolDiscovered() {
        return solDiscovered.get();
    }

    public static void setSolDiscovered(boolean discovered) {
        solDiscovered.set(discovered);
    }

    /**
     * Inner class to represent a HELLO response.
     * This class is used to deserialize the JSON response sent in response to
     * a "HELLO?" message.
     */
    public static class HelloResponse {
        public String star;
        public String sol;
        public String sol_ip;
        public int sol_tcp;
        public String component;

        @JsonCreator
        public HelloResponse(
                @JsonProperty("star") String star,
                @JsonProperty("sol") String sol,
                @JsonProperty("sol-ip") String sol_ip,
                @JsonProperty("sol-tcp") int sol_tcp,
                @JsonProperty("component") String component) {
            this.star = star;
            this.sol = sol;
            this.sol_ip = sol_ip;
            this.sol_tcp = sol_tcp;
            this.component = component;
        }
    }
}
