package com.vs.starnet.star.controller;

import com.vs.starnet.star.model.Component;
import com.vs.starnet.star.service.StarService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 */

/**
 * Controller for handling component registration and interactions with SOL.
 */
@RestController
@RequestMapping("/vs/v1/system")
public class StarController {
    static final Logger LOGGER = LogManager.getRootLogger();

    StarService starService;

    public StarController(StarService starService) {
        this.starService = starService;
    }

    /**
     * Handle POST request for component registration.
     *
     * @param component the registration data sent by the component
     * @return the appropriate HTTP status as a text response
     */
    @PostMapping
    public ResponseEntity<String> registerComponent(@RequestBody @Valid Component component) {
        return starService.registerComponent(component);
    }

    /**
     * Handle DELETE request for component deregistration.
     *
     * @param comUuid the UUID of the component to deregister
     * @return the appropriate HTTP status as a text response
     */
    @DeleteMapping({"/{comUuid}", "/"})
    public ResponseEntity<String> deregisterComponent(@PathVariable(required = false) String comUuid, @RequestParam String star, HttpServletRequest request) {
        return starService.deregisterComponent(comUuid, star, request);
    }

    /**
     * Handle PATCH request for updating a component's status.
     *
     * @param comUuid   the UUID of the component to update
     * @param component the updated component data
     * @return the appropriate HTTP status as a text response
     */
    @PatchMapping("/{comUuid}")
    public ResponseEntity<String> updateComponent(@PathVariable String comUuid, @RequestBody Component component) {
        return starService.updateComponent(comUuid, component);
    }

    /**
     * Handle GET request for checking component status.
     *
     * @param comUuid the UUID of the component to check
     * @param star    the STAR-UUID of the star system
     * @return the component status or an error response
     */
    @GetMapping({"/{comUuid}", "/"})
    public ResponseEntity<Map<String, String>> getComponentStatus(@PathVariable(required = false) String comUuid, @RequestParam String star) {
        return starService.getComponentStatus(comUuid, star);
    }
}
