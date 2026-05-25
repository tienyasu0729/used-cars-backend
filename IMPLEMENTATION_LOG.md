# IMPLEMENTATION LOG — BanXeOTo Đà Nẵng Backend

## DEV — Google first-login profile onboarding

### ✅ Completed
- DB: `Users.profile_completion_required` (Flyway `V5__add_profile_completion_required.sql`).
- APIs: `UserProfileDto.profileCompletionRequired`; `ErrorCode.PROFILE_COMPLETION_REQUIRED` (400).
- Features:
  - Google user mới: `profileCompletionRequired=true` sau `POST /auth/google`.
  - User đã có: `ProfileCompletionSupport.refreshProfileCompletionFlag` sau link Google / `PUT /users/me`.
  - Chặn `POST` booking (customer) và `POST/PUT` installment application khi hồ sơ thiếu SĐT hoặc họ tên.
  - Frontend: redirect `/dashboard/profile?onboarding=1`, banner bỏ qua, popup gate đặt lịch / trả góp.

### 🧪 Test Result
- `AuthIntegrationTest.googleFirstCustomer_profileCompletionRequiredUntilPhoneSaved`
- `AuthIntegrationTest.incompleteProfile_assertCustomerProfileCompleteThrows`

### Manual E2E
1. Google login email mới → profile onboarding, không về home.
2. Bỏ qua → đặt lịch / trả góp → popup.
3. Lưu SĐT + họ tên → đặt lịch OK.

---

## DEV — Google dual password login (profile flags + UX)

### ✅ Completed
- APIs: không endpoint mới; mở rộng `UserProfileDto` với `hasPassword`, `googleLinked` (login, Google login, GET `/users/me`).
- Entities: không đổi schema DB.
- Features:
  - `AuthService.buildLoginProfile` + `UserService.getMeProfile` map cờ profile.
  - Email quên mật khẩu: copy “Đặt mật khẩu đăng nhập” khi `passwordHash` null (Google-first).
  - Javadoc `requestPasswordReset` / `resetPassword` mô tả dual login Google.
  - Frontend: `ForgotPasswordPage`, `SecurityPage`, `LoginForm`, `auth.types`.
  - `AuthIntegrationTest.googleOnlyUser_canSetPasswordViaForgotResetThenLogin`.

### 🧪 Test Result
- `AuthIntegrationTest.googleOnlyUser_canSetPasswordViaForgotResetThenLogin`: chạy qua Maven test (profile `test`).
- Manual E2E (SMTP + Google OAuth): xem checklist bên dưới.

### ⚠️ Notes
- `forgot-password` vẫn trả 200 khi SMTP chưa cấu hình; email không gửi được (log warn).
- Gmail **535 BadCredentials**: OAuth login ≠ SMTP; App Password hết hạn/sai → tạo mới, ghi `scripts/mail-env.local.ps1` (xem `mail-env.local.example.ps1`) hoặc `SPRING_MAIL_PASSWORD` khi chạy backend.
- Sau `reset-password`, `authProvider` giữ `google`, `providerId` không đổi — dual login email + Google.
- Không có `POST /auth/set-password` khi đã đăng nhập (ngoài phạm vi).

### 🔒 Consistency Check
- [x] DB ↔ API mapping (không đổi cột; cờ suy từ `password_hash`, `provider_id`)
- [x] State machine valid (N/A)
- [x] RBAC không đổi
- [x] Error code correct (giữ nguyên)

### Manual E2E checklist (trước khi đóng DEV trên staging)
1. Đăng nhập Google (`VITE_GOOGLE_CLIENT_ID` + `app.google.client-id`).
2. Forgot-password cùng email Google → mở link email → đặt MK (`spring.mail.*`, `frontendBaseUrl`).
3. Login email + password → OK.
4. Login Google lại → OK.
5. Network: `hasPassword` / `googleLinked` trong login và `GET /users/me`.
