@echo off
rem E2E token 受控签发入口薄封装（WP-005 返修 W5-R05）：只转调同目录 run-token-fixture.ps1，
rem 不维护第二份凭证逻辑；全部输入经环境继承，退出码原样透传。
set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%run-token-fixture.ps1"
exit /b %ERRORLEVEL%
