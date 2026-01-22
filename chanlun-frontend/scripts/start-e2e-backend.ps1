# E2E 测试后端启动脚本 (PowerShell)
# 设置 EXCHANGE_API_MOCK=true 启用 Mock 模式

$env:EXCHANGE_API_MOCK = "true"
$env:SPRING_PROFILES_ACTIVE = "e2e"

Write-Host "Starting backend with EXCHANGE_API_MOCK=true..."
Write-Host "Profile: e2e"

Set-Location -Path "..\chanlun-backend"
& mvn spring-boot:run -DskipTests
