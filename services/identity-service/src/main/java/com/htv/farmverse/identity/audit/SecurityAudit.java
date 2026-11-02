package com.htv.farmverse.identity.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Cửa duy nhất ghi nhật ký bảo mật (logger {@code SECURITY_AUDIT}).
 *
 * <p>Mỗi sự kiện có hai dạng cùng lúc: một dòng {@code event=... actor=... key=value} đọc được ở console dev,
 * và các cặp key/value của SLF4J để log JSON (bật {@code logging.structured.format.console}) có field riêng.
 *
 * <p>{@code actor} mặc định là người đang đăng nhập trong request; truyền {@code "actor", ...} để ghi đè.
 * Mọi giá trị được làm sạch (bỏ ký tự điều khiển, cắt độ dài) để dữ liệu do người gọi nhập không chèn được dòng log giả.
 * <b>Không bao giờ</b> truyền mật khẩu, mã TOTP, mã khôi phục hay token vào đây.
 */
public final class SecurityAudit {

    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final int MAX_FIELD = 80;
    private static final int MAX_LIST = 300;

    private SecurityAudit() {
    }

    /** Sự kiện bình thường. {@code fields} là cặp key, value xen kẽ. */
    public static void info(String event, Object... fields) {
        write(AUDIT.atInfo(), event, fields);
    }

    /** Sự kiện đáng chú ý: thất bại, khóa, bị từ chối, dùng mã khôi phục. */
    public static void warn(String event, Object... fields) {
        write(AUDIT.atWarn(), event, fields);
    }

    private static void write(LoggingEventBuilder builder, String event, Object[] fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("fields phải là cặp key, value");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("actor", actor());
        for (int i = 0; i < fields.length; i += 2) {
            values.put(String.valueOf(fields[i]), format(fields[i + 1]));
        }

        StringBuilder message = new StringBuilder("event=").append(event);
        LoggingEventBuilder logged = builder.addKeyValue("event", event);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            message.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            logged = logged.addKeyValue(entry.getKey(), entry.getValue());
        }
        logged.log(message.toString());
    }

    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "-";
        }
        return clean(authentication.getName(), MAX_FIELD);
    }

    private static String format(Object value) {
        if (value instanceof Iterable<?> items) {
            StringBuilder list = new StringBuilder("[");
            for (Object item : items) {
                if (list.length() > 1) {
                    list.append(',');
                }
                list.append(clean(String.valueOf(item), MAX_FIELD));
            }
            return clean(list.append(']').toString(), MAX_LIST);
        }
        return clean(value == null ? null : String.valueOf(value), MAX_FIELD);
    }

    /** Bỏ ký tự điều khiển và khoảng trắng (để không vỡ dạng key=value), cắt độ dài. */
    static String clean(String value, int max) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}\\s]", "_");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    static String clean(String value) {
        return clean(value, MAX_FIELD);
    }
}
