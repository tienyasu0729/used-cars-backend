# used-cars-backend

## Redis local bang Docker

Backend local dung Redis tai `localhost:6379` theo `src/main/resources/application-local.yml`.

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

## Chay backend local

Dam bao SQL Server dang chay o `localhost:1433` va Redis dang chay o `localhost:6379`, sau do:

```powershell
.\scripts\run-backend-check-sql.ps1
```
