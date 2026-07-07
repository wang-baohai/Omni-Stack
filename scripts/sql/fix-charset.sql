SET NAMES utf8mb4;
USE omni_auth;
UPDATE sys_role SET role_name='普通员工' WHERE id=10;
UPDATE sys_role SET role_name='工作组组长' WHERE id=11;
UPDATE sys_role SET role_name='部门领导' WHERE id=12;
UPDATE sys_org_unit SET name='技术研发部' WHERE id=100;
UPDATE sys_org_unit SET name='后端1组' WHERE id=101;
UPDATE sys_org_unit SET name='架构1组' WHERE id=102;
UPDATE sys_org_unit SET name='人事部' WHERE id=200;