@echo off
setlocal
cd /d "%~dp0"

if not exist ".env" (
    echo [ERROR] 未找到 .env，无法解析当前 Compose 项目。
    exit /b 1
)

echo [INFO] 停止开发栈并保留数据库等命名卷。
docker compose --profile "*" down
exit /b %errorlevel%
