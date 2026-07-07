SET NAMES utf8mb4;
USE omni_auth;
SELECT id, parent_id, name, type, unit_code FROM sys_org_unit WHERE tenant_id=1 AND status=1 ORDER BY id;
SELECT u.username, u.primary_unit_id, o.name as unit_name FROM sys_user u LEFT JOIN sys_org_unit o ON u.primary_unit_id=o.id WHERE u.tenant_id=1 AND u.status=1 ORDER BY u.id;