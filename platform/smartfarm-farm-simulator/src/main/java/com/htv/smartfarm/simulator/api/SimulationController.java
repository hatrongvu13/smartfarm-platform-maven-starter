package com.htv.smartfarm.simulator.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    @PostMapping("/{type}")
    ResponseEntity<Map<String, Object>> emit(@PathVariable String type) {
        // Adapter boundary: replace body with MqttEventPublisher in the next increment.
        return ResponseEntity.accepted().body(Map.of("topic", "smartfarm/devices/simulator/status", "type", type, "at", Instant.now().toString()));
    }
}
