# Tập 06 — gRPC identity không check scope: service quyền cao gác yếu nhất

**Mức: VỪA (MEDIUM)**

## Hook
"Identity là service quyền lực nhất: nó cấp quyền cho cả hệ. Vậy mà cổng gRPC của nó chỉ hỏi 'bạn có token không', không hỏi 'bạn có quyền không'."

## Bối cảnh
Cơ chế chính sách gRPC: một method có thể là PUBLIC, AUTHENTICATED (chỉ cần
token hợp lệ), hoặc SCOPED (cần quyền cụ thể). Khai scope qua
`smartfarm.security.grpc.authorities`.

Identity service đặt `authorization-mode: SHADOW` nhưng **không khai**
`authorities` nào, không `authenticated-methods`, không `public-methods`,
cũng không bật `require-explicit-policy`. Trong khi các service khác
(order, inventory, health...) đều khai đầy đủ scope và bật
`require-explicit-policy: true`.

Hệ quả: với method chưa khai scope, `requiredAuthority == null`, và cơ chế
coi `null` là "chỉ cần token". Mọi method gRPC của identity — gồm cả quản
trị như `PlatformAuthorizationAdministration/*` — chạy với bất kỳ token hợp
lệ nào, không kiểm quyền.

## Tái hiện (quay màn hình)
```bash
# Identity đặt SHADOW nhưng không khai authorities
grep -n "authorization-mode\|authorities\|require-explicit-policy" \
  services/smartfarm-identity-service/src/main/resources/application.yml

# So với một service làm đúng: khai scope + bật require-explicit-policy
grep -n "authorities\|require-explicit-policy" \
  services/smartfarm-order-service/src/main/resources/application.yml
```

## Biểu hiện
Không lỗi, không sập — đó là cái nguy. Một client có token người dùng bình
thường vẫn gọi được method quản trị của identity qua gRPC, vì không gì chặn
theo quyền. SHADOW mode chỉ quan sát, và chỉ quan sát khi method có khai
quyền — ở đây không khai, nên nó không quan sát gì.

## Nguyên nhân gốc
Hợp đồng gRPC không nhất quán giữa các service: 6 service bật chính sách
rõ ràng, riêng identity bỏ ngỏ. Service đáng gác chặt nhất lại gác lỏng nhất.

## Cách sửa
Khai `authorities` cho các method quản trị/thư mục của identity, soi theo
các scope `identity:*` đã có trong danh sách quyền:
```yaml
smartfarm:
  security:
    grpc:
      require-explicit-policy: true
      authorities:
        com.htv.smartfarm.identity.v1.PlatformAuthorizationAdministration/GrantRole: identity:admin
        com.htv.smartfarm.identity.v1.IdentityAdministration/CreateUser: identity:write
        # ... khai đủ cho từng method
```
Bật `require-explicit-policy: true` để nếu quên khai method nào, service
báo lỗi lúc khởi động thay vì âm thầm mở.

## Lời thoại gợi ý
"Lỗi nguy hiểm nhất không làm chương trình sập — nó để chương trình chạy,
chỉ là chạy mở toang. Service cấp quyền cho cả hệ thống nên là nơi gác chặt
nhất, không phải nơi gác lỏng nhất."
