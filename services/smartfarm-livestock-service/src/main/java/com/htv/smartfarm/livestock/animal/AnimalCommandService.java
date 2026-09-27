package com.htv.smartfarm.livestock.animal;

import com.htv.smartfarm.proto.livestock.v1.Animal;
import com.htv.smartfarm.proto.livestock.v1.AnimalStatus;
import com.htv.smartfarm.proto.livestock.v1.RegisterAnimalRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write/read model for the animal registry. Registration is idempotent on the ear-tag:
 * re-registering the same {@code (tenant, farm, tag_code)} returns the existing animal
 * instead of creating a duplicate. Tenant is the verified caller tenant, never the body.
 */
@Service
public class AnimalCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.animal");

    private final AnimalJpaRepository animals;

    public AnimalCommandService(AnimalJpaRepository animals) {
        this.animals = animals;
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    @Transactional
    public AnimalEntity register(String tenant, String actor, RegisterAnimalRequest req) {
        if (tenant == null || tenant.isBlank()) throw new SecurityException("authenticated tenant required");
        if (!req.hasAnimal()) throw new IllegalArgumentException("animal required");
        var a = req.getAnimal();
        if (a.getFarmId().isBlank()) throw new IllegalArgumentException("farm_id required");
        if (a.getTagCode().isBlank()) throw new IllegalArgumentException("tag_code required");
        if (!req.getContext().getTenantId().isBlank() && !tenant.equals(req.getContext().getTenantId()))
            throw new SecurityException("tenant mismatch");

        var existing = animals.findByTenantIdAndFarmIdAndTagCode(tenant, a.getFarmId(), a.getTagCode()).orElse(null);
        if (existing != null) {
            AUDIT.info("animal_register_idempotent_hit tenant={} actor_id={} animal_id={} tag={}",
                    tenant, safe(req.getContext().getActorId()), existing.getId(), existing.getTagCode());
            return existing;
        }
        String status = a.getStatus() == AnimalStatus.ANIMAL_STATUS_UNSPECIFIED
                ? "ANIMAL_STATUS_ACTIVE" : a.getStatus().name();
        Long birth = a.hasBirthDate() ? a.getBirthDate().getSeconds() * 1000 : null;
        var e = new AnimalEntity(UUID.randomUUID().toString(), tenant, a.getFarmId(),
                emptyToNull(a.getBarnId()), emptyToNull(a.getBatchId()), a.getTagCode(),
                emptyToNull(a.getSpecies()), birth, status, Instant.now().toEpochMilli());
        animals.save(e);
        AUDIT.info("animal_registered tenant={} actor_id={} animal_id={} farm_id={} tag={} species={}",
                tenant, safe(req.getContext().getActorId()), e.getId(), e.getFarmId(), e.getTagCode(), safe(e.getSpecies()));
        return e;
    }

    @Transactional(readOnly = true)
    public AnimalEntity get(String tenant, String id) {
        return animals.findByTenantIdAndId(tenant, id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AnimalEntity> list(String tenant, String farm, String batch, int limit) {
        if (farm == null || farm.isBlank()) throw new IllegalArgumentException("farm_id required");
        return animals.list(tenant, farm, emptyToNull(batch),
                PageRequest.of(0, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200))));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
