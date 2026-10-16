# Identity Tenant-Scope Design Checklist

> ⚠️ **ĐÃ HỢP NHẤT VỀ MASTER CHECKLIST (2026-10-16):**  
> Tài liệu này là bản ghi nhận đề xuất lịch sử. Toàn bộ các hạng mục cốt lõi và các đề xuất gây phá vỡ kiến trúc (pruned/rejected) đã được rà soát và hợp nhất chính thức vào [**Master Checklist Identity Service**](../../IDENTITY_BACKLOG_UPDATED.md). Vui lòng tra cứu tiến độ và kế hoạch chính thức tại file Master Checklist.

---

## 1. Kết Luận Rà Soát Quyết Định Kiến Trúc
1. **Thẩm quyền vòng đời Tenant:** Identity Service là cơ quan duy nhất quản lý tenant và membership. Sử dụng trường `sf_tenant.enabled` logic sẵn có; không tách thêm Tenant Service riêng để tránh phân mảnh.
2. **Quyền hạn Platform toàn cục (ADR-001):** Không hỗ trợ tenant-scoped permission mutation; Permission catalog thuộc platform quản lý toàn cục.
3. **Mô hình cô lập Tenancy:** Sử dụng chung Relational Database với phân vùng `tenant_id` logic. Không tách database/schema per tenant trong Identity để tránh over-engineering.
4. **Sự kiện & Watermark Invalidation:** Đồng bộ trạng thái vô hiệu hóa qua MQTT topic `events/identity/authorization-invalidated`.

---

## 2. Chi Tiết Đề Xuất Lịch Sử (Lưu Trữ Tham Khảo)

### Tenant lifecycle authority

- **Recommendation:** Identity is the lifecycle authority for tenants in the current product boundary. No separate Tenant/Organization service is currently defined as the tenant master. Identity owns the canonical tenant identifier and the lifecycle state used to allow or deny authentication and tenant-scoped authorization.
- To minimize migration and runtime impact, first map existing operations to the existing `sf_tenant.enabled` boolean and preserve its current meanings and defaults. Do not introduce a lifecycle enum, new required API, or change login/token behavior until owners approve the transition rules and compatibility plan.
- If richer lifecycle states are later required, add them compatibly: retain and consistently map `enabled` during a transition period, use an additive migration with a deterministic backfill, and release readers before writers. Do not require all services or existing tokens to upgrade at once.
- A future suspension policy should reject new authentication/token issuance and tenant-scoped authorization, and should invalidate local authorization state using existing version/event mechanisms. Session revocation and tenant decommissioning semantics need a separately approved rollout because they can terminate existing sessions.
- Prefer logical decommissioning over physical deletion to preserve references and audit history, but first inventory foreign keys, retention requirements, and existing delete behavior. Do not prohibit or change deletion paths before confirming current contracts.

### Tenancy model and isolation guarantees

- **Low-impact recommendation:** keep the existing shared Identity service and relational schema. For new tenant-owned data, use the established tenant ID and existing ownership relationships; audit current tables and queries before adding columns or constraints. Do not introduce per-tenant databases/schemas, a new database dependency, or a cross-service tenant registry absent a product/regulatory need.
- Preserve existing request, JWT, gRPC, and event contracts. Add tenant validation at existing application/repository boundaries and strengthen constraints only where the current schema and data support a safe migration. Use additive migrations and compatibility tests; do not reject existing valid requests merely because a proposed field is absent.
- **Target logical isolation:** authenticated tenant context scopes tenant-owned reads and writes; resource ownership is checked before access; lists/search/jobs and caches preserve the same tenant boundary. Platform-global operations remain explicitly platform-authorized.
