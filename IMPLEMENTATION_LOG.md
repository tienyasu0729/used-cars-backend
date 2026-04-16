## DEV Security & Deposit — Bảo mật + cọc + FE auth

### Completed

- APIs: CORS theo `app.security.cors`; rate limit POST `/api/v1/auth/login`, `/api/v1/auth/register` (Bucket4j, fail-open); webhook VNPay IPN / ZaloPay callback kiểm tra IP khi whitelist YAML có giá trị; `ErrorCode.RATE_LIMITED` + `GlobalExceptionHandler`.
- Entities: không đổi schema DB.
- Features: `JwtRoleNames` chuẩn hóa UPPERCASE; `DepositService` timeout cọc online đồng bộ config + xe RESERVED khi cọc VNPay/ZaloPay + batch tên khách (N+1); `@Valid` bổ sung cho một số body tùy chọn.
- Frontend: token chuẩn `auth_token` (đọc fallback `token`); logout/401 qua `authStore.clearAuth`; stub `tryRefreshToken`.

### Test Result

- `mvnw compile -DskipTests`: OK (local).
- API/E2E thủ công: cần chạy với DB/Redis thật theo checklist kế hoạch (login/register/deposit/payment/webhook).

### Notes

- CORS/WebSocket: list pattern rỗng → mặc định `localhost` / `127.0.0.1` / `[::1]`. Prod cần set `app.security.cors.allowed-origin-patterns`.
- Cọc online: xe chuyển RESERVED ngay khi tạo link thanh toán (khác hành vi cũ: trước đó xe vẫn AVAILABLE trên listing).
- `cancelIfExpiredOnlineDeposit`: dùng cùng config timeout với scheduler; exception khi đọc config → fallback 6 phút.

### Consistency Check

- [x] Không đổi contract JSON public cho API chính; Zalo callback thêm `return_code: -5` khi chặn IP (chỉ khi bật whitelist).
- [x] RBAC không đổi.
- [x] Không migration DB.

---

## DEV — Xóa (ẩn) hội thoại Chat

### ✅ Completed
- APIs: `DELETE /api/v1/chat/conversations/{id}` — ẩn hội thoại khỏi danh sách của user (soft delete per-user).
- Entities: `ChatParticipant` thêm field `hiddenAt` (DATETIME2 NULL).
- Features:
  - User ẩn hội thoại → `hidden_at = NOW()`, `unread_count = 0`.
  - `listConversations` bỏ qua hội thoại có `hidden_at != null`.
  - Khi có tin nhắn mới, `hidden_at` được reset về `null` → hội thoại tự hiện lại.
  - Frontend: nút xóa (icon Trash2) hiện khi hover vào hội thoại; **xác nhận bằng `ConfirmDialog`** (`@/components/ui/ConfirmDialog`) — tiêu đề "Xóa hội thoại", nội dung có tên đối phương + ghi chú hội thoại hiện lại khi có tin mới; nút **Xóa** / **Hủy** (không dùng `window.confirm`).
  - Áp dụng cho `ChatLayout` (customer + FloatingChatWidget) và `StaffChatLayout` (staff + manager page).

### 🧪 Test Result
- Compile frontend: cần kiểm tra `pnpm dev` (mở dialog xác nhận khi bấm xóa).
- Compile backend: cần chạy `mvnw compile -DskipTests`.
- **Cần chạy SQL migration trước khi test API.**

### ⚠️ Notes
- Migration script: `docs/db_design/sqlserver-add-hidden-at-chat-participants.sql` — chạy thủ công trên SQL Server.
- `init_schema.sql` đã cập nhật cột `hidden_at` trong bảng `ChatParticipants`.
- Đây là soft delete per-user, không xóa dữ liệu thật. Đối phương không bị ảnh hưởng.
- **FE (repo `used-cars`):** file `ChatLayout.tsx`, `StaffChatLayout.tsx` — state `deleteTarget` + `ConfirmDialog`; code UI nằm ngoài repo backend nhưng ghi ở đây để đồng bộ mô tả tính năng end-to-end.

