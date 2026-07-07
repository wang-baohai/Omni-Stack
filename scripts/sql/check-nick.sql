SET NAMES utf8mb4;
USE omni_auth;
SELECT id, username, nickname FROM sys_user WHERE tenant_id=1 AND status=1 ORDER BY id;