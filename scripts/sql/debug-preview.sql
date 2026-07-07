SET NAMES utf8mb4;
-- Step 1: 张三的 primary_unit_id
SELECT id, username, nickname, primary_unit_id FROM omni_auth.sys_user WHERE id=100;

-- Step 2: 模拟 resolveCandidatesInSameUnit (roleCode=TEAM_LEADER, unitId=101, tenantId=1)
SELECT DISTINCT u.id AS user_id, u.username, u.nickname, ou.name AS unit_name
FROM omni_auth.sys_user_role_scope urs
JOIN omni_auth.sys_role r ON urs.role_id = r.id
JOIN omni_auth.sys_user u ON urs.user_id = u.id
LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
WHERE r.role_code = 'TEAM_LEADER' AND r.tenant_id = 1 AND r.status = 1
  AND urs.unit_id = 101 AND urs.status = 1 AND u.status = 1
ORDER BY u.id ASC;

-- Step 3: 也试试 DEPT_LEADER
SELECT DISTINCT u.id AS user_id, u.username, u.nickname, ou.name AS unit_name
FROM omni_auth.sys_user_role_scope urs
JOIN omni_auth.sys_role r ON urs.role_id = r.id
JOIN omni_auth.sys_user u ON urs.user_id = u.id
LEFT JOIN omni_auth.sys_org_unit ou ON u.primary_unit_id = ou.id
WHERE r.role_code = 'DEPT_LEADER' AND r.tenant_id = 1 AND r.status = 1
  AND urs.unit_id = 101 AND urs.status = 1 AND u.status = 1
ORDER BY u.id ASC;