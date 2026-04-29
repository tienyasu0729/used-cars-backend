param(
    [string]$SqlHost = "localhost",
    [int]$SqlPort = 1433,
    [string]$DatabaseName = "usedCars",
    [string]$SqlUser = "sa",
    [string]$SqlPassword = "123456"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "Checking SQL Server TCP endpoint ${SqlHost}:${SqlPort} ..."
$tcp = Test-NetConnection $SqlHost -Port $SqlPort -WarningAction SilentlyContinue

if (-not $tcp.TcpTestSucceeded) {
    Write-Host ""
    Write-Host "SQL Server is not reachable at ${SqlHost}:${SqlPort}." -ForegroundColor Red
    Write-Host "Start SQL Server, enable TCP/IP, and make sure the instance listens on port $SqlPort."
    Write-Host "The backend datasource points to database '$DatabaseName'."
    Write-Host ""
    Write-Host "If the database does not exist, create it in SQL Server:"
    Write-Host "  CREATE DATABASE [$DatabaseName];"
    exit 1
}

Write-Host "SQL Server port is reachable. Starting backend ..."
if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
    Write-Host ""
    Write-Host "sqlcmd is not available. Install SQL Server command line tools or run database scripts manually." -ForegroundColor Red
    exit 1
}

$server = "$SqlHost,$SqlPort"
$migration = Join-Path $repoRoot "src\main\resources\db\migration\V20260428__create_installment_tables.sql"

Write-Host "Ensuring database '$DatabaseName' exists ..."
& sqlcmd -S $server -U $SqlUser -P $SqlPassword -Q "IF DB_ID(N'$DatabaseName') IS NULL CREATE DATABASE [$DatabaseName];" -b
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (Test-Path $migration) {
    Write-Host "Ensuring installment schema exists ..."
    & sqlcmd -S $server -U $SqlUser -P $SqlPassword -d $DatabaseName -i $migration -b
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& .\mvnw.cmd spring-boot:run
