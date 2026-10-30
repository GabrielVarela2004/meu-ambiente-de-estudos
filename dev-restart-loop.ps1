# Roda o backend ou o frontend e o reinicia se cair (ex.: falta de memoria), no lugar do PM2/supervisord.
# Uso (chamado pelo start-dev.ps1): .\dev-restart-loop.ps1 -Service backend   (ou frontend)
# Ctrl+C na janela encerra o servico e o laco. Muitas quedas seguidas (ex.: erro de compilacao) param o laco.
param(
    [Parameter(Mandatory = $true)][ValidateSet('backend', 'frontend')][string]$Service,
    [int]$MaxRestarts = 5,
    [int]$WindowMinutes = 10
)

$root = $PSScriptRoot
$crashes = New-Object System.Collections.Generic.List[datetime]

if ($Service -eq 'backend') {
    $Host.UI.RawUI.WindowTitle = 'Estudos - Backend (:8080)'
    Set-Location "$root\backend"
} else {
    $Host.UI.RawUI.WindowTitle = 'Estudos - Frontend (:5173)'
    Set-Location "$root\frontend"
}

while ($true) {
    Write-Host "[$(Get-Date -Format HH:mm:ss)] Iniciando $Service..." -ForegroundColor Cyan
    if ($Service -eq 'backend') {
        # Java limitado a 768 MB: a maquina tem pouca RAM livre.
        & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Xmx768m"
    } else {
        & npm run dev
    }
    $code = $LASTEXITCODE

    $now = Get-Date
    $crashes.Add($now)
    $recent = @($crashes | Where-Object { $_ -gt $now.AddMinutes(-$WindowMinutes) })
    if ($recent.Count -gt $MaxRestarts) {
        Write-Host "[$(Get-Date -Format HH:mm:ss)] $Service caiu $($recent.Count) vezes em $WindowMinutes min (codigo $code). Laco interrompido: veja o erro acima." -ForegroundColor Red
        break
    }
    Write-Host "[$(Get-Date -Format HH:mm:ss)] $Service terminou (codigo $code). Reiniciando em 5 s... (Ctrl+C para parar)" -ForegroundColor Yellow
    Start-Sleep -Seconds 5
}
