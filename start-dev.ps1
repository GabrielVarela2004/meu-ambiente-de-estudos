Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Iniciando Plataforma Inteligente de Estudos com IA" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

$root = $PSScriptRoot

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\backend'; .\mvnw.cmd spring-boot:run"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\frontend'; npm run dev"

Write-Host "[OK] Backend e Frontend iniciados em janelas proprias!" -ForegroundColor Green
Write-Host "[OK] Backend:  http://localhost:8080" -ForegroundColor Yellow
Write-Host "[OK] Frontend: http://localhost:5173" -ForegroundColor Yellow
Start-Process "http://localhost:5173"
