@echo off
REM E2E 测试后端启动脚本 (Windows CMD)
REM 设置 EXCHANGE_API_MOCK=true 启用 Mock 模式

set EXCHANGE_API_MOCK=true
set SPRING_PROFILES_ACTIVE=e2e

echo Starting backend with EXCHANGE_API_MOCK=true...
echo Profile: e2e

cd /d "%~dp0..\..\chanlun-backend"
call mvn spring-boot:run -DskipTests
