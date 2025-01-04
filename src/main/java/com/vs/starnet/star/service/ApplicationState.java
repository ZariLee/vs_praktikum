package com.vs.starnet.star.service;

import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Component;
import lombok.Getter;
import lombok.Setter;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 */
public class ApplicationState {

    private static final AtomicReference<NodeRole> currentRole = new AtomicReference<>(NodeRole.COMPONENT);
    private static final AtomicReference<String> groupId = new AtomicReference<>();
    private static final AtomicReference<InetAddress> ip = new AtomicReference<>();
    private static final AtomicInteger port = new AtomicInteger(0);
    private static final AtomicReference<String> starUuid = new AtomicReference<>();
    private static final AtomicReference<String> comUuid = new AtomicReference<>();
    private static final AtomicInteger maxComponents = new AtomicInteger(0);
    private static final AtomicReference<String> solStarUuid = new AtomicReference<>();
    private static final AtomicReference<String> solComUuid = new AtomicReference<>();
    private static final AtomicReference<InetAddress> solIp = new AtomicReference<>();
    private static final AtomicInteger solPort = new AtomicInteger(0);
    private static AtomicBoolean isReady = new AtomicBoolean(false);
    @Setter
    @Getter
    private static ReentrantLock solLock = new ReentrantLock();
    @Setter
    @Getter
    private static int galaxyPort;

    public static NodeRole getCurrentRole() {
        return currentRole.get();
    }

    public static void setCurrentRole(NodeRole role) {
        currentRole.set(role);
    }

    public static String getGroupId() {
        return groupId.get();
    }

    public static void setGroupId(String id) {
        groupId.set(id);
    }

    public static InetAddress getIp() {
        return ip.get();
    }

    public static void setIp(InetAddress address) {
        ip.set(address);
    }

    public static int getPort() {
        return port.get();
    }

    public static void setPort(int portValue) {
        port.set(portValue);
    }

    public static String getStarUuid() {
        return starUuid.get();
    }

    public static void setStarUuid(String uuid) {
        starUuid.set(uuid);
    }

    public static String getComUuid() {
        return comUuid.get();
    }

    public static void setComUuid(String uuid) {
        comUuid.set(uuid);
    }

    public static int getMaxComponents() {
        return maxComponents.get();
    }

    public static void setMaxComponents(int max) {
        maxComponents.set(max);
    }

    public static String getSolStarUuid() {
        return solStarUuid.get();
    }

    public static void setSolStarUuid(String uuid) {
        solStarUuid.set(uuid);
    }

    public static String getSolComUuid() {
        return solComUuid.get();
    }

    public static void setSolComUuid(String uuid) {
        solComUuid.set(uuid);
    }

    public static InetAddress getSolIp() {
        return solIp.get();
    }

    public static void setSolIp(InetAddress address) {
        solIp.set(address);
    }

    public static int getSolPort() {
        return solPort.get();
    }

    public static void setSolPort(int portValue) {
        solPort.set(portValue);
    }

    public static boolean getIsReady() {
        return isReady.get();
    }

    public static void setIsReady(boolean readyValue) {
        isReady.set(readyValue);
    }

    public static synchronized void reset() {
        currentRole.set(NodeRole.COMPONENT);
        groupId.set(null);
        ip.set(null);
        port.set(0);
        starUuid.set(null);
        comUuid.set(null);
        maxComponents.set(0);
        solStarUuid.set(null);
        solComUuid.set(null);
        solIp.set(null);
        solPort.set(0);
    }
}