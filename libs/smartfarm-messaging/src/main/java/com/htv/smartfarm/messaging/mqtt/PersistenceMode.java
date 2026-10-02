package com.htv.smartfarm.messaging.mqtt;

/** How a consumer's MQTT client persists in-flight (QoS1/2) state. */
public enum PersistenceMode {
    /** In-memory: nothing survives a restart. Pair with cleanSession=true (ephemeral consumer). */
    MEMORY,
    /** File-backed: in-flight state survives a restart. Requires a stable client id + a writable, per-client directory. */
    FILE
}
