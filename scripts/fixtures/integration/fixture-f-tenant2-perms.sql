-- WP-008 F 环境：为 tenant 2002 克隆权限目录并重挂 fixture 角色授权。
-- 依据：omni-auth SysRoleMapper.selectRolesGrantingPermission 要求 r.tenant_id AND p.tenant_id 均等于用户租户；
-- 现网 sys_permission 仅 tenant_id=1（单租户），真实多租户开通必须为每个租户持有权限目录副本。
-- 仅作用于 F 隔离库 omni_auth 的 tenant 2002 行，幂等可重跑。
SET @t := 2002;

-- 1) 清理既有 tenant-2002 克隆及其授权（幂等）
DELETE rp FROM sys_role_permission rp
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.tenant_id = @t;
DELETE FROM sys_permission WHERE tenant_id = @t;

-- 2) 克隆 tenant-1 全量权限目录到 tenant 2002（按 code 复制，parent_id 对解析无影响）
INSERT INTO sys_permission
  (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by, update_by)
SELECT @t, parent_id, permission_code, permission_name, type, path, depth, sort, status, 'wp008f', 'wp008f'
  FROM sys_permission WHERE tenant_id = 1;

-- 3) 将 fixture 角色 2111-2115 的授权重挂到 tenant-2002 副本（按 permission_code 匹配）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT rp.role_id, p2.id
  FROM sys_role_permission rp
  JOIN sys_permission p1 ON p1.id = rp.permission_id AND p1.tenant_id = 1
  JOIN sys_permission p2 ON p2.tenant_id = @t AND p2.permission_code = p1.permission_code
 WHERE rp.role_id IN (2111, 2112, 2113, 2114, 2115);

-- 4) 对齐 fixture 角色的数据范围语义（CRM 行级过滤依 sys_role.data_scope）：
--    SALES_REP=SELF（仅本人）、SALES_MANAGER=DEPT_AND_BELOW、CRM_VIEWER=DEPT（同单位可见但无 pii）。
UPDATE sys_role SET data_scope='SELF'           WHERE id=2113 AND tenant_id=@t;
UPDATE sys_role SET data_scope='DEPT_AND_BELOW'  WHERE id=2114 AND tenant_id=@t;
UPDATE sys_role SET data_scope='DEPT'            WHERE id=2115 AND tenant_id=@t;

-- 5) 向 SALES_REP/SALES_MANAGER 授予 crm:pii:view（VIEWER 不授，验证脱敏）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.tenant_id=@t AND p.permission_code='crm:pii:view'
 WHERE r.id IN (2113,2114)
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id=r.id AND x.permission_id=p.id);

-- 6) 跨租户负例身份：给 tenant-1 用户 100(zhangsan) 挂 tenant-1 SALES_REP(role 22)，
--    使其能解析 crm:customer:list 但受 TenantLine 隔离看不到 tenant 2002 行。
INSERT INTO sys_user_role (user_id, role_id)
SELECT 100, 22 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id=100 AND role_id=22);
