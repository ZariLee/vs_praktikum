package com.example.verteiltesystem;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@org.springframework.stereotype.Component
public class Component {
    private String comUUID;
    private int comUUIDint;
    private InetAddress ipAddress; // ip of the participant
    private int port;
    private boolean isSol;
    private Map<String, Component> components;
    private int maxComponents;
    private LocalDateTime initializedTime;
    private DatagramSocket udpSocket;
    private Map sol;

    // Consrtuctor

    public Component(@Value("${component.port}") int port, @Value("${component.maxComponents}") int maxComponents) throws UnknownHostException, SocketException {
        generateComUuid();
        readIP();
        this.port = port;
        this.isSol = false;
        this.components = new HashMap<>();
        this.maxComponents = maxComponents;
        initializedTime = LocalDateTime.now();
        this.udpSocket = new DatagramSocket();
        this.sol = new HashMap();
        enterSystem();
    }

    // generates 4 digit unique identifier for each component
    private void generateComUuid() {
        comUUIDint = 1000 + (int) Math.random() * 9000;
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

    private void enterSystem() {
        try {
            if (udpSocket == null || udpSocket.isClosed()) {
                udpSocket = new DatagramSocket(port);
                System.out.println("Socket bound to port: " + port);
            }
            // Broadcast "Hello?" to check the presence
            sendMessage("HELLO?");
            System.out.println("Broadcasted HELLO message to join the system.");
            startListening();
        } catch (SocketException e) {
            System.err.println("Failed to bind socket to port: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error during communication: " + e.getMessage());
        }

    }

    // method to send "HELLO?" to all components
    protected void sendMessage(String message) throws IOException {
        sendMessage(message, InetAddress.getByName("255.255.255.255")); // Global Broadcast Address
    }

    // Method to send msg to other component
    private void sendMessage(String message, InetAddress address) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        udpSocket.send(packet);
        System.out.println("Sent: " + message);
    }

    private void receiveMessage() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            udpSocket.receive(packet);
            String readMessage = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received: " + readMessage);
        }


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
