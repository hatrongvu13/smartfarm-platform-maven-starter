# SmartFarm — Gợi ý Stack Frontend (kèm mô hình trang trại 3D)

> Gợi ý stack để xây FE SmartFarm, **bao gồm phần mô hình 3D trang trại** để định vị **kho** và **các khu vực trong
> kho**, gắn với việc **giao task cho nhân công tại một vị trí cụ thể**. Đây là khuyến nghị kiến trúc — không ràng buộc;
> chọn theo năng lực đội và lộ trình.

## 1. Bối cảnh & yêu cầu FE

- Backend là **gateway BFF** expose REST + **GraphQL đọc** + **WebSocket realtime** (`/ws/events`). FE là SPA gọi qua
  cổng `:8080`, đa tenant (tenant từ token), RBAC theo scope.
- Đặc thù cần: form nhiều dòng (order multi-line, warehouse-per-line), theo dõi **saga realtime**, poll job báo cáo,
  bảng dữ liệu lớn, và **một khung cảnh 3D** cho kho/khu vực.

## 2. Stack cốt lõi (khuyến nghị)

| Lớp | Lựa chọn khuyến nghị | Lý do |
|---|---|---|
| Framework | **React 18 + TypeScript + Vite** | Hệ sinh thái lớn nhất cho cả data-app lẫn 3D (react-three-fiber). Vite build nhanh. |
| Routing | **React Router** (hoặc TanStack Router) | Điều hướng theo navigation map ở FE-REQUIREMENTS. |
| Server-state | **TanStack Query** | Cache/poll/retry cho REST (rất hợp poll job báo cáo, invalidate sau mutation). |
| Client-state | **Zustand** | Nhẹ cho auth/token, farm đang chọn, trạng thái 3D. |
| Form + validate | **React Hook Form + Zod** | Zod khớp trực tiếp các DTO/validate trong FE-REQUIREMENTS (schema = nguồn chân lý FE). |
| HTTP | **Axios** (interceptor Bearer + auto-refresh + retry-once 401) | Đúng nguyên tắc A.1/A.2 của require. |
| GraphQL (đọc) | **urql** (nhẹ) hoặc Apollo | Chỉ mặt đọc; dùng khi cần lấy nhiều field 1 round-trip. |
| Realtime | **WebSocket API** thuần + lớp reconnect/backoff | Endpoint `/ws/events?token=` — không cần thư viện nặng. |
| UI kit | **Tailwind CSS + shadcn/ui** (hoặc MUI) | Dựng nhanh, nhất quán; dark mode dễ. |
| Bảng dữ liệu | **TanStack Table** | List task/order/job lớn, sort/filter/paginate. |
| i18n | **i18next** | VI/EN — dữ liệu bilingual nên tách chuỗi ngay từ đầu. |
| Auth token | access token trong memory + refresh theo timer | Theo A.1; tránh XSS lộ token dài hạn. |

## 3. Phần 3D trang trại (kho + khu vực + giao việc theo vị trí)

Mục tiêu: hiển thị **sơ đồ 3D** trang trại/nhà kho; cho phép **đặt/chỉnh vị trí kho và các khu (zone/bin) trong kho**;
khi **giao task** thì gắn task vào một vị trí, nhân công thấy được "đi tới đâu, lấy gì ở khu nào".

### 3.1 Thư viện 3D

| Nhu cầu | Khuyến nghị | Ghi chú |
|---|---|---|
| Render 3D trong React | **three.js + @react-three/fiber (R3F)** | R3F cho phép mô tả scene bằng JSX, khớp React state — nên chọn thay vì three.js thuần. |
| Helpers/controls | **@react-three/drei** | OrbitControls, GLTF loader, Html-in-3D (label khu kho), gizmo, bounds. |
| Animation | **GSAP** (camera fly-to, highlight) + **react-spring** (tùy chọn cho state-driven) | GSAP mạnh cho timeline camera/định tuyến; dùng cho hiệu ứng "bay tới kho khi nhận task". |
| Vật lý (nếu cần va chạm/kéo-thả 3D) | **@react-three/rapier** | Chỉ khi cần kéo-thả có ràng buộc; v2 có thể bỏ qua ban đầu. |
| Model asset | **GLTF/GLB** (Blender export) | Định dạng chuẩn web; nén bằng Draco. |
| Post-processing (tùy chọn) | **@react-three/postprocessing** | Outline highlight khu được chọn. |

