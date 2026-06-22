@echo off
setlocal enabledelayedexpansion
:: ============================================================
:: Omni-Stack 中间件 — 一键启动
:: ============================================================
:: 用法：右键 → 以管理员身份运行
::
:: 自动完成：
::   1. 启动 Docker Desktop（如未运行）
::   2. 保留 Docker 所需端口（防止 Hyper-V/WSL2 占用）
::   3. 启动所有中间件容器
::
:: 启动指定服务：start.bat mysql redis
:: ============================================================

:: --- 切换到脚本所在目录 ---
cd /d "%~dp0"

:: --- 管理员权限检查 ---
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 请右键此脚本，选择"以管理员身份运行"！
    echo.
    pause
    exit /b 1
)

:: --- 检查 Docker ---
echo [1/3] 检查 Docker...

:: 检查 docker 命令是否存在（Docker Desktop 是否已安装）
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 未检测到 Docker Desktop！
    echo.
    echo         请先下载安装 Docker Desktop：
    echo         https://www.docker.com/products/docker-desktop/
    echo.
    start https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

:: 检查 Docker 引擎是否正在运行
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo       Docker 未运行，正在启动 Docker Desktop...
    start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"

    :: 等待 Docker 就绪（最多 120 秒）
    set "count=0"
    :wait_docker
    timeout /t 2 /nobreak >nul
    set /a count+=2
    docker info >nul 2>&1
    if %errorlevel% equ 0 goto docker_ready
    if !count! lss 120 (
        echo       等待 Docker 就绪... !count!s
        goto wait_docker
    )
    echo [ERROR] Docker Desktop 启动超时，请手动检查
    echo.
    pause
    exit /b 1

    :docker_ready
    echo       Docker 已就绪
) else (
    echo       Docker 已就绪
)
echo.

:: --- 端口保护（静默执行，仅 Windows + Hyper-V/WSL2 环境生效） ---
echo [2/3] 端口保护...
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command ^
  "sc.exe query winnat *>$null; if ($LASTEXITCODE -eq 0) { sc.exe stop winnat *>$null; @(3306,6379,8080,8848,9848,9876,10909,10911,10912) | ForEach-Object { netsh int ipv4 add excludedportrange protocol=tcp startport=$_ numberofports=1 persistent=yes *>$null }; sc.exe start winnat *>$null }"
echo       完成
echo.

:: --- 启动 Docker Compose ---
echo [3/3] 启动中间件...
if "%~1"=="" (
    docker compose up -d
) else (
    docker compose up -d %*
)
echo.
pause
