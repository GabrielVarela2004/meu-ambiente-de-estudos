Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Iniciando Plataforma Inteligente de Estudos com IA" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$root = $PSScriptRoot

# 1. Docker Desktop e PostgreSQL (o backend nao sobe sem o banco)
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[..] Abrindo o Docker Desktop..." -ForegroundColor Yellow
    Start-Process "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    for ($i = 0; $i -lt 36; $i++) {
        Start-Sleep -Seconds 5
        docker info *> $null
        if ($LASTEXITCODE -eq 0) { break }
    }
}
docker compose -f "$root\docker-compose.yml" up -d postgres
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Nao foi possivel subir o PostgreSQL. Verifique o Docker Desktop." -ForegroundColor Red
    exit 1
}

# 2. Backend e frontend em janelas proprias, reiniciados se cairem (dev-restart-loop.ps1).
$loop = "`"$root\dev-restart-loop.ps1`""
Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File $loop -Service backend"
Start-Process powershell -ArgumentList "-NoExit -ExecutionPolicy Bypass -File $loop -Service frontend"

# 3. Espera o backend ficar saudavel (/actuator/health) antes de abrir o navegador
Write-Host "[..] Aguardando o backend ficar pronto..." -ForegroundColor Yellow
$healthy = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $health = Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 3
        if ($health.status -eq 'UP') { $healthy = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}

if ($healthy) {
    Write-Host "[OK] Backend:  http://localhost:8080 (banco: $($health.components.db.status), IA disponivel: $($health.components.aiProviders.details.aiAvailable))" -ForegroundColor Green
} else {
    Write-Host "[!] O backend ainda nao respondeu; veja a janela 'Estudos - Backend'." -ForegroundColor Red
}
Write-Host "[OK] Frontend: http://localhost:5173" -ForegroundColor Green
Start-Process "http://localhost:5173"
