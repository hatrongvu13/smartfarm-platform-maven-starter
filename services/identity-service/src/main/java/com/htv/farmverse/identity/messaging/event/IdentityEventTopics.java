package com.htv.farmverse.identity.messaging.event;

import java.util.Locale;
import java.util.regex.Pattern;

import com.htv.farmverse.common.contract.IdentityEventContract;

public final class IdentityEventTopics {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private IdentityEventTopics() {
    }

    public static String domain(String tenantId, String eventName, int version) {
        return IdentityEventContract.TOPIC_NAMESPACE + "/"
                + segment(tenantId, "tenantId")
                + "/" + IdentityEventContract.GLOBAL_FARM_ID
                + "/" + IdentityEventContract.DOMAIN_EVENT_SEGMENT + "/"
                + segment(normalize(eventName), "eventName")
                + "/" + IdentityEventContract.TOPIC_VERSION_PREFIX + version;
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
