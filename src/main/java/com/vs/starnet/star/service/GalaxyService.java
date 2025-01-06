package com.vs.starnet.star.service;

import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.network.HttpHandler;
import com.vs.starnet.star.network.UdpHandler;
import com.vs.starnet.star.repository.SolRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

/**
 * manages the functionality of a system with a sol
 * performs tasks such as registration, updates,
 * deregistration, and discovery of stars using both HTTP and UDP protocols.
 */
@Service
public class GalaxyService {
    static final Logger LOGGER = LogManager.getRootLogger();
    private final SolRepository solRepository;

    public GalaxyService(SolRepository solRepository) {
        this.solRepository = solRepository;
    }

    /**
     * broadcasts a hello with identifier
     */
    public static void discoverGalaxy() {
        // Send broadcast to discover the galaxy
        try {
            UdpHandler.sendBroadcast(String.format("HELLO? I AM %s", ApplicationState.getStarUuid()), ApplicationState.getGalaxyPort());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * ensures only sol can register a star
     * @param sol the current sol
     * @return either conflict content or new star
     */
    public ResponseEntity<Sol> registerStar(Sol sol) {
        if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.error("Only SOL can register a star.");
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (solRepository.existsById(sol.getSolStarUuid())) {
            LOGGER.error("Star already exists, use PATCH to update the information.");
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        // Save the star information
        solRepository.save(sol.getSolStarUuid(), sol);
       LOGGER.log(Level.getLevel("STAR_INFO"),"Star registered: {}", sol);

        // Return the response entity with the star information
        return ResponseEntity.ok(Sol.builder().solStarUuid(ApplicationState.getStarUuid())
                .solUuid(ApplicationState.getComUuid()).comIp(ApplicationState.getIp()
                        .getHostAddress()).comPort(ApplicationState.getPort())
                .noCom(ApplicationState.getMaxComponents()).status("200").build());
    }

    /**
     * ensures update is done bei sol. updates a star
     * @param starUuid component identifier
     * @param sol sol for validation
     * @return either conflict content or updated star entity
     */
    public ResponseEntity<Sol> updateStar(String starUuid, Sol sol) {
        if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.error("Only SOL can update a star.");
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if(starUuid == null || starUuid.isEmpty()){
            LOGGER.error("Star UUID is required.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (!solRepository.existsById(sol.getSolStarUuid())) {
            LOGGER.error("Star does not exist, use POST to register the star.");
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        // Update the star information
        solRepository.save(sol.getSolStarUuid(), sol);
       LOGGER.log(Level.getLevel("STAR_INFO"),"Star updated: {}", sol);

        // Return the response entity with the star information
        return ResponseEntity.ok(Sol.builder().solStarUuid(ApplicationState.getStarUuid())
                .solUuid(ApplicationState.getComUuid()).comIp(ApplicationState.getIp()
                        .getHostAddress()).comPort(ApplicationState.getPort())
                .noCom(ApplicationState.getMaxComponents()).status("200").build());
    }

    /**
     * gets star details if done by sol
     * @param starUuid component identifier
     * @return either conflict content or component details
     */
    public ResponseEntity<Sol> getStarDetail(String starUuid) {
        if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.error("Only SOL can get star details.");
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if(starUuid == null || starUuid.isEmpty()){
            LOGGER.error("Star UUID is required.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (!solRepository.existsById(starUuid)) {
            LOGGER.error("Star does not exist.");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        // Get the star information
        Sol sol = solRepository.findById(starUuid);
        LOGGER.log(Level.getLevel("STAR_DEBUG"),"Star details: {}", sol);

        return ResponseEntity.ok(sol);
    }

    /**
     * gets star details of all stars if done by sol
     * @return either conflict content or component details in map of all components
     */
    public ResponseEntity<Map<String, Object>> getStarDetails() {
        if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.error("Only SOL can get star details.");
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
        // Get all star information
        Map<String, Sol> allStarsMap = solRepository.findAll();


        // Create the response map
        Map<String, Object> responseMap = new LinkedHashMap<>();
        List<Map<String, Object>> starsList = new ArrayList<>();

        for (Sol sol : allStarsMap.values()) {
            Map<String, Object> starDetails = new LinkedHashMap<>();
            starDetails.put("star", sol.getSolStarUuid()); // star UUID
            starDetails.put("sol", sol.getSolUuid()); // sol UUID
            starDetails.put("sol-ip", sol.getComIp()); // sol-ip
            starDetails.put("sol-tcp", sol.getComPort()); // sol-tcp
            starDetails.put("no-com", sol.getNoCom()); // number of components
            starDetails.put("status", sol.getStatus()); // component status

            starsList.add(starDetails);
        }

        responseMap.put("totalResults", starsList.size());
        responseMap.put("stars", starsList);

        LOGGER.log(Level.getLevel("STAR_DEBUG"),"Star details: {}", responseMap);

        return ResponseEntity.ok(responseMap);
    }

    /**
     * deregisters a component
     * @param starUuid component identifier
     * @param request HTTP request
     * @return validation that component was deregistered or conflict content
     */
    public ResponseEntity<String> deregisterStar(String starUuid, HttpServletRequest request) {
        String senderIp = request.getRemoteAddr();
        if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.error("Only SOL can deregister a star.");
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if(starUuid == null || starUuid.isEmpty()){
            LOGGER.error("Star UUID is required.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (!solRepository.existsById(starUuid)) {
            LOGGER.error("Star does not exist.");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (!senderIp.equals(solRepository.findById(starUuid).getComIp())) {
            LOGGER.error("Unauthorized request to deregister the star.");
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        // Set the status of the star to "left"
        solRepository.findById(starUuid).setStatus("left");

        // Set the deregistration time of the star
        solRepository.findById(starUuid).setDeregistrationTime(Instant.now());

        // Delete the star information and move to inactive map
        solRepository.delete(starUuid);
        LOGGER.log(Level.getLevel("STAR_INFO"),"Star deregistered: {}", starUuid);

        // Return the response entity with the star information
        return ResponseEntity.ok("Star deregistered: " + starUuid);
    }

    /**
     * reverses deregistration of a component
     */
    public void unregisterLocalStar(){
        if (ApplicationState.getCurrentRole() != NodeRole.SOL) {
            LOGGER.error("Only SOL can deregister a star.");
            return;
        }
        // Set the status of the star to "left"
        solRepository.findById(ApplicationState.getStarUuid()).setStatus("left");
        // Set the deregistration time of the star
        solRepository.findById(ApplicationState.getStarUuid()).setDeregistrationTime(Instant.now());

        // Delete the star information and move to inactive map
        solRepository.delete(ApplicationState.getStarUuid());
        LOGGER.log(Level.getLevel("STAR_INFO"),"Star deregistered: {}", ApplicationState.getStarUuid());

        // Attempt to notify other stars
        notifyOtherStars(ApplicationState.getStarUuid());
    }

    /**
     * notifies stars based on uuid key
     * @param starUuid identifier
     */
    private void notifyOtherStars(String starUuid) {
        solRepository.findAll().forEach((starUuidKey, star) -> {
                try {
                    attemptDeregistration(star, starUuid);
                } catch (Exception e) {
                    LOGGER.error("Failed to notify star: " + star.getSolStarUuid());
                }
        });
    }

    /**
     * helper method for notifying other stars with a retry logic, before giving up on notification
     * @param targetStar identifier
     * @param deregisteringStarUuid identifier
     * @throws InterruptedException
     */
    private void attemptDeregistration(Sol targetStar, String deregisteringStarUuid) throws InterruptedException {
        int retries = 0;
        boolean success = false;

        while (retries < 2 && !success) {
            try {
                // Construct the target URL
                String url = "http://" + targetStar.getComIp() + ":" + ApplicationState.getGalaxyPort() + "/vs/v1/star/" + deregisteringStarUuid;

                // Send HTTP DELETE request
                HttpResponse<String> response = HttpHandler.sendDeleteRequest(url, null, "text/plain");

                if (response.statusCode() == 200) {
                    LOGGER.log(Level.getLevel("STAR_DEBUG"),"Successfully notified star: " + targetStar.getSolStarUuid());
                    success = true;
                } else if (response.statusCode() == 404) {
                    LOGGER.warn("Star not found: " + targetStar.getSolStarUuid());
                    success = true;
                } else if (response.statusCode() == 401) {
                    LOGGER.warn("Unauthorized to notify star: " + targetStar.getSolStarUuid());
                    success = true;
                }
            } catch (Exception e) {
                retries++;
                LOGGER.error("Failed to notify star: " + targetStar.getSolStarUuid() + ", retrying in 10 seconds");
                Thread.sleep(10000); // Retry after 10 seconds
            }
        }

        if (!success) {
            LOGGER.error("Giving up on notifying star: " + targetStar.getSolStarUuid());
        }
        System.exit(1);
    }
}