> **three.js + GSAP** bạn đã nghĩ tới là hợp lý; bổ sung **R3F + drei** để tích hợp React sạch sẽ thay vì quản lý
> scene thủ công. GSAP lo animation camera/highlight; R3F lo scene-graph gắn state.

### 3.2 Mô hình dữ liệu vị trí (đề xuất — CẦN backend v2 hỗ trợ)

Hiện backend v1 **chưa có** toạ độ/không gian cho warehouse/zone (chỉ có `warehouseId`, `barnId`, `batchId` dạng
chuỗi). Để phần 3D "định vị" thật sự, cần thêm ở v2 (xem BACKLOG-v2):

```ts
// FE model (khớp với API v2 dự kiến)
interface Farm3DLayout {
  farmId: string;
  scene: { modelUrl?: string; width: number; depth: number; };   // GLB nền hoặc grid
  warehouses: Warehouse3D[];
}
interface Warehouse3D {
  warehouseId: string; name: string;
  position: { x: number; y: number; z: number };                 // vị trí trong scene
  rotationY?: number;
  zones: Zone3D[];                                               // khu/bin trong kho
}
interface Zone3D {
  zoneId: string; label: string;                                 // vd "Kệ A1", "Khu lạnh"
  position: { x: number; y: number; z: number };
  itemIds?: string[];                                           // hàng lưu ở khu này
}
// Task gắn vị trí (v2): thêm zoneId/warehouseId vào task để nhân công định tuyến
interface TaskLocationRef { taskId: string; warehouseId?: string; zoneId?: string; }
```

### 3.3 Luồng "giao task theo vị trí" (3D)

1. Màn **Sơ đồ kho 3D**: load `Farm3DLayout` (v2 API) → render warehouses là khối 3D, zones là marker/kệ bên trong.
2. Chọn một **zone** → panel hiện item lưu ở đó + nút "Giao task tại đây".
3. Giao task → mở form assign (tái dùng C.3) **kèm** `warehouseId`/`zoneId` đã chọn → `POST tasks` + (v2) gắn vị trí.
4. Nhân công (mobile/desktop): mở task → **camera GSAP fly-to** khu tương ứng, highlight outline khu cần tới.
5. Khi order saga reserve/commit theo `warehouseId` từng dòng → 3D có thể **highlight** kho đang được trừ tồn.

### 3.4 Hiệu năng 3D
- Lazy-load scene 3D (route riêng, `React.lazy` + Suspense) để không nặng bundle chính.
- Instancing cho nhiều zone/kệ giống nhau; Draco-compress GLB; giới hạn draw calls.
- Tách 3D thành package/route riêng; phần data-app (task/order/report) không phụ thuộc 3D để load nhanh.

## 4. Cấu trúc dự án FE đề xuất

```
src/
  app/            router, providers (QueryClient, Auth, i18n)
  api/            axios client + interceptors; endpoint modules (auth, tasks, orders, reports, admin)
  features/
    auth/  dashboard/  tasks/  schedules/  animals/  inventory/  orders/  reports/  admin/
  realtime/       websocket client + event dispatch
  farm3d/         R3F scene, warehouse/zone components, GSAP camera controller   (lazy route)
  components/     UI kit wrappers (shadcn), DataTable, forms
  lib/            zod schemas (khớp DTO require), formatters (money/epoch), rbac guards
  store/          zustand (auth, selected farm, 3d selection)
```

## 5. Lộ trình FE
- **FE-M1 (khớp v1 backend)**: auth + 8 màn hình data (C.1–C.9), realtime WS, RBAC hiding. Chưa cần 3D.
- **FE-M2 (3D nền)**: sơ đồ kho 3D read-only từ layout tĩnh (mock hoặc v2 API), camera GSAP, highlight zone.
- **FE-M3 (3D tương tác)**: đặt/chỉnh vị trí kho/zone, gắn task theo vị trí, định tuyến nhân công — **phụ thuộc API v2**
  (toạ độ warehouse/zone + task-location). Xem BACKLOG-v2.
