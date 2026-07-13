@echo off
setlocal enabledelayedexpansion
:: ============================================================
:: Omni-Stack 全家桶 — 一键启动
:: ============================================================
:: 包含：中间件 + 后端微服务 + 前端
:: 用法：右键 → 以管理员身份运行
::
:: 自动完成：
::   1. 启动 Docker Desktop（如未运行）
::   2. 保留 Docker 所需端口（防止 Hyper-V/WSL2 占用）
::   3. 构建并启动全部容器
::
:: 启动指定服务：start.bat mysql redis
:: ============================================================

:: --- 切换到脚本所在目录 ---
cd /d "%~dp0"

:: --- 检查服务间共享密钥配置 ---
if not exist ".env" (
    echo [ERROR] 未找到 .env，请先复制 .env.example 为 .env，
    echo         并将 OMNI_INTERNAL_API_TOKEN 替换为随机密钥。
    echo.
    pause
    exit /b 1
)

:: --- 管理员权限检查 ---
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 请右键此脚本，选择"以管理员身份运行"！
    echo.
    pause
    exit /b 1
)

:: --- 检查 Docker ---
echo [1/5] 检查 Docker...

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
if %errorlevel% equ 0 (
    echo       Docker 已就绪
    echo.
    goto start_port
)

:: --- Docker 未运行，尝试启动 ---
echo       Docker 未运行，正在启动 Docker Desktop...

:: 检查 Docker Desktop 路径
if not exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" (
    echo [ERROR] 未找到 Docker Desktop！
    echo         请确认 Docker Desktop 已安装到默认路径。
    echo.
    pause
    exit /b 1
)

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
echo.

:start_port

:: --- 端口保护（静默执行，仅 Windows + Hyper-V/WSL2 环境生效） ---
echo [2/5] 端口保护...
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command ^
  "sc.exe query winnat *>$null; if ($LASTEXITCODE -eq 0) { sc.exe stop winnat *>$null; @(3000,3306,6379,8080,8100,8101,8102,8103,8104,8848,9848,19876,10909,10911,10912,18080) | ForEach-Object { netsh int ipv4 add excludedportrange protocol=tcp startport=$_ numberofports=1 persistent=yes *>$null }; sc.exe start winnat *>$null }"
echo       完成
echo.

:: --- 拉取中间件镜像 ---
echo [3/5] 拉取中间件镜像（首次使用需下载，请耐心等待）...
docker compose pull mysql redis nacos rocketmq-namesrv rocketmq-broker xxl-job-admin 2>&1
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] 镜像拉取失败！常见原因：
    echo.
    echo         1. 网络连接问题（国内用户建议配置 Docker 镜像加速）
    echo            打开 Docker Desktop ^> Settings ^> Docker Engine，添加：
    echo            "registry-mirrors": ["https://docker.1ms.run"]
    echo.
    echo         2. Docker Hub 无法访问
    echo            尝试手动拉取：docker pull xuxueli/xxl-job-admin:3.3.1
    echo.
    echo         3. 磁盘空间不足
    echo.
    goto done
)
echo       镜像就绪
echo.

:: --- 构建应用镜像 ---
echo [4/5] 构建应用镜像（首次构建约 5-10 分钟）...
docker compose build omni-auth omni-base omni-workflow omni-crm omni-gateway omni-frontend 2>&1
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] 构建失败！请检查以上错误信息。
    echo         常见原因：JDK/Node 镜像下载失败、Maven 依赖下载超时。
    echo.
    goto done
)
echo       构建完成
echo.

:: --- 启动全部容器 ---
echo [5/5] 启动全家桶...
if "%~1"=="" (
    docker compose up -d 2>&1
) else (
    docker compose up -d %* 2>&1
)
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] 启动失败！请检查以上错误信息。
    echo         运行 docker compose logs 查看详细日志。
    echo.
) else (
    echo.
    echo ==================================================
    echo   Omni-Stack 全家桶启动完成！
    echo.
    echo   -- 中间件 --
    echo   MySQL:        localhost:3306       root/root
    echo   Redis:        localhost:6379
    echo   Nacos:        http://localhost:8080
    echo   RocketMQ:     localhost:19876
    echo   XXL-JOB:      http://localhost:18080  admin/123456
    echo.
    echo   -- 应用 --
    echo   前端:         http://localhost:3000
    echo   Auth:         http://localhost:8100
    echo   Base:         http://localhost:8101
    echo   Gateway:      http://localhost:8102
    echo   Workflow:     http://localhost:8103
    echo   CRM:          http://localhost:8104
    echo.
    echo   默认账号:     admin / admin123
    echo ==================================================
)
echo.

:done
pause
