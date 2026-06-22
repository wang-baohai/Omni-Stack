-- DEPRECATED: 此脚本不再需要。
-- docker-compose.yml 已通过 --character-set-server=utf8mb4 参数在容器级别强制 UTF-8MB4，
-- 配合 init-all.sql 中的 SET NAMES utf8mb4 和显式 CHARSET 声明，中文不会再乱码。
-- 保留此文件用于手动修复非 Docker 环境（如本地 MySQL 安装）。
-- ============================================================
-- 修复因 Docker 初始化字符集问题导致的中文字段乱码（历史补丁）
-- ============================================================

USE omni_auth;

-- 1. 修复 sys_permission.permission_name
UPDATE sys_permission SET permission_name = '系统管理' WHERE id = 1;
UPDATE sys_permission SET permission_name = '用户管理' WHERE id = 2;
UPDATE sys_permission SET permission_name = '角色管理' WHERE id = 3;
UPDATE sys_permission SET permission_name = '权限管理' WHERE id = 4;
UPDATE sys_permission SET permission_name = '组织管理' WHERE id = 5;
UPDATE sys_permission SET permission_name = '租户管理' WHERE id = 6;
UPDATE sys_permission SET permission_name = '查看用户' WHERE id = 7;
UPDATE sys_permission SET permission_name = '创建用户' WHERE id = 8;
UPDATE sys_permission SET permission_name = '更新用户' WHERE id = 9;
UPDATE sys_permission SET permission_name = '删除用户' WHERE id = 10;
UPDATE sys_permission SET permission_name = '查看角色' WHERE id = 11;
UPDATE sys_permission SET permission_name = '创建角色' WHERE id = 12;
UPDATE sys_permission SET permission_name = '更新角色' WHERE id = 13;
UPDATE sys_permission SET permission_name = '删除角色' WHERE id = 14;
UPDATE sys_permission SET permission_name = '查看权限' WHERE id = 15;
UPDATE sys_permission SET permission_name = '创建权限' WHERE id = 16;
UPDATE sys_permission SET permission_name = '更新权限' WHERE id = 17;
UPDATE sys_permission SET permission_name = '删除权限' WHERE id = 18;
UPDATE sys_permission SET permission_name = '查看组织' WHERE id = 19;
UPDATE sys_permission SET permission_name = '创建组织' WHERE id = 20;
UPDATE sys_permission SET permission_name = '更新组织' WHERE id = 21;
UPDATE sys_permission SET permission_name = '删除组织' WHERE id = 22;
UPDATE sys_permission SET permission_name = '查看租户' WHERE id = 23;
UPDATE sys_permission SET permission_name = '创建租户' WHERE id = 24;
UPDATE sys_permission SET permission_name = '更新租户' WHERE id = 25;
UPDATE sys_permission SET permission_name = '删除租户' WHERE id = 26;
UPDATE sys_permission SET permission_name = 'OAuth2 客户端' WHERE id = 27;
UPDATE sys_permission SET permission_name = '查看客户端' WHERE id = 28;
UPDATE sys_permission SET permission_name = '创建客户端' WHERE id = 29;
UPDATE sys_permission SET permission_name = '更新客户端' WHERE id = 30;
UPDATE sys_permission SET permission_name = '删除客户端' WHERE id = 31;
UPDATE sys_permission SET permission_name = '在线用户' WHERE id = 32;
UPDATE sys_permission SET permission_name = '查看在线用户' WHERE id = 33;
UPDATE sys_permission SET permission_name = '强制下线' WHERE id = 34;
UPDATE sys_permission SET permission_name = '授权记录' WHERE id = 35;
UPDATE sys_permission SET permission_name = '查看授权记录' WHERE id = 36;
UPDATE sys_permission SET permission_name = '审计日志' WHERE id = 37;
UPDATE sys_permission SET permission_name = '查看审计日志' WHERE id = 38;
UPDATE sys_permission SET permission_name = 'XSS防护配置' WHERE id = 39;
UPDATE sys_permission SET permission_name = '查看XSS配置' WHERE id = 40;
UPDATE sys_permission SET permission_name = '更新XSS配置' WHERE id = 41;
UPDATE sys_permission SET permission_name = '创建XSS规则' WHERE id = 42;
UPDATE sys_permission SET permission_name = '删除XSS规则' WHERE id = 43;
UPDATE sys_permission SET permission_name = '基础数据' WHERE id = 50;
UPDATE sys_permission SET permission_name = '字典管理' WHERE id = 51;
UPDATE sys_permission SET permission_name = '查看字典类型' WHERE id = 52;
UPDATE sys_permission SET permission_name = '创建字典类型' WHERE id = 53;
UPDATE sys_permission SET permission_name = '更新字典类型' WHERE id = 54;
UPDATE sys_permission SET permission_name = '删除字典类型' WHERE id = 55;
UPDATE sys_permission SET permission_name = '查看字典数据' WHERE id = 56;
UPDATE sys_permission SET permission_name = '创建字典数据' WHERE id = 57;
UPDATE sys_permission SET permission_name = '更新字典数据' WHERE id = 58;
UPDATE sys_permission SET permission_name = '删除字典数据' WHERE id = 59;
UPDATE sys_permission SET permission_name = '刷新字典缓存' WHERE id = 60;
UPDATE sys_permission SET permission_name = '操作日志' WHERE id = 61;
UPDATE sys_permission SET permission_name = '查看操作日志' WHERE id = 62;

