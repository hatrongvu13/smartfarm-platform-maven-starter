package com.htv.smartfarm.livestock.outbox;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Single-instance DEV relay. Publish outside DB transaction; ack before marking PUBLISHED. */
@Component
@ConditionalOnProperty(prefix="smartfarm.outbox",name="enabled",havingValue="true")
public class OutboxRelay {
    private static final Logger log=LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository outbox;private final MqttPublisher publisher;
    public OutboxRelay(OutboxRepository outbox,MqttPublisher publisher){this.outbox=outbox;this.publisher=publisher;}
    @Scheduled(fixedDelayString="${smartfarm.outbox.poll-ms:2000}")
    public void poll(){
        for(OutboxEvent row:outbox.pending(50)){
            try{
                publisher.publish(TaskEventMapper.topic(row),TaskEventMapper.event(row).toByteArray());
                outbox.markPublished(row.eventId());
                log.info("Outbox event published: eventId={} type={}",row.eventId(),row.eventType());
            }catch(Exception ex){
                // Leave NEW; retry with the SAME eventId. Do not expose payload or credentials in logs.
                log.warn("Outbox publish deferred: eventId={} reason={}",row.eventId(),ex.getClass().getSimpleName());
                break; // broker unavailable: avoid tight-loop attempts across the batch
            }
        }
    }
}
