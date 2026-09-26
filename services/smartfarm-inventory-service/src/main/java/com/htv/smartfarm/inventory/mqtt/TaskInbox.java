package com.htv.smartfarm.inventory.mqtt;

import com.htv.smartfarm.inventory.domain.InventoryRepository;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskInbox {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private final InventoryRepository repo;

    public TaskInbox(InventoryRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public boolean accept(String topic, byte[] payload) throws InvalidProtocolBufferException {
        var e = DomainEvent.parseFrom(payload);
        var m = e.getMetadata();
        if (!e.hasTaskChanged() || m.getEventId().isBlank() || m.getTenantId().isBlank() || m.getFarmId().isBlank() || m.getAggregateId().isBlank())
            throw new IllegalArgumentException("invalid task event");
        if (!SEGMENT.matcher(m.getTenantId()).matches() || !SEGMENT.matcher(m.getFarmId()).matches() || !topic.equals("smartfarm/" + m.getTenantId() + "/" + m.getFarmId() + "/domain/task-changed/v1") || !m.getAggregateId().equals(e.getTaskChanged().getTask().getTaskId()))
            throw new IllegalArgumentException("topic/metadata mismatch");
        if (repo.inboxExists(m.getEventId())) return false;
        return repo.recordInbox(m.getEventId(), m.getTenantId(), m.getAggregateId());
    }
}
