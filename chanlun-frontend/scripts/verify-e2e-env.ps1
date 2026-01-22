# E2E Environment Verification Script (PowerShell)
# Check if E2E test environment is correctly configured

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "E2E Environment Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$allPassed = $true

# 1. Check Node.js
Write-Host "[1/6] Checking Node.js..." -ForegroundColor Yellow
$nodeVersion = node --version 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  OK Node.js version: $nodeVersion" -ForegroundColor Green
} else {
    Write-Host "  FAIL Node.js not installed" -ForegroundColor Red
    $allPassed = $false
}

# 2. Check npm dependencies
Write-Host "[2/6] Checking npm dependencies..." -ForegroundColor Yellow
$nodeModulesPath = Join-Path $PSScriptRoot "..\node_modules"
if (Test-Path $nodeModulesPath) {
    Write-Host "  OK node_modules installed" -ForegroundColor Green
} else {
    Write-Host "  FAIL node_modules not installed, run npm install" -ForegroundColor Red
    $allPassed = $false
}

# 3. Check Playwright
Write-Host "[3/6] Checking Playwright..." -ForegroundColor Yellow
Set-Location (Join-Path $PSScriptRoot "..")
$playwrightVersion = npx playwright --version 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  OK Playwright version: $playwrightVersion" -ForegroundColor Green
} else {
    Write-Host "  FAIL Playwright not installed" -ForegroundColor Red
    $allPassed = $false
}

# 4. Check Chromium browser
Write-Host "[4/6] Checking Chromium browser..." -ForegroundColor Yellow
$chromiumPath = "$env:LOCALAPPDATA\ms-playwright"
$chromiumDirs = Get-ChildItem -Path $chromiumPath -Directory -Filter "chromium-*" -ErrorAction SilentlyContinue
if ($chromiumDirs) {
    Write-Host "  OK Chromium installed: $($chromiumDirs[0].Name)" -ForegroundColor Green
} else {
    Write-Host "  FAIL Chromium not installed, run: npx playwright install chromium" -ForegroundColor Red
    $allPassed = $false
}

# 5. Check backend config file
Write-Host "[5/6] Checking backend E2E config..." -ForegroundColor Yellow
$backendConfigPath = Join-Path $PSScriptRoot "..\..\chanlun-backend\src\main\resources\application-e2e.yml"
if (Test-Path $backendConfigPath) {
    $configContent = Get-Content $backendConfigPath -Raw
    if ($configContent -match "api-mock:\s*true") {
        Write-Host "  OK application-e2e.yml has api-mock: true" -ForegroundColor Green
    } else {
        Write-Host "  FAIL application-e2e.yml missing api-mock: true" -ForegroundColor Red
        $allPassed = $false
    }
} else {
    Write-Host "  FAIL application-e2e.yml not found" -ForegroundColor Red
    $allPassed = $false
}

# 6. Check frontend E2E config file
Write-Host "[6/6] Checking frontend E2E config..." -ForegroundColor Yellow
$frontendConfigPath = Join-Path $PSScriptRoot "..\.env.e2e"
if (Test-Path $frontendConfigPath) {
    Write-Host "  OK .env.e2e exists" -ForegroundColor Green
} else {
    Write-Host "  FAIL .env.e2e not found" -ForegroundColor Red
    $allPassed = $false
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($allPassed) {
    Write-Host "OK E2E environment verification passed!" -ForegroundColor Green
    Write-Host ""
    Write-Host "To run E2E tests:" -ForegroundColor Yellow
    Write-Host "1. Start backend: .\scripts\start-e2e-backend.ps1"
    Write-Host "2. Start frontend: npm run dev"
    Write-Host "3. Run tests: npm run test:e2e"
} else {
    Write-Host "FAIL E2E environment verification failed, please fix issues above" -ForegroundColor Red
}
Write-Host "========================================" -ForegroundColor Cyan
