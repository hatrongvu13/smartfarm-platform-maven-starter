package com.htv.smartfarm.readiness.probe;

import com.htv.smartfarm.proto.readiness.v1.ComponentStatus;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cache a bounded snapshot; never probe arbitrary URLs from a caller request.
 */
@Service
public class ReadinessMonitor {
    public record Check(String serviceName, ComponentStatus status, String detail, Instant checkedAt) {
    }

    public record Snapshot(ComponentStatus overall, List<Check> components, Instant checkedAt) {
    }

    private final HttpClient http;
    private final ProbeSettings settings;
    private final AtomicReference<Snapshot> last = new AtomicReference<>(new Snapshot(ComponentStatus.COMPONENT_STATUS_NOT_READY, List.of(), Instant.EPOCH));

    public ReadinessMonitor(HttpClient http, ProbeSettings settings) {
        this.http = http;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${smartfarm.readiness.poll-ms:5000}", initialDelayString = "${smartfarm.readiness.initial-delay-ms:1000}")
    public void refresh() {
        List<Check> checks = new ArrayList<>();
        for (ProbeSettings.Target target : settings.targets()) {
            ComponentStatus status = ComponentStatus.COMPONENT_STATUS_NOT_READY;
            String detail = "unavailable";
            try {
                var req = HttpRequest.newBuilder(URI.create(target.url())).timeout(settings.timeout()).GET().build();
                var response = http.send(req, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    status = ComponentStatus.COMPONENT_STATUS_READY;
                    detail = "http_200";
                } else detail = "http_" + response.statusCode();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                detail = "interrupted";
            } catch (Exception unavailable) {
                detail = "unavailable";
            }
            checks.add(new Check(target.name(), status, detail, Instant.now()));
        }
        long ready = checks.stream().filter(c -> c.status() == ComponentStatus.COMPONENT_STATUS_READY).count();
        ComponentStatus overall = ready == checks.size() ? ComponentStatus.COMPONENT_STATUS_READY :
                ready == 0 ? ComponentStatus.COMPONENT_STATUS_NOT_READY : ComponentStatus.COMPONENT_STATUS_DEGRADED;
        last.set(new Snapshot(overall, List.copyOf(checks), Instant.now()));
    }

    public Snapshot snapshot() {
        Snapshot value = last.get();
        if (value.checkedAt().plus(settings.maxAge()).isBefore(Instant.now()))
            return new Snapshot(ComponentStatus.COMPONENT_STATUS_NOT_READY, value.components(), value.checkedAt());
        return value;
    }
}
