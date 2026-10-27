package com.htv.farmverse.identity.messaging.command;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMqttInboxMaintenance {

    private final IdentityMqttInboxRepository inbox;
    private final IdentityMqttCommandProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${farmverse.identity.mqtt.commands.cleanup-interval:1h}")
    @Transactional
    public void cleanup() {
        int deleted = inbox.deleteReceivedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) log.info("Identity MQTT inbox cleanup: deleted={}", deleted);
    }
}
