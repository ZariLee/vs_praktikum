package com.example.verteiltesystem;

public class MessagePayload {
    private String targetComUUID;
    private String message;

    public MessagePayload() {
    }

    public MessagePayload(String targetComUUID, String message) {
        this.targetComUUID = targetComUUID;
        this.message = message;
    }

    public String getTargetComUUID() {
        return targetComUUID;
    }

    public void setTargetComUUID(String targetComUUID) {
        this.targetComUUID = targetComUUID;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
