package com.htv.farmverse.gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.adapter.ForwardedHeaderTransformer;

/**
 * Đọc {@code Forwarded} / {@code X-Forwarded-*} (scheme, host, port) CHỈ khi kết nối TCP đến từ proxy tin cậy
 * (edge TLS). Từ nguồn khác: xóa các header đó rồi xử lý như request thường.
 *
 * <p>Vì sao cần: gateway dựng {@code redirect_uri} OIDC từ URL của request, cả lúc bắt đầu đăng nhập lẫn lúc callback
 * (Spring so hai giá trị). Sau edge TLS, gateway thấy {@code http://<máy nội bộ>}, nên phải lấy URL công khai từ
 * header của edge. WebFlux không có danh sách proxy tin cậy sẵn ({@link ForwardedHeaderTransformer} và chế độ
 * {@code native} của Reactor Netty đều tin mọi nguồn), nên lớp này thêm bước kiểm địa chỉ.
 *
 * <p>Đặt làm bean tên {@code forwardedHeaderTransformer}: {@code WebHttpHandlerBuilder} áp nó trước mọi
 * {@code WebFilter}, kể cả Spring Security. Cần {@code server.forward-headers-strategy: none} để Reactor Netty không
 * tự xử lý trước (Boot tự bật {@code native} trên Kubernetes).
 *
 * <p>Địa chỉ so khớp là địa chỉ TCP thật, không phải {@code X-Forwarded-For}: đó chính là thứ đang cần kiểm.
 * Không dùng {@code IpAddressMatcher} của Spring Security vì lớp đó cần Servlet API, gateway là WebFlux.
 */
public class TrustedProxyForwardedHeaderTransformer extends ForwardedHeaderTransformer {

    private final List<Cidr> trusted;
    /** Chỉ xóa header forwarded, không áp dụng. (Tham số boolean của constructor là useStandardHeader, không phải cờ này.) */
    private final ForwardedHeaderTransformer strip = new ForwardedHeaderTransformer();

    /** @param trustedProxies IP hoặc CIDR, ví dụ {@code 10.0.0.0/8}, {@code fd00::/8}; rỗng = không tin ai. */
    public TrustedProxyForwardedHeaderTransformer(List<String> trustedProxies) {
        this.trusted = trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).map(Cidr::parse).toList();
        strip.setRemoveOnly(true);
        setUseForwardedPrefix(false);   // gateway luôn ở gốc "/": không cho proxy đổi đường dẫn gốc
    }

    @Override
    public ServerHttpRequest apply(ServerHttpRequest request) {
        return fromTrustedProxy(request) ? super.apply(request) : strip.apply(request);
    }

    boolean fromTrustedProxy(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        return trusts(remote.getAddress());
    }

    boolean trusts(InetAddress address) {
        return trusted.stream().anyMatch(cidr -> cidr.contains(address));
    }

    /** Một dải IP dạng CIDR. Chỉ nhận địa chỉ số (không phân giải tên miền), sai định dạng thì lỗi khi khởi động. */
    record Cidr(byte[] network, int prefix) {

        static Cidr parse(String value) {
            String[] parts = value.split("/", 2);
            if (!parts[0].matches("[0-9a-fA-F:.]+")) {
                throw new IllegalArgumentException("trusted-proxies phải là IP hoặc CIDR, không phải tên máy: " + value);
            }
            byte[] network;
            try {
                network = InetAddress.getByName(parts[0]).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("trusted-proxies không hợp lệ: " + value, e);
            }
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
            if (prefix < 0 || prefix > network.length * 8) {
                throw new IllegalArgumentException("Độ dài prefix không hợp lệ: " + value);
            }
            return new Cidr(network, prefix);
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;   // IPv4 và IPv6 không bao giờ khớp nhau
            }
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rest) & 0xFF;
            return (candidate[full] & mask) == (network[full] & mask);
        }
    }
}
