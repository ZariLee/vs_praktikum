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
 */
@RestController
@RequestMapping("/vs/v1/star")
public class GalaxyController {
    static final Logger LOGGER = LogManager.getRootLogger();

    GalaxyService galaxyService;

    public GalaxyController(GalaxyService galaxyService) {
        this.galaxyService = galaxyService;
    }

    @PostMapping
    public ResponseEntity<Sol> registerStar(@RequestBody @Valid Sol sol) {
        return galaxyService.registerStar(sol);
    }

    @PatchMapping("/{starUuid}")
    public ResponseEntity<Sol> updateStar(@PathVariable String starUuid, @RequestBody Sol sol) {
        return galaxyService.updateStar(starUuid, sol);
    }

    @GetMapping("/{starUuid}")
    public ResponseEntity<Sol> getStarDetail(@PathVariable("starUuid") String starUuid) {
        return galaxyService.getStarDetail(starUuid);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStarDetails(){
        return galaxyService.getStarDetails();
    }

    @DeleteMapping("/{starUuid}")
    public ResponseEntity<String> deregisterStar(@PathVariable("starUuid") String starUuid, HttpServletRequest request) {
        return galaxyService.deregisterStar(starUuid, request);
    }
}
