param(
    [string]$SqlHost            = "localhost",
    [int]   $SqlPort            = 1433,
    [string]$DatabaseName       = "usedCars",
    [string]$SqlUser            = "sa",
    [string]$SqlPassword        = "123456",
    [string]$RedisHost          = "localhost",
    [int]   $RedisPort          = 6379,
    [string]$RedisContainerName = "redis",
    [string]$RedisImage         = "redis:7-alpine"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Write-Step($text) {
    Write-Host ""
    Write-Host "--------------------------------------------" -ForegroundColor DarkCyan
    Write-Host "  $text" -ForegroundColor DarkCyan
    Write-Host "--------------------------------------------" -ForegroundColor DarkCyan
}

# Helper: cho den khi TCP port mo, toi da $TimeoutSec giay
function Wait-TcpPort {
    param(
        [string]$HostName,
        [int]   $Port,
        [string]$ServiceName,
        [int]   $TimeoutSec = 30
    )
    Write-Host "Waiting for $ServiceName at ${HostName}:${Port} ..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $tcp = Test-NetConnection $HostName -Port $Port -WarningAction SilentlyContinue
        if ($tcp.TcpTestSucceeded) {
            Write-Host "$ServiceName is ready." -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "$ServiceName did not become ready within ${TimeoutSec}s." -ForegroundColor Red
    return $false
}

# Helper: Redis san sang khi redis-cli ping trong container tra ve PONG
function Wait-RedisReady {
    param(
        [string]$ContainerName,
        [string]$HostName,
        [int]   $Port,
        [int]   $TimeoutSec = 20
    )

    Write-Host "Waiting for Redis readiness (container ping) ..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $pong = (docker exec $ContainerName redis-cli ping 2>$null)
        $pong = "$pong".Trim()
        if ($LASTEXITCODE -eq 0 -and $pong -eq "PONG") {
            Write-Host "Redis is ready (PONG)." -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
    }

    Write-Host "Redis container ping did not return PONG within ${TimeoutSec}s." -ForegroundColor Yellow
    Write-Host "Falling back to TCP check at ${HostName}:${Port} ..." -ForegroundColor Yellow
    return (Wait-TcpPort -HostName $HostName -Port $Port -ServiceName "Redis" -TimeoutSec 10)
}

# ============================================================
# BUOC 1 - DAM BAO DOCKER DESKTOP DANG CHAY
# ============================================================
Write-Step "STEP 1 - Docker Desktop"

# 1a. Kiem tra Docker CLI co duoc cai khong
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker CLI not found. Please install Docker Desktop and try again." -ForegroundColor Red
    exit 1
}

# 1b. Kiem tra Docker daemon co dang chay khong
# Dung $ErrorActionPreference tam thoi la SilentlyContinue de docker info
# khong nem exception khi daemon chua chay
$dockerReady = $false
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
& docker info 2>$null | Out-Null
$dockerExitCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap

if ($dockerExitCode -eq 0) {
    $dockerReady = $true
    Write-Host "Docker Desktop is already running." -ForegroundColor Green
}

if (-not $dockerReady) {
    Write-Host "Docker daemon is not running. Starting Docker Desktop ..." -ForegroundColor Yellow

    # Tim Docker Desktop.exe
    $dockerDesktopPaths = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Docker\Docker Desktop.exe"
    )
    $dockerExe = $dockerDesktopPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

    if (-not $dockerExe) {
        Write-Host "Cannot find Docker Desktop executable. Please start Docker Desktop manually and rerun." -ForegroundColor Red
        exit 1
    }

    Start-Process -FilePath $dockerExe

    # Cho Docker daemon san sang (toi da 120 giay)
    Write-Host "Waiting for Docker daemon to be ready (up to 120s) ..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $ErrorActionPreference = "SilentlyContinue"
        & docker info 2>$null | Out-Null
        $loopExitCode = $LASTEXITCODE
        $ErrorActionPreference = "Stop"
        if ($loopExitCode -eq 0) {
            $dockerReady = $true
            break
        }
        Write-Host "  Still waiting ..." -ForegroundColor DarkGray
    }

    if (-not $dockerReady) {
        Write-Host "Docker Desktop did not become ready within 120s. Please start it manually and rerun." -ForegroundColor Red
        exit 1
    }

    Write-Host "Docker Desktop is ready." -ForegroundColor Green
}

