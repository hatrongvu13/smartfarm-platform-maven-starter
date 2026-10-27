package com.htv.farmverse.identity.messaging.command;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityMqttInboxWriter {

    private final IdentityMqttInboxRepository inbox;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveReceived(
            IdentityMqttCommandEnvelope envelope,
            String topic,
            byte[] payload,
            Instant now
    ) {
        inbox.saveAndFlush(IdentityMqttInboxEntity.received(envelope, topic, payload, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRejected(
            String commandId,
            String topic,
            byte[] payload,
            String rejectionCode,
            Instant now
    ) {
        inbox.saveAndFlush(IdentityMqttInboxEntity.rejected(
                commandId, topic, payload, rejectionCode, now));
    }
}
