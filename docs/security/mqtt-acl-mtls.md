# MQTT Security — Signed Envelope + (chưa bật) ACL & mTLS

> Reconciled 2026-10-15: `infra/mosquitto.conf` allows anonymous connections and does not configure ACL/TLS. `MqttSecurityVerifier` and `MqttSecurityEnvelope` are implemented. App-level HMAC is enabled by current dev profile files and defaults off in production profile files; broker-level ACL/TLS remains a separate, unenabled control.

Hai lớp này độc lập và bổ sung cho nhau. Khuyến nghị production: bật **cả hai**.
mTLS + ACL chặn client lạ kết nối broker; signed envelope đảm bảo toàn vẹn/ nguồn gốc message
kể cả khi một service hợp lệ bị chiếm hoặc khi broker bị bỏ qua lớp mạng.

---

## 1) Signed Envelope (app-level) — ĐÃ TRIỂN KHAI, profile-dependent

Thành phần (trong `libs/farmverse-security`):
- `com.htv.farmverse.security.mqtt.MqttSecurityEnvelope` — frame tự mô tả, magic `SFM1`, HMAC-SHA256 over `topic ‖ metadata ‖ payload`.
- `com.htv.farmverse.security.mqtt.MqttSecurityVerifier` — `sign(topic,payload)` / `verify(topic,frame)`; so sánh chữ ký constant-time; chữ ký ràng buộc cả topic (frame ký cho topic A bị từ chối ở topic B).
- `com.htv.farmverse.security.mqtt.MqttSecurityProperties` — prefix `farmverse.security.mqtt`; property defaults are false, while service profile files override dev to true and prod to false.

### Cấu hình

```yaml
farmverse:
  security:
    mqtt:
      sign-enabled: ${MQTT_SIGN_ENABLED:false}     # producer ký message
      verify-enabled: ${MQTT_VERIFY_ENABLED:false}  # consumer bắt buộc chữ ký hợp lệ
      hmac-secret: ${MQTT_HMAC_SECRET:}             # bí mật dùng chung giữa các service
      key-id: ${MQTT_HMAC_KEY_ID:default}           # định danh khóa (phục vụ xoay khóa)
      max-clock-skew-millis: ${MQTT_MAX_CLOCK_SKEW_MILLIS:0}  # 0 = tắt kiểm tra độ tươi
```

### Ngữ nghĩa (backward-compatible)
| sign-enabled | verify-enabled | Hành vi |
|---|---|---|
| false | false | **Mặc định** — `sign()` trả payload nguyên vẹn; `verify()` nhận mọi thứ (gỡ frame nếu có, pass-through nếu không). KHÔNG đổi hành vi. |
| true | false | Producer ký; consumer vẫn chấp nhận cả signed lẫn legacy (giai đoạn chuyển tiếp). |
| true | true | Producer ký; consumer **từ chối** mọi message không có chữ ký hợp lệ. |
| false | true | Chỉ dùng để test "enforcing" — mọi legacy payload bị từ chối. |

### Thứ tự rollout an toàn (không downtime)
1. Deploy tất cả service với `hmac-secret` cấu hình, `sign-enabled=true`, `verify-enabled=false`.
2. Theo dõi: toàn bộ traffic giờ là signed frame; consumer vẫn chấp nhận hết.
3. Khi chắc mọi producer đã ký, bật `verify-enabled=true` trên từng consumer.
4. (Tùy chọn) bật `max-clock-skew-millis` (vd 300000 = 5 phút) để giảm cửa sổ replay.

### Cách nối vào publisher/consumer (CHƯA nối — mô tả patch)
> Hiện mới triển khai thư viện + test; CHƯA chèn vào `OrderMqttPublisher`/`OrderChangedConsumer`
> để không đổi hành vi hệ thống local đang chạy. Khi bật, patch mô tả như sau:

- Producer (`OrderMqttPublisher.publish`, `livestock OutboxRelay`, `health PahoHealthEventPublisher`, `identity IdentityOutboxRelay`):
  thay `client.publish(topic, new MqttMessage(payload))` bằng `payload = verifier.sign(topic, payload);` trước khi tạo `MqttMessage`.
- Consumer (`OrderChangedConsumer.messageArrived`, gateway `MqttEventSubscriber`, inventory `TaskMqttSubscriber`):
  ```java
  var r = verifier.verify(topic, message.getPayload());
  if (!r.accepted()) { log.warn("drop MQTT msg on {}: {}", topic, r.reason()); return; }
  projector.apply(r.payload());
  ```
- Bean: khai báo `MqttSecurityVerifier` + bind `MqttSecurityProperties` qua một `@Configuration`
  trong `libs/farmverse-security` (autoconfigure), inject vào các component trên.

---

## 2) Broker ACL (mosquitto) — MẪU, CHƯA BẬT

