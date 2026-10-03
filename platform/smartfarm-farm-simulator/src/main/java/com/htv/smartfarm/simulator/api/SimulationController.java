package com.htv.smartfarm.simulator.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * DEV-ONLY simulator stub (quarantined — cleanup SKEL-01). This endpoint is a scaffold: it
 * returns a hard-coded 202 and publishes NOTHING. It is explicitly profile-gated to
 * {@code dev & !prod} so it can never be served in production. To make it a real device
 * simulator, wire a messaging publisher here and drop {@code web-application-type: none}
 * in application.yml — that is net-new feature work, not cleanup.
 */
@Profile("dev & !prod")
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    @PostMapping("/{type}")
    ResponseEntity<Map<String, Object>> emit(@PathVariable String type) {
        // Adapter boundary: replace body with a real MQTT publisher in a future increment.
        return ResponseEntity.accepted().body(Map.of("topic", "smartfarm/devices/simulator/status", "type", type, "at", Instant.now().toString()));
    }
}
