# Ejecutar todas las pruebas de carga

Write-Host "🚀 Ejecutando pruebas de carga con Gatling..." -ForegroundColor Cyan
Write-Host ""

# Cambiar al directorio de load-tests
Set-Location -Path "load-tests"

# Limpiar builds anteriores
Write-Host "🧹 Limpiando builds anteriores..." -ForegroundColor Yellow
mvn clean

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  1️⃣  PRUEBA BÁSICA DE CARGA" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

mvn gatling:test -Dgatling.simulationClass=simulations.BasicLoadTest

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  2️⃣  PRUEBA DE BALANCEO DE CARGA" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""

mvn gatling:test -Dgatling.simulationClass=simulations.LoadBalancerTest

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  3️⃣  PRUEBA DE ESTRÉS" -ForegroundColor Red
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "⚠️  ADVERTENCIA: Esta prueba es INTENSIVA" -ForegroundColor Yellow
Write-Host "    Puede durar hasta 10 minutos" -ForegroundColor Yellow
Write-Host ""

$confirmStress = Read-Host "¿Ejecutar prueba de estrés? (S/N)"

if ($confirmStress -eq "S" -or $confirmStress -eq "s") {
    mvn gatling:test -Dgatling.simulationClass=simulations.StressTest
} else {
    Write-Host "⏭️  Prueba de estrés omitida" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  ✅ PRUEBAS COMPLETADAS" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host ""
Write-Host "📊 Los reportes están en: target/gatling/results/" -ForegroundColor Cyan
Write-Host ""

# Abrir el último reporte generado
$latestReport = Get-ChildItem -Path "target/gatling" -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($latestReport) {
    $indexPath = Join-Path $latestReport.FullName "index.html"
    if (Test-Path $indexPath) {
        Write-Host "🌐 Abriendo reporte en el navegador..." -ForegroundColor Green
        Start-Process $indexPath
    }
}

# Volver al directorio raíz
Set-Location -Path ".."
