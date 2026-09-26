package com.htv.smartfarm.readiness.api;

import com.htv.smartfarm.readiness.probe.ReadinessMonitor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessController {
    private final ReadinessMonitor monitor;

    public ReadinessController(ReadinessMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/api/v1/platform/readiness")
    public ReadinessMonitor.Snapshot readiness() {
        return monitor.snapshot();
    }
}
