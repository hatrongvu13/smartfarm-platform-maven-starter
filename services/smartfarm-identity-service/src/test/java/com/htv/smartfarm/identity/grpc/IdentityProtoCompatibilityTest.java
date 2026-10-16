package com.htv.smartfarm.identity.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import com.google.protobuf.Descriptors;
import com.htv.smartfarm.proto.identity.v1.IdentityProto;

import org.junit.jupiter.api.Test;

class IdentityProtoCompatibilityTest {

    @Test
    void preservesTheExistingIdentityV1WireContract() throws IOException {
        var expected = getClass().getResourceAsStream(
                "/proto/identity-v1-contract.txt"
        );
        assertThat(expected)
                .as("Identity v1 contract baseline")
                .isNotNull();

        try (var input = expected) {
            var baseline = new TreeSet<>(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .filter(line -> !line.isBlank())
                            .toList()
            );
            assertThat(contractEntries())
                    .containsAll(baseline);
        }
    }

    private static Set<String> contractEntries() {
        var entries = new TreeSet<String>();
        var file = IdentityProto.getDescriptor();
        var fileProto = file.toProto();
        var options = fileProto.getOptions();

        entries.add("P|" + file.getPackage());
        entries.add("O|syntax|" + fileProto.getSyntax());
        entries.add("O|java_package|" + options.getJavaPackage());
        entries.add("O|java_multiple_files|" + options.getJavaMultipleFiles());
        entries.add("O|java_outer_classname|" + options.getJavaOuterClassname());

        file.getMessageTypes().forEach(message ->
                addMessageEntries(message, entries)
        );
        file.getEnumTypes().forEach(enumType ->
                addEnumEntries(enumType, entries)
        );
        file.getServices().forEach(service -> {
            entries.add("S|" + service.getFullName());
            service.getMethods().forEach(method -> entries.add(
                    "R|" + service.getFullName()
                            + "|" + method.getName()
                            + "|" + method.getInputType().getFullName()
                            + "|" + method.getOutputType().getFullName()
                            + "|" + method.isClientStreaming()
                            + "|" + method.isServerStreaming()
            ));
        });
        return entries;
    }

    private static void addMessageEntries(
            Descriptors.Descriptor message,
            Set<String> entries
    ) {
        entries.add("M|" + message.getFullName());
        message.getFields().forEach(field -> {
            var type = field.getType();
            var typeName = type == Descriptors.FieldDescriptor.Type.MESSAGE
                    ? field.getMessageType().getFullName()
                    : type == Descriptors.FieldDescriptor.Type.ENUM
                            ? field.getEnumType().getFullName()
                            : "-";
            var oneof = field.getContainingOneof() == null
                    ? "-"
                    : field.getContainingOneof().getName();
            entries.add(
                    "F|" + message.getFullName()
                            + "|" + field.getNumber()
                            + "|" + field.getName()
                            + "|" + type.name()
                            + "|" + field.isRepeated()
                            + "|" + field.hasPresence()
                            + "|" + oneof
                            + "|" + field.getJsonName()
                            + "|" + typeName
                            + "|" + field.isPacked()
                            + "|" + field.isMapField()
            );
        });
        message.getNestedTypes().forEach(nested ->
                addMessageEntries(nested, entries)
        );
        message.getEnumTypes().forEach(enumType ->
                addEnumEntries(enumType, entries)
        );
    }

    private static void addEnumEntries(
            Descriptors.EnumDescriptor enumType,
            Set<String> entries
    ) {
        entries.add("E|" + enumType.getFullName());
        enumType.getValues().forEach(value -> entries.add(
                "V|" + enumType.getFullName()
                        + "|" + value.getNumber()
                        + "|" + value.getName()
        ));
    }
}
