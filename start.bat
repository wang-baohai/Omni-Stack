@echo off
setlocal
cd /d "%~dp0"

if not exist ".env" (
    echo [ERROR] 未找到 .env，请先复制 .env.example 并填写本地 Secret。
    exit /b 1
)

where docker >nul 2>&1 || (
    echo [ERROR] 未检测到 Docker Desktop。
    exit /b 1
)
docker info >nul 2>&1 || (
    echo [ERROR] Docker 引擎未运行，请先启动 Docker Desktop。
    exit /b 1
)
where npm >nul 2>&1 || (
    echo [ERROR] 未检测到 Node.js/npm，要求 Node.js 22.12.0 或更高版本。
    exit /b 1
)

if not exist "tools\omni-cli\node_modules" (
    echo [INFO] 首次运行，正在安装 Omni CLI 依赖...
    call npm --prefix tools\omni-cli ci || exit /b 1
)

set "OMNI_PRESET=%~1"
if "%OMNI_PRESET%"=="" set "OMNI_PRESET=full"
echo [INFO] 启动 %OMNI_PRESET% 预设；不再需要管理员权限或 Windows 端口预留。
call npm --prefix tools\omni-cli run dev -- dev up --preset %OMNI_PRESET% --build
exit /b %errorlevel%
