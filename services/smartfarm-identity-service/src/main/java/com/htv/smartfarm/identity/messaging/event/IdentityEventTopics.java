package com.htv.smartfarm.identity.messaging.event;

import java.util.Locale;
import java.util.regex.Pattern;

public final class IdentityEventTopics {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private IdentityEventTopics() {
    }

    /**
     * Standard SmartFarm domain topic: {@code smartfarm/<tenant>/_global/domain/<event>/v<n>}.
     * Identity is tenant-global (no farm), so the farm segment is the literal {@code _global}; this
     * matches the gateway WS bridge filter {@code smartfarm/+/+/domain/#} (EVT-02 — previously used a
     * non-standard {@code <aggregate>-<event>} segment).
     */
    public static String domain(String tenantId, String eventName, int version) {
        return "smartfarm/" + segment(tenantId, "tenantId")
                + "/_global/domain/"
                + segment(normalize(eventName), "eventName")
                + "/v" + version;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replace('.', '-');
    }

    private static String segment(String value, String field) {
        if (value == null || !SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid MQTT topic segment");
        }
        return value;
    }
}
