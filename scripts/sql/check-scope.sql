SET NAMES utf8mb4;
USE omni_auth;
SELECT urs.id, urs.user_id, u.username, urs.role_id, r.role_code, r.role_name, urs.unit_id, ou.name as unit_name FROM sys_user_role_scope urs JOIN sys_user u ON urs.user_id = u.id JOIN sys_role r ON urs.role_id = r.id LEFT JOIN sys_org_unit ou ON urs.unit_id = ou.id WHERE urs.tenant_id = 1 AND urs.status = 1 ORDER BY urs.id;