SET NAMES utf8mb4;
USE omni_auth;
SELECT id, role_code, role_name FROM sys_role WHERE id IN (10,11,12);
SELECT id, name, unit_code FROM sys_org_unit WHERE id IN (100,101,102,200);