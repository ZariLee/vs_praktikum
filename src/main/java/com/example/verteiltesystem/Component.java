package com.example.verteiltesystem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@org.springframework.stereotype.Component
public class Component {
    private final ServerProperties serverProperties;
    private String comUUID;
    private int comUUIDint;
    private InetAddress ipAddress;
    private int port;
    private boolean isSol;
    private Map<String, Component> components;
    private int maxComponents;
    private LocalDateTime initializedTime;
    private DatagramSocket udpSocket;
    private Map<String, String> solInfo; // all relevant information to address Sol
    private int solTimer = 5000;

    public Component(Environment environment, @Value("${component.maxComponents}") int maxComponents, ServerProperties serverProperties) throws IOException {
        generateComUuid();
        readIP();
        this.port = Integer.parseInt(environment.getProperty("server.port", "8080"));;
        this.isSol = false;
        this.components = new HashMap<>();
        this.maxComponents = maxComponents;
        this.solInfo = new HashMap<>();
        this.initializedTime = LocalDateTime.now();
        this.udpSocket = new DatagramSocket();
        enterSystem();
        this.serverProperties = serverProperties;
    }

    // Generates 4-digit unique identifier for each component
    private void generateComUuid() {
        comUUIDint = 1000 + (int) (Math.random() * 9000);
        comUUID = Integer.toString(comUUIDint);
    }

    private void readIP() throws UnknownHostException {
        ipAddress = InetAddress.getLocalHost();
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                receiveMessage();
            } catch (IOException e) {
                System.err.println("Error while receiving message: " + e.getMessage());
            }
        });
        listenerThread.start();
    }

    public void shutdown() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            System.out.println("UDP Socket closed.");
        }
    }

    private void enterSystem() {
        try {
            if (udpSocket == null || udpSocket.isClosed()) {
                udpSocket = new DatagramSocket(port);
                System.out.println("Socket bound to port: " + port);
            }

            sendMessage("HELLO?");
            System.out.println("Broadcasted HELLO message to join the system.");
            startListening();

            // Timer for Sol to answer
            boolean solFound = waitForSolResponse(this.solTimer);

            if (!solFound) {
                // If Sol not found
                becomeSol();
            }

        } catch (SocketException e) {
            System.err.println("Failed to bind socket to port: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error during communication: " + e.getMessage());
        }
    }

    // Broadcast "HELLO?"
    protected void sendMessage(String message) throws IOException {
        sendMessage(message, InetAddress.getByName("255.255.255.255")); // Global Broadcast Address
    }

    // Method to address specific component
    private void sendMessage(String message, InetAddress address) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        udpSocket.send(packet);
        System.out.println("Sent: " + message);
    }

    // Waits for a SOL response for a given timeout duration
    private boolean waitForSolResponse(int timeout) throws IOException {
        udpSocket.setSoTimeout(timeout);

        try {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);

            String response = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received response: " + response);

            if (response.startsWith("SOL:")) {
                // Parse SOL information
                String[] parts = response.split(";");
                solInfo.put("UUID", parts[1]);
                solInfo.put("IP", parts[2]);
                solInfo.put("PORT", parts[3]);
                return true;
            }
        } catch (SocketTimeoutException e) {
            System.out.println("No SOL response received within the timeout period.");
        }

        return false;
    }

    // This component becomes Sol
    private void becomeSol() {
        isSol = true;
        solInfo.put("UUID", comUUID);
        solInfo.put("IP", ipAddress.getHostAddress());
        solInfo.put("PORT", Integer.toString(port));
        System.out.println("This component is now the SOL.");
    }

    private void receiveMessage() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                udpSocket.receive(packet);
                String receivedMessage = new String(packet.getData(), 0, packet.getLength());
                InetAddress senderAddress = packet.getAddress();

                // Skip processing if the message is from this component
                if (senderAddress.equals(ipAddress)) {
                    System.out.println("Ignored message from self: " + receivedMessage);
                    continue;
                }

                System.out.println("Received: " + receivedMessage);

                if (isSol && receivedMessage.equals("HELLO?")) {
                    sendSolResponse(senderAddress, packet.getPort());
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Receive timed out while waiting for messages.");
            }
        }
    }


    // Respond to HELLO? if this component is SOL
    private void sendSolResponse(InetAddress address, int port) throws IOException {
        String response = "SOL;" + comUUID + ";" + ipAddress.getHostAddress() + ";" + this.port;
        byte[] data = response.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        udpSocket.send(packet);
        System.out.println("Responded as SOL to: " + address + ":" + port);
    }

    // GETTERS
    public String getComUUID() {
        return comUUID;
    }

    public int getComUUIDint() {
        return comUUIDint;
    }

    public InetAddress getIpAdresse() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public boolean isSol() {
        return isSol;
    }
}
