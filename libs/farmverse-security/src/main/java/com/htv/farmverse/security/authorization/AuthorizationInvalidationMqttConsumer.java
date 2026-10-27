package com.htv.farmverse.security.authorization;

import com.htv.farmverse.proto.events.v1.DomainEvent;
import com.htv.farmverse.security.mqtt.MqttSecurityVerifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Broadcast consumer for Identity authorization watermarks. Broker outages never block service startup. */
public final class AuthorizationInvalidationMqttConsumer {
    private static final Logger log=LoggerFactory.getLogger(AuthorizationInvalidationMqttConsumer.class);
    private static final Pattern TENANT=Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private final AuthorizationInvalidationMqttProperties properties;
    private final AuthorizationInvalidationObserver observer;
    private final MqttSecurityVerifier security;
    private final ScheduledExecutorService retry=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"authorization-watermark-mqtt"); t.setDaemon(true); return t; });
    private final AtomicBoolean connecting=new AtomicBoolean();
    private volatile MqttClient client;
    private volatile boolean stopped;

    public AuthorizationInvalidationMqttConsumer(AuthorizationInvalidationMqttProperties properties,
            AuthorizationInvalidationObserver observer,MqttSecurityVerifier security) {
        this.properties=properties; this.observer=observer; this.security=security;
    }
    @PostConstruct public void start() { if(properties.enabled()) schedule(Duration.ZERO); }
    private void schedule(Duration delay) {
        if(!stopped) retry.schedule(this::connect,Math.max(0,delay.toMillis()),TimeUnit.MILLISECONDS);
    }
    private void connect() {
        if(stopped||!connecting.compareAndSet(false,true)) return;
        try {
            if(client==null) {
                client=new MqttClient(properties.brokerUrl(),properties.clientId(),new MemoryPersistence());
                client.setManualAcks(true);
                client.setCallback(callback());
            }
            if(client.isConnected()) return;
            MqttConnectOptions o=new MqttConnectOptions();
            o.setCleanSession(true); // unique client per live instance: broadcast to every destination
            o.setAutomaticReconnect(true); o.setConnectionTimeout(5); o.setKeepAliveInterval(20);
            if(!properties.username().isBlank()) { o.setUserName(properties.username()); o.setPassword(properties.password().toCharArray()); }
            client.connect(o);
            subscribe(false);
        } catch(MqttException e) {
            log.warn("Authorization invalidation MQTT connect deferred: {}",e.getReasonCode());
            schedule(Duration.ofSeconds(10));
        } finally { connecting.set(false); }
    }
    private MqttCallbackExtended callback() {
        return new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect,String uri) { subscribe(reconnect); }
            @Override public void connectionLost(Throwable cause) {
                log.warn("Authorization invalidation MQTT disconnected: {}",cause==null?"unknown":cause.getClass().getSimpleName());
                schedule(Duration.ofSeconds(10));
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            @Override public void messageArrived(String topic,MqttMessage message) throws Exception { consume(topic,message); }
        };
    }
    private void subscribe(boolean reconnect) {
        try {
            if(client!=null&&client.isConnected()) {
                client.subscribe(properties.topicFilter(),properties.qos());
                log.info("Authorization invalidation subscribed topic={} client_id={} reconnect={}",properties.topicFilter(),properties.clientId(),reconnect);
            }
        } catch(MqttException e) { log.warn("Authorization invalidation subscribe deferred: {}",e.getReasonCode()); schedule(Duration.ofSeconds(10)); }
    }
    private void consume(String topic,MqttMessage message) throws MqttException {
        MqttSecurityVerifier.Result verified=security.verify(topic,message.getPayload());
        if(!verified.accepted()) { ack(message); log.warn("Authorization invalidation rejected topic={} reason={}",topic,verified.reason()); return; }
        try {
            DomainEvent event=DomainEvent.parseFrom(verified.payload());
            if(!event.hasAuthorizationInvalidated()) { ack(message); return; }
            var value=event.getAuthorizationInvalidated();
            validate(topic,value.getTenantId(),event.getMetadata().getTenantId());
            observer.observe(value.getTenantId(),value.getSubjectId(),value.getTenantAuthorizationVersion(),value.getSubjectAuthorizationVersion());
            ack(message);
            log.info("Authorization watermark observed tenant={} subject={} tenant_version={} subject_version={} reason={}",
                    value.getTenantId(),value.getSubjectId().isBlank()?"-":value.getSubjectId(),
                    value.getTenantAuthorizationVersion(),value.getSubjectAuthorizationVersion(),value.getReasonCode());
        } catch(com.google.protobuf.InvalidProtocolBufferException|IllegalArgumentException permanent) {
            ack(message); log.warn("Authorization invalidation malformed topic={} reason={}",topic,permanent.getMessage());
        }
    }
    private void validate(String topic,String payloadTenant,String metadataTenant) {
        if(payloadTenant==null||!TENANT.matcher(payloadTenant).matches()) throw new IllegalArgumentException("invalid tenant in payload");
        String[] s=topic.split("/");
        if(s.length!=6||!"farmverse".equals(s[0])||!payloadTenant.equals(s[1])||!"_global".equals(s[2])
                ||!"domain".equals(s[3])||!"authorization-invalidated".equals(s[4])||!"v1".equals(s[5]))
            throw new IllegalArgumentException("topic/payload tenant mismatch");
        if(metadataTenant!=null&&!metadataTenant.isBlank()&&!payloadTenant.equals(metadataTenant))
            throw new IllegalArgumentException("metadata/payload tenant mismatch");
    }
    private void ack(MqttMessage m) throws MqttException { client.messageArrivedComplete(m.getId(),m.getQos()); }
    @PreDestroy public void stop() {
        stopped=true; retry.shutdownNow(); MqttClient c=client; if(c==null)return;
        try { if(c.isConnected())c.disconnect(); c.close(); } catch(MqttException e) { log.debug("Authorization invalidation MQTT close: {}",e.getReasonCode()); }
    }
}
