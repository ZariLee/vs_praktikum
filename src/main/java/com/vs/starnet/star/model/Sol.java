package com.vs.starnet.star.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

/**
 * @author itakurah (Niklas Hoefflin)
 * GitHub: <a href="https://github.com/itakurah">itakurah</a>
 * LinkedIn: <a href="https://www.linkedin.com/in/niklashoefflin">Niklas Hoefflin</a>
 */
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Sol {
    @JsonProperty("star")
    @NotNull(message = "Star UUID is required.")
    private String solStarUuid;

    @JsonProperty("sol")
    @NotNull(message = "SOL UUID is required.")
    private String solUuid;

    @JsonProperty("sol-ip")
    @NotNull(message = "Component IP is required.")
    private String comIp;

    @JsonProperty("sol-tcp")
    @NotNull(message = "Component port is required.")
    private int comPort;

    @JsonProperty("no-com")
    @NotNull(message = "Component count is required.")
    private int noCom;     // Maps to "com-tcp"

    @JsonProperty("status")
    @NotNull(message = "Component status is required.")
    private String status;

    @JsonIgnore
    private Instant deregistrationTime;
}