> KHÔNG áp dụng tự động. Thay `infra/mosquitto.conf` khi triển khai thật.
> Topic shape dự án: `farmverse/{tenant}/{farm}/domain/{event}/{id}` và `farmverse/{tenant}/{farm}/command/...`.

`infra/mosquitto.conf` (bản production mẫu):
```conf
# TẮT ẩn danh — bắt buộc có credential hoặc client-cert
allow_anonymous false

# listener TLS (xem mục mTLS bên dưới); bỏ listener 1883 trần ở prod
listener 8883
protocol mqtt

# WebSocket qua TLS cho web client (gateway proxy)
listener 9001
protocol websockets

# Nguồn danh tính: hoặc password_file, hoặc use_identity_as_username từ client cert
password_file /mosquitto/config/passwd
acl_file /mosquitto/config/aclfile
```

`infra/mosquitto/aclfile` (mẫu — mỗi service một user, quyền tối thiểu):
```conf
# order-service: publish order-changed, subscribe order-changed (read-model)
user farmverse-order
topic write farmverse/+/+/domain/order-changed/+
topic read  farmverse/+/+/domain/order-changed/+

# livestock-service: publish task-changed
user farmverse-livestock
topic write farmverse/+/+/domain/task-changed/+

# inventory-service: subscribe task-changed (task inbox)
user farmverse-inventory
topic read  farmverse/+/+/domain/task-changed/+

# health-service: publish health observations
user farmverse-health
topic write farmverse/+/+/domain/health-changed/+

# identity-service: publish identity command/result + events
user farmverse-identity
topic write farmverse/+/+/command/identity/+
topic write farmverse/+/+/domain/identity-event/+

# gateway: subscribe mọi domain event để fan-out WebSocket (chỉ read)
user api-gateway
topic read  farmverse/+/+/domain/#
```

`infra/mosquitto/passwd` sinh bằng:
```bash
mosquitto_passwd -c infra/mosquitto/passwd farmverse-order
mosquitto_passwd    infra/mosquitto/passwd farmverse-livestock
# ... mỗi service một dòng
```
Mỗi service nạp username/password qua env đã có sẵn: `farmverse.mqtt.username` / `farmverse.mqtt.password`
(thấy trong `application-prod.yml` của order: `FARMVERSE_MQTT_USERNAME`/`FARMVERSE_MQTT_PASSWORD`).

---

## 3) mTLS (mutual TLS) — MẪU, CHƯA BẬT

> Chặn client không có cert hợp lệ kết nối broker; thay cho (hoặc kèm) password_file.

Bổ sung vào listener TLS trong `mosquitto.conf`:
```conf
listener 8883
protocol mqtt
cafile   /mosquitto/certs/ca.crt
certfile /mosquitto/certs/server.crt
keyfile  /mosquitto/certs/server.key
require_certificate true
use_identity_as_username true     # CN của client-cert trở thành username cho ACL ở trên
tls_version tlsv1.2
```

Sinh CA + server + per-service client cert (mẫu dev — thay bằng CA thật ở prod):
```bash
# CA
openssl req -new -x509 -days 3650 -nodes \
  -keyout ca.key -out ca.crt -subj "/CN=farmverse-mqtt-ca"

# Server (broker)
openssl req -new -nodes -keyout server.key -out server.csr -subj "/CN=mqtt.farmverse.local"
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days 825

# Client per service — CN phải khớp user trong aclfile
for svc in farmverse-order farmverse-livestock farmverse-inventory \
           farmverse-health farmverse-identity api-gateway; do
  openssl req -new -nodes -keyout "$svc.key" -out "$svc.csr" -subj "/CN=$svc"
  openssl x509 -req -in "$svc.csr" -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out "$svc.crt" -days 825
done
```

Phía Paho client (mô tả — CHƯA sửa code): nạp `ca.crt` + client cert/key vào
`SSLSocketFactory` và `MqttConnectOptions.setSocketFactory(...)`, đổi URL `tcp://` → `ssl://host:8883`.
Nên bọc trong một helper dùng chung ở `libs/farmverse-security` khi triển khai thật.

---

## 4) Checklist khi áp dụng thật
- [ ] Phát sinh `hmac-secret` mạnh (≥32 bytes), lưu trong secret manager, chia sẻ cho mọi service.
- [ ] Bật `sign-enabled=true` toàn bộ producer trước; quan sát; rồi `verify-enabled=true` ở consumer.
- [ ] Nối `verifier.sign/verify` vào publisher/consumer (patch mô tả ở mục 1).
- [ ] Thay `infra/mosquitto.conf` sang bản `allow_anonymous false` + `acl_file` + TLS.
- [ ] Sinh cert theo mục 3, mount vào container broker + từng service.
- [ ] Cập nhật `compose.yaml` mount `aclfile`/`passwd`/`certs`; đổi port 1883→8883.
- [ ] Xác minh: client ẩn danh bị từ chối; message không ký bị drop khi `verify-enabled=true`.