# ============================================================
# BUOC 2 - DAM BAO REDIS DANG CHAY (qua Docker)
# ============================================================
Write-Step "STEP 2 - Redis"

# Kiem tra trang thai container (trim de bo newline thua)
# Dung single-quote cho --format de tranh PowerShell expand {{ }}
$containerStatus = (docker inspect --format '{{.State.Status}}' $RedisContainerName 2>&1)
$containerStatus = "$containerStatus".Trim()

if ($LASTEXITCODE -ne 0) {
    # Container chua ton tai -> tao moi
    Write-Host "Container '$RedisContainerName' not found. Creating ..." -ForegroundColor Yellow
    docker run -d --name $RedisContainerName -p "${RedisPort}:6379" $RedisImage | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to create Redis container." -ForegroundColor Red
        exit 1
    }
    Write-Host "Redis container created." -ForegroundColor Green
} elseif ($containerStatus -ne "running") {
    # Container da co nhung dang dung -> start lai
    Write-Host "Container '$RedisContainerName' is '$containerStatus'. Starting ..." -ForegroundColor Yellow
    docker start $RedisContainerName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to start Redis container." -ForegroundColor Red
        exit 1
    }
    Write-Host "Redis container started." -ForegroundColor Green
} else {
    Write-Host "Redis container '$RedisContainerName' is already running." -ForegroundColor Green
}

if (-not (Wait-RedisReady -ContainerName $RedisContainerName -HostName $RedisHost -Port $RedisPort -TimeoutSec 20)) {
    exit 1
}

# ============================================================
# BUOC 3 - DAM BAO SQL SERVER DANG CHAY
# ============================================================
Write-Step "STEP 3 - SQL Server"

# Dung TcpClient thay vi Test-NetConnection de tranh verbose output lam ro man hinh
$tcpReachable = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect($SqlHost, $SqlPort)
    $client.Close()
    $tcpReachable = $true
} catch { }

if (-not $tcpReachable) {
    Write-Host "SQL Server not reachable. Attempting to start Windows service ..." -ForegroundColor Yellow

    # Tim service SQL Server (MSSQLSERVER = default instance, hoac MSSQL$<ten>)
    # Dung single-quote de tranh PowerShell expand bien $* trong pattern
    $sqlService = Get-Service -Name "MSSQLSERVER" -ErrorAction SilentlyContinue
    if (-not $sqlService) {
        $sqlService = Get-Service | Where-Object { $_.Name -like 'MSSQL$*' } | Select-Object -First 1
    }

    if ($sqlService) {
        Write-Host "Found SQL Server service: '$($sqlService.Name)'. Starting ..." -ForegroundColor Yellow
        Start-Service -Name $sqlService.Name
        if (-not (Wait-TcpPort -HostName $SqlHost -Port $SqlPort -ServiceName "SQL Server" -TimeoutSec 60)) {
            exit 1
        }
    } else {
        Write-Host "Could not find a SQL Server Windows service." -ForegroundColor Red
        Write-Host "Please start SQL Server manually (TCP/IP on port $SqlPort) and rerun this script."
        exit 1
    }
} else {
    Write-Host "SQL Server is already reachable at ${SqlHost}:${SqlPort}." -ForegroundColor Green
}

# ============================================================
# BUOC 4 - KHOI DONG NGROK
# ============================================================
Write-Step "STEP 4 - Starting ngrok"

$ngrokExe = "C:\ngrok\ngrok.exe"

if (-not (Test-Path $ngrokExe)) {
    Write-Host "ngrok.exe not found at $ngrokExe. Skipping ngrok." -ForegroundColor Yellow
} else {
    $ngrokRunning = Get-Process -Name "ngrok" -ErrorAction SilentlyContinue
    if ($ngrokRunning) {
        Write-Host "ngrok is already running (PID $($ngrokRunning.Id))." -ForegroundColor Green
    } else {
        Start-Process -FilePath "powershell.exe" `
            -ArgumentList "-NoExit", "-Command", "Set-Location 'C:\ngrok'; & .\ngrok.exe http 8080" `
            -WindowStyle Normal
        Write-Host "ngrok started in a new window -> http://localhost:4040 to see tunnel URL." -ForegroundColor Green
    }
}

# ============================================================
# BUOC 5 - KHOI DONG BACKEND
# (Migration duoc Flyway tu dong xu ly khi Spring Boot khoi dong)
# ============================================================
Write-Step "STEP 5 - Starting Spring Boot backend"

& .\mvnw.cmd spring-boot:run
