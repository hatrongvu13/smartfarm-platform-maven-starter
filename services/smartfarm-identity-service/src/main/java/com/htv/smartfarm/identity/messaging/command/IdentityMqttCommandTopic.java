package com.htv.smartfarm.identity.messaging.command;

import java.util.Locale;
import java.util.regex.Pattern;

public record IdentityMqttCommandTopic(
        String tenantId,
        String commandName,
        int version
) {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern TOPIC = Pattern.compile(
            "^smartfarm/([A-Za-z0-9_-]{1,100})/identity/command/([a-z0-9_-]{1,100})/v([1-9][0-9]*)$"
    );

    public static IdentityMqttCommandTopic parse(String topic) {
        var matcher = TOPIC.matcher(topic == null ? "" : topic);
        if (!matcher.matches()) throw new IllegalArgumentException("COMMAND_TOPIC_INVALID");
        return new IdentityMqttCommandTopic(
                matcher.group(1),
                matcher.group(2),
                Integer.parseInt(matcher.group(3))
        );
    }

    public boolean matchesType(String commandType) {
        if (commandType == null || commandType.isBlank()) return false;
        String eventName = commandType.substring(commandType.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT)
                .replace('.', '-')
                .replace('_', '-');
        return SEGMENT.matcher(commandName).matches()
                && commandName.equals(eventName);
    }
}
