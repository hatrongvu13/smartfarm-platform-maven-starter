package com.htv.farmverse.contracts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.htv.farmverse.contracts.identity.v1.IdentityDirectoryProto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T6: khóa hợp đồng proto. So mô tả hiện tại (từ descriptor của lớp sinh ra) với bản chuẩn đã commit
 * {@code src/test/resources/contracts/identity-v1.txt}: mọi dòng của bản chuẩn phải còn nguyên.
 * Thêm field/rpc/giá trị enum mới thì được (dòng mới); đổi số field, đổi kiểu, đổi tên, xóa thì đỏ.
 *
 * <p>Khi cố ý THÊM vào hợp đồng: {@link #baselineCoversTheWholeCurrentContract} đỏ và in các dòng mới; chép chúng
 * vào bản chuẩn để khóa luôn phần mới. Không bao giờ sửa hay xóa dòng cũ: phá vỡ thì làm {@code v2}.
 */
class ContractCompatibilityTest {

    static final FileDescriptor IDENTITY_V1 = IdentityDirectoryProto.getDescriptor();

    @Test
    void identityV1KeepsEveryPublishedFieldNumberTypeAndName() throws IOException {
        List<String> baseline = baseline("contracts/identity-v1.txt");
        TreeSet<String> current = describe(IDENTITY_V1);

        assertThat(baseline).as("bản chuẩn không rỗng").isNotEmpty();
        assertThat(current).as("hợp đồng hiện tại phải chứa mọi dòng của bản chuẩn (chỉ được thêm)")
                .containsAll(baseline);
    }

    /** Bản chuẩn phải đầy đủ: phần đã có trong proto mà chưa khóa thì cũng báo, để không quên cập nhật. */
    @Test
    void baselineCoversTheWholeCurrentContract() throws IOException {
        TreeSet<String> missing = describe(IDENTITY_V1);
        missing.removeAll(baseline("contracts/identity-v1.txt"));

        assertThat(missing).as("thêm các dòng này vào src/test/resources/contracts/identity-v1.txt").isEmpty();
    }

    @Test
    void packageIsVersionedAndEveryEnumStartsWithUnspecifiedZero() {
        assertThat(IDENTITY_V1.getPackage()).matches("farmverse\\.[a-z]+\\.v[0-9]+");
        List<EnumDescriptor> enums = new ArrayList<>(IDENTITY_V1.getEnumTypes());
        IDENTITY_V1.getMessageTypes().forEach(m -> enums.addAll(m.getEnumTypes()));
        for (EnumDescriptor e : enums) {
            EnumValueDescriptor first = e.getValues().get(0);
            assertThat(first.getNumber()).as(e.getFullName()).isZero();
            assertThat(first.getName()).as(e.getFullName()).endsWith("_UNSPECIFIED");
        }
    }

    /**
     * Một dòng cho mỗi phần tử của hợp đồng: message/field (số, kiểu, nhãn), enum/giá trị, service/rpc (kiểu vào, ra,
     * streaming). Field đã khai {@code reserved} không xuất hiện ở đây, nên bỏ field mà không reserved vẫn bị bắt.
     */
    static TreeSet<String> describe(FileDescriptor file) {
        TreeSet<String> lines = new TreeSet<>();
        for (Descriptor message : file.getMessageTypes()) {
            describe(message, lines);
        }
        for (EnumDescriptor e : file.getEnumTypes()) {
            describe(e, lines);
        }
        for (ServiceDescriptor service : file.getServices()) {
            for (MethodDescriptor rpc : service.getMethods()) {
                lines.add("rpc " + service.getFullName() + "." + rpc.getName() + "(" + (rpc.isClientStreaming() ? "stream " : "")
                        + rpc.getInputType().getFullName() + ") returns (" + (rpc.isServerStreaming() ? "stream " : "")
                        + rpc.getOutputType().getFullName() + ")");
            }
        }
        return lines;
    }

    private static void describe(Descriptor message, TreeSet<String> lines) {
        for (FieldDescriptor field : message.getFields()) {
            String type = switch (field.getJavaType()) {
                case MESSAGE -> field.getMessageType().getFullName();
                case ENUM -> field.getEnumType().getFullName();
                default -> field.getType().name().toLowerCase();
            };
            lines.add("field " + message.getFullName() + "." + field.getName() + " = " + field.getNumber() + " "
                    + (field.isRepeated() ? "repeated " : "") + type);
        }
        message.getNestedTypes().forEach(nested -> describe(nested, lines));
        message.getEnumTypes().forEach(e -> describe(e, lines));
    }

    private static void describe(EnumDescriptor e, TreeSet<String> lines) {
        for (EnumValueDescriptor value : e.getValues()) {
            lines.add("enum " + e.getFullName() + "." + value.getName() + " = " + value.getNumber());
        }
    }

    private static List<String> baseline(String resource) throws IOException {
        try (InputStream in = ContractCompatibilityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        }
    }
}
