package com.htv.smartfarm.health.domain;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.health.store.HealthStore;
import com.htv.smartfarm.proto.events.v1.*;
import com.htv.smartfarm.proto.health.v1.*;
import com.htv.smartfarm.proto.common.v1.RequestContext;

import java.time.Instant;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthCommands {
    private final HealthStore store;

    public HealthCommands(HealthStore store) {
        this.store = store;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    private static String key(String tenant, RequestContext context) {
        require(tenant != null && !tenant.isBlank(), "authenticated tenant required");
        require(context != null, "context required");
        require(context.getTenantId().isBlank() || tenant.equals(context.getTenantId()), "tenant mismatch");
        String key = context.getIdempotencyKey();
        require(!key.isBlank() && key.length() <= 128, "idempotency key required (max 128)");
        return key;
    }

    private static Timestamp now() {
        Instant i = Instant.now();
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }

    private static EventMetadata meta(String tenant, String farm, String aggregate, String correlation) {
        return EventMetadata.newBuilder().setEventId(UUID.randomUUID().toString()).setTenantId(tenant).setFarmId(farm).setAggregateId(aggregate).setCorrelationId(correlation).setProducer("smartfarm-health-service").setOccurredAt(now()).build();
    }

    @Transactional
    public HealthObservation recordObservation(String tenant, RecordObservationRequest req) {
        require(req.hasContext() && req.hasObservation(), "context and observation required");
        String k = key(tenant, req.getContext());
        var v = req.getObservation();
        require(!v.getFarmId().isBlank() && !v.getAnimalId().isBlank() && !v.getSymptom().isBlank(), "farm/animal/symptom required");
        require(v.getFarmId().length() <= 100 && v.getAnimalId().length() <= 100 && v.getSymptom().length() <= 1000 && v.getNotes().length() <= 4000, "field too long");
        var existing = store.observationByKey(tenant, k);
        if (existing != null) {
            require(existing.getAnimalId().equals(v.getAnimalId()) && existing.getFarmId().equals(v.getFarmId()) && existing.getSymptom().equals(v.getSymptom()) && existing.getNotes().equals(v.getNotes()), "idempotency key reused");
            return existing;
        }
        var o = v.toBuilder().setObservationId(UUID.randomUUID().toString()).setObservedAt(v.hasObservedAt() ? v.getObservedAt() : now()).build();
        store.saveObservation(tenant, k, o);
        var event = DomainEvent.newBuilder().setMetadata(meta(tenant, o.getFarmId(), o.getObservationId(), req.getContext().getCorrelationId())).setObservationRecorded(ObservationRecorded.newBuilder().setObservation(o)).build();
        store.saveOutbox(event.getMetadata().getEventId(), tenant, o.getFarmId(), o.getObservationId(), "observation-recorded.v1", event.toByteArray());
        return o;
    }

    @Transactional
    public Vaccination recordVaccination(String tenant, RecordVaccinationRequest req) {
        require(req.hasContext() && req.hasVaccination(), "context and vaccination required");
        String k = key(tenant, req.getContext());
        var v = req.getVaccination();
        require(!v.getAnimalId().isBlank() && !v.getVaccineName().isBlank(), "animal and vaccine name required");
        require(v.getAnimalId().length() <= 100 && v.getVaccineName().length() <= 200 && v.getLotCode().length() <= 100, "field too long");
        var existing = store.vaccinationByKey(tenant, k);
        if (existing != null) {
            require(existing.getAnimalId().equals(v.getAnimalId()) && existing.getVaccineName().equals(v.getVaccineName()) && existing.getLotCode().equals(v.getLotCode()), "idempotency key reused");
            return existing;
        }
        var saved = v.toBuilder().setVaccinationId(UUID.randomUUID().toString()).setAdministeredAt(v.hasAdministeredAt() ? v.getAdministeredAt() : now()).build();
        store.saveVaccination(tenant, k, saved);
        // The existing Vaccination message has no farm_id; do not guess a farm/topic from animal_id.
        // Persist the record only. A farm-scoped event requires a verified animal->farm mapping.
        return saved;
    }

    @Transactional(readOnly = true)
    public List<HealthObservation> observations(String tenant, ListObservationsRequest req) {
        require(req.hasContext(), "context required");
        require(req.getContext().getTenantId().isBlank() || tenant.equals(req.getContext().getTenantId()), "tenant mismatch");
        require(!req.getAnimalId().isBlank(), "animal_id required");
        require(!req.hasPage() || req.getPage().getPageToken().isBlank(), "page_token not supported in v1 slice");
        int size = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 20;
        require(size <= 100, "page_size max 100");
        return store.observations(tenant, req.getAnimalId(), size);
    }

    @Transactional(readOnly = true)
    public List<Vaccination> vaccinations(String tenant, ListVaccinationsRequest req) {
        require(req.hasContext(), "context required");
        require(req.getContext().getTenantId().isBlank() || tenant.equals(req.getContext().getTenantId()), "tenant mismatch");
        require(!req.getAnimalId().isBlank(), "animal_id required");
        require(!req.hasPage() || req.getPage().getPageToken().isBlank(), "page_token not supported in v1 slice");
        int size = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 20;
        require(size <= 100, "page_size max 100");
        return store.vaccinations(tenant, req.getAnimalId(), size);
    }
}
