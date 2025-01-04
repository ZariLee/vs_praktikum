package com.vs.starnet.star.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 */
@Getter
@Setter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL) // Include only non-null fields in JSON serialization
public class Message {

    @JsonProperty("star")
    @NotNull(message = "Star UUID is required.")
    private String star;          // STAR-UUID

    @JsonProperty("origin")
    private String origin;        // COM-UUID or EMAIL

    @JsonProperty("sender")
    @NotNull(message = "Sender is required.")
    private String sender;        // COM-UUID

    @JsonProperty("msg-id")
    private String msgId;         // MSG-UUID

    @JsonProperty("version")
    private String version;       // Message version

    @JsonProperty("created")
    private long created;         // Creation timestamp

    @JsonProperty("changed")
    private long changed;         // Last modification timestamp

    @JsonProperty("subject")
    private String subject;       // Subject of the message

    @JsonProperty("message")
    private String message;       // Body of the message

    @JsonProperty("msg-type")
    private String status;        // Message status ("active")

    // Optional fields for SOL processing
    @JsonProperty("from-star")
    private String fromStar;      // Originating STAR-UUID (optional)

    @JsonProperty("received")
    private Long received;        // Timestamp when first received by SOL (optional)

    @JsonProperty("to-star")
    private String toStar;        // Target STAR-UUID for forwarding (optional)

    @JsonProperty("delivered")
    private Long delivered;       // Timestamp when delivered to another SOL (optional)
}