### 🔒 Consistency Check
- [x] DB ↔ API mapping: `hidden_at` DATETIME2 NULL ↔ `Instant hiddenAt` ↔ `DELETE /chat/conversations/{id}`
- [x] State machine valid: hidden_at = null (hiển thị) ↔ hidden_at = timestamp (ẩn) ↔ tin nhắn mới → reset null
- [x] RBAC applied: endpoint yêu cầu `isAuthenticated()`, chỉ user tham gia hội thoại mới ẩn được
- [x] Error code correct: `CHAT_ACCESS_DENIED` khi user không tham gia hội thoại

---

## DEV — Gợi ý tìm kiếm (Search Autocomplete)

### ✅ Completed
- APIs: `GET /api/v1/vehicles/suggestions?q=...&limit=...` — trả danh sách gợi ý từ 3 nguồn (hãng/dòng xe, title xe, năm sản xuất).
- Entities: Không thay đổi schema. Tận dụng bảng `Subcategories` (ghép Category.name + Subcategory.name) và `Vehicles` (title, year).
- Features:
  - Backend: endpoint public, không cần xác thực. Validate `q >= 2 ký tự`, giới hạn `limit` tối đa 15. Prefix match ưu tiên hơn contains match (sắp xếp trong Java). Loại bỏ trùng lặp, chỉ gợi ý xe `Available` + `is_deleted = false`.
  - Frontend: component `SearchAutocomplete` dùng chung cho cả `PublicHeader` (desktop + mobile) và `HomePage` (hero search). Debounce 300ms. Dropdown nhóm theo loại (brand / vehicle / year), highlight từ khóa khớp, keyboard navigation (Arrow Up/Down, Enter, Escape), click outside đóng dropdown, ARIA attributes đầy đủ.
  - DTO mới: `SuggestionDto` (type, text).
  - Hook mới: `useSearchSuggestions` (debounce + react-query).
  - Fix lỗi compile có sẵn: thêm `GOOGLE_AUTH_FAILED` vào switch expression trong `GlobalExceptionHandler`.

### 🧪 Test Result
- API `GET /api/v1/vehicles/suggestions?q=ki`: OK — trả về gợi ý hãng Kia đúng format `{ type: "brand", text: "Kia Morning" }`.
- API `GET /api/v1/vehicles/suggestions?q=Kia`: OK — hiển thị đúng các dòng xe Kia.
- API `GET /api/v1/vehicles/suggestions?q=2022`: OK — trả về `{ type: "year", text: "2022" }`.
- API `GET /api/v1/vehicles/suggestions?q=a` (1 ký tự): OK — trả về mảng rỗng `[]`.
- Backend compile: OK (`mvnw compile`).
- Frontend TypeScript check: OK (`npx tsc --noEmit`).

### ⚠️ Notes
- Không thay đổi schema DB — không cần chạy migration.
- Prefix-first sorting được xử lý trong Java (VehicleService) thay vì SQL vì SQL Server không hỗ trợ `ORDER BY CASE` với `SELECT DISTINCT`.
- Tìm năm sản xuất: lấy tất cả distinct years rồi lọc trong Java (tránh vấn đề CAST trong JPQL/SQL Server).

### 🔒 Consistency Check
- [x] DB ↔ API mapping: Subcategories.name + Categories.name → SuggestionDto.text (type="brand"); Vehicles.title → SuggestionDto.text (type="vehicle"); Vehicles.year → SuggestionDto.text (type="year")
- [x] State machine valid: N/A (read-only endpoint)
- [x] RBAC applied: endpoint public (không cần xác thực, giống GET /api/v1/vehicles)
- [x] Error code correct: q < 2 ký tự trả mảng rỗng (HTTP 200), lỗi server trả HTTP 500
