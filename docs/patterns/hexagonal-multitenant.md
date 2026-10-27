# Pattern: Hexagonal + DDD aggregate + multi-tenant `@TenantId`

**Nhà:** `services/farm-service`

```
farm/
  domain/            Farm (aggregate root), Barn (value object), FarmRuleViolation — không import Spring
  application/       FarmService (use case), FarmRepository (port)
  adapter/out/jpa/   FarmEntity (@TenantId), JpaFarmRepository (adapter của port), TenantFromTokenResolver
  adapter/in/rest/   FarmController
```

| Luật | Ở đâu |
|---|---|
| Mã chuồng không trùng trong farm | `Farm.addBarn` |
| Farm ARCHIVED không thêm chuồng | `Farm.addBarn` |
| Mỗi tenant chỉ thấy dữ liệu của mình | Hibernate `@TenantId` + `CurrentTenantIdentifierResolver` đọc `tenant_id` trong token |

Vì sao có interface `FarmRepository` (port)? Domain/use case không phụ thuộc JPA; test domain chạy không cần Spring.
Đây là chỗ hiếm hoi trong dự án interface một cài đặt có lý do: ranh giới hexagonal là thứ đang học.

Bằng chứng: `FarmTest` (luật aggregate), `TenantIsolationTest` (tenant B không đọc được farm của tenant A),
`FarmControllerTest` (401/403, lỗi luật → ProblemDetail 422).
