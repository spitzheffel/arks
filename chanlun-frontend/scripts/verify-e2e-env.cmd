@echo off
REM E2E 环境验证脚本 (Windows CMD)
REM 检查 E2E 测试环境是否正确配置

echo ========================================
echo E2E 环境验证
echo ========================================
echo.

set ALL_PASSED=1

REM 1. 检查 Node.js
echo [1/6] 检查 Node.js...
node --version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    for /f "tokens=*" %%i in ('node --version') do echo   √ Node.js 版本: %%i
) else (
    echo   X Node.js 未安装
    set ALL_PASSED=0
)

REM 2. 检查 npm 依赖
echo [2/6] 检查 npm 依赖...
if exist "%~dp0..\node_modules" (
    echo   √ node_modules 已安装
) else (
    echo   X node_modules 未安装，请运行 npm install
    set ALL_PASSED=0
)

REM 3. 检查 Playwright
echo [3/6] 检查 Playwright...
cd /d "%~dp0.."
call npx playwright --version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    for /f "tokens=*" %%i in ('npx playwright --version') do echo   √ Playwright 版本: %%i
) else (
    echo   X Playwright 未安装
    set ALL_PASSED=0
)

REM 4. 检查 Chromium 浏览器
echo [4/6] 检查 Chromium 浏览器...
if exist "%LOCALAPPDATA%\ms-playwright\chromium-*" (
    echo   √ Chromium 已安装
) else (
    echo   X Chromium 未安装，请运行 npx playwright install chromium
    set ALL_PASSED=0
)

REM 5. 检查后端配置文件
echo [5/6] 检查后端 E2E 配置...
if exist "%~dp0..\..\chanlun-backend\src\main\resources\application-e2e.yml" (
    findstr /C:"api-mock: true" "%~dp0..\..\chanlun-backend\src\main\resources\application-e2e.yml" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo   √ application-e2e.yml 已配置 api-mock: true
    ) else (
        echo   X application-e2e.yml 未配置 api-mock: true
        set ALL_PASSED=0
    )
) else (
    echo   X application-e2e.yml 不存在
    set ALL_PASSED=0
)

REM 6. 检查前端 E2E 配置文件
echo [6/6] 检查前端 E2E 配置...
if exist "%~dp0..\.env.e2e" (
    echo   √ .env.e2e 已存在
) else (
    echo   X .env.e2e 不存在
    set ALL_PASSED=0
)

echo.
echo ========================================
if %ALL_PASSED% EQU 1 (
    echo √ E2E 环境验证通过!
    echo.
    echo 运行 E2E 测试步骤:
    echo 1. 启动后端: scripts\start-e2e-backend.cmd
    echo 2. 启动前端: npm run dev
    echo 3. 运行测试: npm run test:e2e
) else (
    echo X E2E 环境验证失败，请修复上述问题
)
echo ========================================
