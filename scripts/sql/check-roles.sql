SET NAMES utf8mb4;
USE omni_auth;
SELECT id, role_code, role_name FROM sys_role WHERE tenant_id=1 AND status=1 ORDER BY id;