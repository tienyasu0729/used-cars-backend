# used-cars-backend

## Redis local bang Docker

Backend local dung Redis tai `localhost:6379` theo `src/main/resources/application.yml`.

Tao va chay Redis container lan dau:

```powershell
docker run -d --name used-cars-redis -p 6379:6379 redis:7-alpine
```

Kiem tra Redis da chay:

```powershell
docker exec used-cars-redis redis-cli ping
```

Ket qua dung:

```text
PONG
```

Lenh dung hang ngay:

```powershell
docker start used-cars-redis
docker stop used-cars-redis
docker logs used-cars-redis
docker exec -it used-cars-redis redis-cli
```

Neu container da ton tai nhung dang dung, chi can chay:

```powershell
docker start used-cars-redis
```

## Hop dong lai thu (PDF)

PDF hop dong lai thu dung cung luong **DOCX template → PDF** nhu hop dong tra gop (`InstallmentContractPdfConverter`), khong con sinh bang OpenPDF/Helvetica.

- Template: `src/main/resources/templates/booking/mau-hd-lai-thu-xe.docx`
- Tao lai template tu mau tra gop: `python scripts/build-booking-contract-template.py`
- Cau hinh thong tin Ben A: `app.installment.contract.seller` trong `application.yml`

## Chay backend local

Dam bao SQL Server dang chay o `localhost:1433` va Redis dang chay o `localhost:6379`, sau do:

```powershell
.\scripts\run-backend-check-sql.ps1
```

Script nay se chay backend truc tiep bang cau hinh mac dinh.
Khong con dung `application-local.yml`. Toan bo cau hinh local hien nam trong `src/main/resources/application.yml`, bao gom `app.pricing.*` de goi AI pricing noi bo.

## SMS / OTP local (Android gateway)

Backend gui OTP bang cach ghi ban ghi `PENDING` vao bang `sms_messages`. App Android poll `GET /api/sms/pending`, gui SMS qua SIM, roi `POST /api/sms/confirm`.

### 1. Cau hinh backend (`application.yml`)

Da co san:

```yaml
app:
  sms:
    enabled: true
    require-https: false   # bat buoc false khi Android goi http://IP-LAN:8080
```

**Khong** chay local voi profile `prod` — `application-prod.yml` dat `require-https: true`, Android HTTP se bi 403.

### 2. Redis + SQL

```powershell
docker start used-cars-redis   # hoac de script run-backend-check-sql.ps1 tu khoi dong
.\scripts\run-backend-check-sql.ps1
```

Dam bao `spring.datasource` trong `application.yml` trung database ban se insert device key.

### 3. Device key (SQL)

Chay tren **cung DB** backend dang dung:

```powershell
sqlcmd -S localhost -d usedCars -U sa -P "123456" -i .\scripts\insert-device-key.sql
```

Neu backend dung DB remote (vi du `161.248.146.56`), doi `-S` cho dung host.

Device key mac dinh: `a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d`

### 4. Lay IP LAN cua may dev

```powershell
ipconfig
# Vi du: IPv4 Address = 192.168.1.42
```

### 5. Cau hinh app Android

| Trường | Local | Prod |
|--------|-------|------|
| Base URL | `http://192.168.1.42:8080` | `https://api.otocudanang.store` |
| Device Key | `a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d` | key da insert tren prod DB |
| Header | `X-Device-Key: <device_key>` | giong local |

**Lu y:** Khong dung `localhost` hoac `127.0.0.1` tren dien thoai — do la chinh dien thoai, khong phai may dev.

Dien thoai va may dev phai cung Wi-Fi. Mo Windows Firewall cho port 8080 neu can.

### 6. Kiem tra gateway (tu may dev hoac dien thoai cung mang)

```powershell
curl.exe -H "X-Device-Key: a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d" http://192.168.1.42:8080/api/sms/pending
```

Ket qua dung: JSON `{"success":true,"data":[...]}` (co the rong neu chua co OTP).

### 7. Kiem tra OTP end-to-end

1. Frontend: `npm run dev` — `.env` co `VITE_API_BASE_URL=http://localhost:8080/api/v1`
2. Goi OTP (dang ky / dat coc / ...) tu trinh duyet tren may dev
3. Xem log backend: `OtpService` tao `sms_messages` status `PENDING`
4. Android poll va gui SMS
5. Nhap ma OTP tren frontend

### 8. Ngrok (tuy chon)

Script `run-backend-check-sql.ps1` co the khoi dong ngrok. Neu Android dung URL ngrok **https**, co the dat `require-https: true`; neu dung **http** LAN thi giu `require-https: false`.