-- 2. 修复 sys_xss_blacklist_rule (rule_name + description)
UPDATE sys_xss_blacklist_rule SET rule_name = 'Script标签', description = '拦截<script>标签及其内容' WHERE id = 1;
UPDATE sys_xss_blacklist_rule SET rule_name = 'IFrame标签', description = '拦截<iframe>标签及其内容' WHERE id = 2;
UPDATE sys_xss_blacklist_rule SET rule_name = 'Object标签', description = '拦截<object>标签及其内容' WHERE id = 3;
UPDATE sys_xss_blacklist_rule SET rule_name = 'Embed标签', description = '拦截<embed>标签及其内容' WHERE id = 4;
UPDATE sys_xss_blacklist_rule SET rule_name = 'Form标签', description = '拦截<form>标签及其内容' WHERE id = 5;
UPDATE sys_xss_blacklist_rule SET rule_name = '事件处理器', description = '拦截on*事件属性(onclick等)' WHERE id = 6;
UPDATE sys_xss_blacklist_rule SET rule_name = 'JavaScript协议', description = '拦截javascript:伪协议' WHERE id = 7;
UPDATE sys_xss_blacklist_rule SET rule_name = 'VBScript协议', description = '拦截vbscript:伪协议' WHERE id = 8;
UPDATE sys_xss_blacklist_rule SET rule_name = 'Data协议', description = '拦截data:URI' WHERE id = 9;
UPDATE sys_xss_blacklist_rule SET rule_name = 'Expression', description = '拦截CSS expression表达式' WHERE id = 10;

USE omni_base;

-- 3. 修复 sys_dict_type.type_name
UPDATE sys_dict_type SET type_name = '用户性别' WHERE type_code = 'sys_user_gender';
UPDATE sys_dict_type SET type_name = '通用状态' WHERE type_code = 'sys_common_status';
UPDATE sys_dict_type SET type_name = '通知类型' WHERE type_code = 'sys_notice_type';

-- 4. 修复 sys_dict_data.dict_label
UPDATE sys_dict_data SET dict_label = '男' WHERE type_code = 'sys_user_gender' AND dict_value = '1';
UPDATE sys_dict_data SET dict_label = '女' WHERE type_code = 'sys_user_gender' AND dict_value = '2';
UPDATE sys_dict_data SET dict_label = '未知' WHERE type_code = 'sys_user_gender' AND dict_value = '0';
UPDATE sys_dict_data SET dict_label = '启用' WHERE type_code = 'sys_common_status' AND dict_value = '1';
UPDATE sys_dict_data SET dict_label = '禁用' WHERE type_code = 'sys_common_status' AND dict_value = '0';
UPDATE sys_dict_data SET dict_label = '系统通知' WHERE type_code = 'sys_notice_type' AND dict_value = '1';
UPDATE sys_dict_data SET dict_label = '业务通知' WHERE type_code = 'sys_notice_type' AND dict_value = '2';
