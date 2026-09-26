package com.htv.smartfarm.inventory.api;

import com.htv.smartfarm.inventory.domain.InventoryRepository;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * Development diagnostics only. Protect at network layer; remove before production.
 */
@RestController
@Profile("dev & !prod")
public class InboxDevController {
    private final InventoryRepository repo;

    public InboxDevController(InventoryRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/internal/dev/inbox/count")
    public Map<String, Long> count() {
        return Map.of("count", repo.inboxCount());
    }
}
