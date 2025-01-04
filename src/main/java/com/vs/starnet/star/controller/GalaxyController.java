package com.vs.starnet.star.controller;

import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.service.GalaxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * This controller serves as an interface between HTTP requests and the GalaxyService business logic
 * for CRUD operations on stars.
 */
@RestController
@RequestMapping("/vs/v1/star")
public class GalaxyController {
    static final Logger LOGGER = LogManager.getRootLogger();

    // deendency injection -> service/GalaxyService
    GalaxyService galaxyService;

    public GalaxyController(GalaxyService galaxyService) {
        this.galaxyService = galaxyService;
    }

    /**
     * Registers new star
     * @param sol object
     * @return Response Entity sol containing registered sol
     */
    @PostMapping
    public ResponseEntity<Sol> registerStar(@RequestBody @Valid Sol sol) {
        return galaxyService.registerStar(sol);
    }

    /**
     * updates existing star
     * @param starUuid
     * @param sol via UUID
     * @return updated star
     */
    @PatchMapping("/{starUuid}")
    public ResponseEntity<Sol> updateStar(@PathVariable String starUuid, @RequestBody Sol sol) {
        return galaxyService.updateStar(starUuid, sol);
    }

    /**
     * Fetches details of specified sol
     * @param starUuid star uuid for identification
     * @return details of specified star
     */
    @GetMapping("/{starUuid}")
    public ResponseEntity<Sol> getStarDetail(@PathVariable("starUuid") String starUuid) {
        return galaxyService.getStarDetail(starUuid);
    }

    /**
     * Gets map of registered star
     * @return map containing details of star
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStarDetails(){
        return galaxyService.getStarDetails();
    }

    /**
     * delettes registered star
     * @param starUuid identifier UUID
     * @param request HttpServletRequest (interface to represent an incoming HTTP request)
     * @return >Response Entity for confirmation
     */
    @DeleteMapping("/{starUuid}")
    public ResponseEntity<String> deregisterStar(@PathVariable("starUuid") String starUuid, HttpServletRequest request) {
        return galaxyService.deregisterStar(starUuid, request);
    }
}
