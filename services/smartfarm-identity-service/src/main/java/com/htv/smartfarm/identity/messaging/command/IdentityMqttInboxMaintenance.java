package com.htv.smartfarm.identity.messaging.command;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityMqttInboxMaintenance {

    private static final Logger log = LoggerFactory.getLogger(IdentityMqttInboxMaintenance.class);
    private final IdentityMqttInboxRepository inbox;
    private final IdentityMqttCommandProperties properties;
    private final Clock clock;

    public IdentityMqttInboxMaintenance(
            IdentityMqttInboxRepository inbox,
            IdentityMqttCommandProperties properties,
            Clock clock
    ) {
        this.inbox = inbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartfarm.identity.mqtt.commands.cleanup-interval:1h}")
    @Transactional
    public void cleanup() {
        int deleted = inbox.deleteReceivedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) log.info("Identity MQTT inbox cleanup: deleted={}", deleted);
    }
}
