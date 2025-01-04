package com.vs.starnet.star.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 */
@Builder
@Getter
@Setter
@ToString
public class Component {

    @JsonProperty("star")
    @NotNull(message = "Star UUID is required.")
    private String solStarUuid;   // Maps to "star"

    @JsonProperty("sol")
    @NotNull(message = "SOL UUID is required.")
    private String solComUuid;    // Maps to "sol"

    @JsonProperty("component")
    @NotNull(message = "Component UUID is required.")
    private String comUuid;  // Maps to "component"

    @JsonProperty("com-ip")
    @NotNull(message = "Component IP is required.")
    private String comIp;    // Maps to "com-ip"

    @JsonProperty("com-tcp")
    @NotNull(message = "Component port is required.")
    private int comPort;     // Maps to "com-tcp"

    @JsonProperty("status")
    @NotNull(message = "Component status is required.")
    private String status;   // Maps to "status"

    @JsonIgnore
    private AtomicReference<Instant> lastInteractionTime;

    @JsonIgnore
    private Instant integrationTime;
}

