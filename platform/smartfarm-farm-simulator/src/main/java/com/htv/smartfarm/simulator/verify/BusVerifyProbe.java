package com.htv.smartfarm.simulator.verify;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.security.mqtt.MqttSecurityEnvelope;
import com.htv.smartfarm.security.mqtt.MqttSecurityProperties;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;

/**
 * Standalone live-bus observer used to PROVE, against running services:
 *  - ISSUE-02: producers emit SFM1 HMAC-signed envelopes and the shared dev
 *    secret verifies them (independent re-computation of the HMAC here).
 *  - EVT-01 : the inner payload parses as a protobuf DomainEvent (not JSON),
 *    and identity events now carry the IDENTITY_LIFECYCLE_EVENT oneof case.
 *
 * Not part of the service runtime; run via exec:java. Prints one block per frame.
 */
public final class BusVerifyProbe {

    public static void main(String[] args) throws Exception {
        String broker = System.getProperty("broker", "tcp://127.0.0.1:1883");
        String topic = System.getProperty("topic", "smartfarm/#");
        String secret = System.getProperty("secret", "dev-mqtt-hmac-secret-shared-across-all-services");
        long runMs = Long.parseLong(System.getProperty("runMs", "25000"));

        MqttSecurityProperties props = new MqttSecurityProperties(
                false, true, secret, "dev-key-1", 0L);
        MqttSecurityVerifier verifier = new MqttSecurityVerifier(props);

        AtomicInteger total = new AtomicInteger();
        AtomicInteger signed = new AtomicInteger();
        AtomicInteger verified = new AtomicInteger();
        AtomicInteger proto = new AtomicInteger();
        AtomicInteger identity = new AtomicInteger();

        MqttClient client = new MqttClient(broker, "bus-verify-probe-" + System.nanoTime(), new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setConnectionTimeout(10);
        client.connect(opts);
        System.out.println("[probe] connected " + broker + " subscribing " + topic);
        client.subscribe(topic, (t, msg) -> {
            byte[] raw = msg.getPayload();
            total.incrementAndGet();
            boolean isSigned = MqttSecurityEnvelope.looksSigned(raw);
            System.out.println("\n--- frame #" + total.get() + " ---");
            System.out.println("topic   : " + t);
            System.out.println("bytes   : " + raw.length + "  prefix=" + hexPrefix(raw, 8)
                    + "  magic=" + (isSigned ? "SFM1(signed)" : "none(legacy/raw)"));
            if (isSigned) {
                signed.incrementAndGet();
            }
            MqttSecurityVerifier.Result r = verifier.verify(t, raw);
            System.out.println("verify  : " + (r.accepted() ? "ACCEPTED" : "REJECTED(" + r.reason() + ")"));
            if (!r.accepted()) {
                return;
            }
            verified.incrementAndGet();
            byte[] inner = r.payload();
            try {
                DomainEvent ev = DomainEvent.parseFrom(inner);
                proto.incrementAndGet();
                String evCase = ev.getPayloadCase().name();
                System.out.println("proto   : DomainEvent OK  case=" + evCase);
                if (ev.getPayloadCase() == DomainEvent.PayloadCase.IDENTITY_LIFECYCLE_EVENT) {
                    identity.incrementAndGet();
                    System.out.println("identity: type=" + ev.getIdentityLifecycleEvent().getEventType()
                            + " aggType=" + ev.getIdentityLifecycleEvent().getAggregateType()
                            + " actor=" + ev.getIdentityLifecycleEvent().getActorId()
                            + " data=" + ev.getIdentityLifecycleEvent().getDataMap());
                }
            } catch (Exception e) {
                System.out.println("proto   : NOT a DomainEvent (" + e.getClass().getSimpleName()
                        + ") firstBytes=" + new String(inner, 0, Math.min(inner.length, 40), StandardCharsets.UTF_8));
            }
        });

        Thread.sleep(runMs);
        client.disconnect();
        System.out.println("\n==== SUMMARY ====");
        System.out.println("frames total     : " + total.get());
        System.out.println("SFM1 signed      : " + signed.get());
        System.out.println("HMAC verified    : " + verified.get());
        System.out.println("protobuf parsed  : " + proto.get());
        System.out.println("identity events  : " + identity.get());
        System.out.println("=================");
        System.exit(0);
    }

    private static String hexPrefix(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) {
            sb.append(String.format("%02x", b[i]));
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
