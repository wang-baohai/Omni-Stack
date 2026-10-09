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
set "OMNI_PRESET=%~1"
if "%OMNI_PRESET%"=="" set "OMNI_PRESET=full"
set "OMNI_PRESET_VALID="
for %%P in (core workflow crm supply-chain full) do if "%OMNI_PRESET%"=="%%P" set "OMNI_PRESET_VALID=1"
if not defined OMNI_PRESET_VALID (
    echo [ERROR] 不支持的预设：%OMNI_PRESET%
    exit /b 1
)
echo [INFO] 启动 %OMNI_PRESET% 预设；不再需要管理员权限或 Windows 端口预留。
docker compose -p omni-stack --profile %OMNI_PRESET% up -d --build
exit /b %errorlevel%
