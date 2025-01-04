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
 * represents the component in the system.
 * handles data for both internal logic and json payload
 */
// Lombok annotations (to build common methods during compilation)
@Builder
@Getter
@Setter
@ToString
public class Component {

    // fields that are part of the Json payload
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

    // ignored in json
    @JsonIgnore
    private AtomicReference<Instant> lastInteractionTime;

    @JsonIgnore
    private Instant integrationTime;
}

