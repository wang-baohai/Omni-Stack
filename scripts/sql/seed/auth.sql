-- Omni-Stack 正式幂等种子；由 09a29fe 基线数据机械提取并改为 INSERT IGNORE。
-- 结构由 Liquibase YAML 管理；本文件禁止包含 DDL、账号、授权或存储过程。

INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, domain, contact_name, contact_phone, status, create_by)
VALUES (1, 'default', 'Default Tenant', NULL, 'admin', NULL, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, path, depth, sort, status, create_by)
VALUES (1, 1, 0, 'Default Tenant', 'ORG', '/1/', 1, 0, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, email, phone, gender, primary_unit_id, status, create_by)
VALUES (1, 1, 'admin', '$2b$10$QjkPz8OnRoNOXTrsj./ov.nDZxK.KvsAZdjzgb1YgWSKKprOVxfIW', 'Administrator', NULL, NULL, 0, 1, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (1, 1, 'SUPER_ADMIN', 'Super Administrator', 'ALL', 0, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (2, 1, 'USER', 'Default User', 'SELF', 99, 1, 'system');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (1, 1, 1);

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (1,  1, 0, 'system',                '系统管理',   'DIRECTORY', '/1/',       1, 0, 1, 'system'),
    (2,  1, 1, 'system:user',           '用户管理',     'MENU',      '/1/2/',     2, 1, 1, 'system'),
    (3,  1, 1, 'system:role',           '角色管理',     'MENU',      '/1/3/',     2, 2, 1, 'system'),
    (4,  1, 1, 'system:permission',     '权限管理',     'MENU',      '/1/4/',     2, 3, 1, 'system'),
    (5,  1, 1, 'system:org',            '组织管理',   'MENU',      '/1/5/',     2, 4, 1, 'system'),
    (6,  1, 1, 'system:tenant',         '租户管理',   'MENU',      '/1/6/',     2, 5, 1, 'system'),
    (7,  1, 2, 'system:user:list',      '查看用户',          'API',       '/1/2/7/',   3, 1, 1, 'system'),
    (8,  1, 2, 'system:user:create',    '创建用户',         'API',       '/1/2/8/',   3, 2, 1, 'system'),
    (9,  1, 2, 'system:user:update',    '更新用户',         'API',       '/1/2/9/',   3, 3, 1, 'system'),
    (10, 1, 2, 'system:user:delete',    '删除用户',         'API',       '/1/2/10/',  3, 4, 1, 'system'),
    (11, 1, 3, 'system:role:list',      '查看角色',          'API',       '/1/3/11/',  3, 1, 1, 'system'),
    (12, 1, 3, 'system:role:create',    '创建角色',         'API',       '/1/3/12/',  3, 2, 1, 'system'),
    (13, 1, 3, 'system:role:update',    '更新角色',         'API',       '/1/3/13/',  3, 3, 1, 'system'),
    (14, 1, 3, 'system:role:delete',    '删除角色',         'API',       '/1/3/14/',  3, 4, 1, 'system'),
    (15, 1, 4, 'system:permission:list','查看权限',    'API',       '/1/4/15/',  3, 1, 1, 'system'),
    (16, 1, 4, 'system:permission:create','创建权限', 'API',       '/1/4/16/',  3, 2, 1, 'system'),
    (17, 1, 4, 'system:permission:update','更新权限', 'API',       '/1/4/17/',  3, 3, 1, 'system'),
    (18, 1, 4, 'system:permission:delete','删除权限', 'API',       '/1/4/18/',  3, 4, 1, 'system'),
    (19, 1, 5, 'system:org:list',       '查看组织',  'API',       '/1/5/19/',  3, 1, 1, 'system'),
    (20, 1, 5, 'system:org:create',     '创建组织', 'API',       '/1/5/20/',  3, 2, 1, 'system'),
    (21, 1, 5, 'system:org:update',     '更新组织', 'API',       '/1/5/21/',  3, 3, 1, 'system'),
    (22, 1, 5, 'system:org:delete',     '删除组织', 'API',       '/1/5/22/',  3, 4, 1, 'system'),
    (23, 1, 6, 'system:tenant:list',    '查看租户',        'API',       '/1/6/23/',  3, 1, 1, 'system'),
    (24, 1, 6, 'system:tenant:create',  '创建租户',       'API',       '/1/6/24/',  3, 2, 1, 'system'),
    (25, 1, 6, 'system:tenant:update',  '更新租户',       'API',       '/1/6/25/',  3, 3, 1, 'system'),
    (26, 1, 6, 'system:tenant:delete',  '删除租户',       'API',       '/1/6/26/',  3, 4, 1, 'system'),
    (27, 1, 1, 'system:oauth2',         'OAuth2 客户端',      'MENU',      '/1/27/',    2, 6, 1, 'system'),
    (28, 1, 27,'system:oauth2:list',    '查看客户端',        'API',       '/1/27/28/', 3, 1, 1, 'system'),
    (29, 1, 27,'system:oauth2:create',  '创建客户端',       'API',       '/1/27/29/', 3, 2, 1, 'system'),
    (30, 1, 27,'system:oauth2:update',  '更新客户端',       'API',       '/1/27/30/', 3, 3, 1, 'system'),
    (31, 1, 27,'system:oauth2:delete',  '删除客户端',       'API',       '/1/27/31/', 3, 4, 1, 'system'),
    (32, 1, 1, 'system:online',         '在线用户',        'MENU',      '/1/32/',    2, 7, 1, 'system'),
    (33, 1, 32,'system:online:list',    '查看在线用户',   'API',       '/1/32/33/', 3, 1, 1, 'system'),
    (34, 1, 32,'system:online:kick',    '强制下线',    'API',       '/1/32/34/', 3, 2, 1, 'system'),
    (35, 1, 1, 'system:authrecord',     '授权记录',        'MENU',      '/1/35/',    2, 8, 1, 'system'),
    (36, 1, 35,'system:authrecord:list','查看授权记录',   'API',       '/1/35/36/', 3, 1, 1, 'system'),
    (37, 1, 1, 'system:auditlog',       '审计日志',        'MENU',      '/1/37/',    2, 9, 1, 'system'),
    (38, 1, 37,'system:auditlog:list',  '查看审计日志',   'API',       '/1/37/38/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6),
    (1, 7), (1, 8), (1, 9), (1, 10),
    (1, 11), (1, 12), (1, 13), (1, 14),
    (1, 15), (1, 16), (1, 17), (1, 18),
    (1, 19), (1, 20), (1, 21), (1, 22),
    (1, 23), (1, 24), (1, 25), (1, 26),
    (1, 27), (1, 28), (1, 29), (1, 30),
    (1, 31), (1, 32), (1, 33), (1, 34),
    (1, 35), (1, 36), (1, 37), (1, 38),
    (1, 39), (1, 40), (1, 41), (1, 42), (1, 43);

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (39, 1, 1,  'system:xssconfig',         'XSS防护配置',   'MENU', '/1/39/',    2, 10, 1, 'system'),
    (40, 1, 39, 'system:xssconfig:list',    '查看XSS配置',   'API',  '/1/39/40/', 3, 1,  1, 'system'),
    (41, 1, 39, 'system:xssconfig:update',  '更新XSS配置',   'API',  '/1/39/41/', 3, 2,  1, 'system'),
    (42, 1, 39, 'system:xssconfig:create',  '创建XSS规则',   'API',  '/1/39/42/', 3, 3,  1, 'system'),
    (43, 1, 39, 'system:xssconfig:delete',  '删除XSS规则',   'API',  '/1/39/43/', 3, 4,  1, 'system');

INSERT IGNORE INTO sys_xss_config (tenant_id, enabled, create_by) VALUES (1, 0, 'system');

INSERT IGNORE INTO sys_xss_blacklist_rule (tenant_id, rule_name, rule_type, pattern, enabled, description, sort_order, create_by) VALUES
    (1, 'Script标签',     'HTML_TAG',           'script',            1, '拦截<script>标签及其内容',   1,  'system'),
    (1, 'IFrame标签',     'HTML_TAG',           'iframe',            1, '拦截<iframe>标签及其内容',   2,  'system'),
    (1, 'Object标签',     'HTML_TAG',           'object',            1, '拦截<object>标签及其内容',   3,  'system'),
    (1, 'Embed标签',      'HTML_TAG',           'embed',             1, '拦截<embed>标签及其内容',    4,  'system'),
    (1, 'Form标签',       'HTML_TAG',           'form',              1, '拦截<form>标签及其内容',     5,  'system'),
    (1, '事件处理器',      'EVENT_HANDLER',      'on\\w+',           1, '拦截on*事件属性(onclick等)', 6,  'system'),
    (1, 'JavaScript协议', 'DANGEROUS_PROTOCOL', 'javascript:',       1, '拦截javascript:伪协议',     7,  'system'),
    (1, 'VBScript协议',   'DANGEROUS_PROTOCOL', 'vbscript:',         1, '拦截vbscript:伪协议',        8,  'system'),
    (1, 'Data协议',       'DANGEROUS_PROTOCOL', 'data:',             1, '拦截data:URI',              9,  'system'),
    (1, 'Expression',     'CUSTOM_PATTERN',     'expression\\s*\\(', 1, '拦截CSS expression表达式',   10, 'system');

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (50, 1, 0,  'base',                 '基础数据',       'DIRECTORY', '/50/',       1, 1, 1, 'system'),
    (51, 1, 50, 'base:dict',            '字典管理',       'MENU',      '/50/51/',    2, 1, 1, 'system'),
    (52, 1, 51, 'dict:type:list',       '查看字典类型',   'API',       '/50/51/52/', 3, 1, 1, 'system'),
    (53, 1, 51, 'dict:type:create',     '创建字典类型',   'API',       '/50/51/53/', 3, 2, 1, 'system'),
    (54, 1, 51, 'dict:type:update',     '更新字典类型',   'API',       '/50/51/54/', 3, 3, 1, 'system'),
    (55, 1, 51, 'dict:type:delete',     '删除字典类型',   'API',       '/50/51/55/', 3, 4, 1, 'system'),
    (56, 1, 51, 'dict:data:list',       '查看字典数据',   'API',       '/50/51/56/', 3, 5, 1, 'system'),
    (57, 1, 51, 'dict:data:create',     '创建字典数据',   'API',       '/50/51/57/', 3, 6, 1, 'system'),
    (58, 1, 51, 'dict:data:update',     '更新字典数据',   'API',       '/50/51/58/', 3, 7, 1, 'system'),
    (59, 1, 51, 'dict:data:delete',     '删除字典数据',   'API',       '/50/51/59/', 3, 8, 1, 'system'),
    (60, 1, 51, 'dict:data:refresh',    '刷新字典缓存',   'API',       '/50/51/60/', 3, 9, 1, 'system');

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (63, 1, 0,  'monitor',                  '运维监控',     'DIRECTORY', '/63/',          1, 5, 1, 'system'),
    (61, 1, 63, 'base:operlog',             '操作日志',     'MENU',      '/63/61/',       2, 1, 1, 'system'),
    (62, 1, 61, 'base:operlog:list',        '查看操作日志', 'API',       '/63/61/62/',    3, 1, 1, 'system'),
    (64, 1, 63, 'base:mqmessage',           '消息记录',     'MENU',      '/63/64/',       2, 2, 1, 'system'),
    (65, 1, 64, 'base:mqmessage:list',      '查看消息记录', 'API',       '/63/64/65/',    3, 1, 1, 'system'),
    (66, 1, 64, 'base:mqmessage:resend',    '重发消息',     'API',       '/63/64/66/',    3, 2, 1, 'system'),
    (67, 1, 64, 'base:mqmessage:skip',      '忽略消息',     'API',       '/63/64/67/',    3, 3, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 50), (1, 51), (1, 52), (1, 53), (1, 54), (1, 55),
    (1, 56), (1, 57), (1, 58), (1, 59), (1, 60), (1, 61), (1, 62),
    (1, 63), (1, 64), (1, 65), (1, 66), (1, 67);

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (110, 1, 0, 'job', '任务调度', 'DIRECTORY', '/110/', 1, 2, 1, 'system');

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (70, 1, 110, 'job:user-job-type',        '任务类型管理',     'MENU', '/110/70/',      2, 1, 1, 'system'),
    (71, 1, 70,  'job:user-job-type:list',    '查看任务类型',     'API',  '/110/70/71/',   3, 1, 1, 'system'),
    (72, 1, 70,  'job:user-job-type:create',  '创建任务类型',     'API',  '/110/70/72/',   3, 2, 1, 'system'),
    (73, 1, 70,  'job:user-job-type:update',  '更新任务类型',     'API',  '/110/70/73/',   3, 3, 1, 'system'),
    (74, 1, 70,  'job:user-job-type:delete',  '删除任务类型',     'API',  '/110/70/74/',   3, 4, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 110),
    (1, 70), (1, 71), (1, 72), (1, 73), (1, 74);

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (100, 1, 110, 'job:system-job',           '系统任务',         'MENU', '/110/100/',      2, 4, 1, 'system'),
    (101, 1, 100, 'job:system-job:list',     '查看系统任务',     'API',  '/110/100/101/',   3, 1, 1, 'system'),
    (102, 1, 100, 'job:system-job:manage',   '管理系统任务',     'API',  '/110/100/102/',   3, 2, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 100), (1, 101), (1, 102);

INSERT IGNORE INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (200, 1, 0,   'workflow',                        '工作流管理',     'DIRECTORY', '/200/',          1, 6, 1, 'system'),
    (201, 1, 200, 'workflow:definition',              '流程定义',       'MENU',      '/200/201/',      2, 2, 1, 'system'),
    (202, 1, 201, 'workflow:definition:list',         '查看流程定义',   'API',       '/200/201/202/',  3, 1, 1, 'system'),
    (203, 1, 201, 'workflow:definition:deploy',       '部署流程',       'API',       '/200/201/203/',  3, 2, 1, 'system'),
    (204, 1, 201, 'workflow:definition:update',       '挂起/激活流程',  'API',       '/200/201/204/',  3, 3, 1, 'system'),
    (205, 1, 201, 'workflow:definition:delete',       '删除流程',       'API',       '/200/201/205/',  3, 4, 1, 'system'),
    (210, 1, 200, 'workflow:instance',                '流程实例',       'MENU',      '/200/210/',      2, 3, 1, 'system'),
    (211, 1, 210, 'workflow:instance:list',           '查看流程实例',   'API',       '/200/210/211/',  3, 1, 1, 'system'),
    (212, 1, 210, 'workflow:instance:start',          '发起流程实例',   'API',       '/200/210/212/',  3, 2, 1, 'system'),
    (213, 1, 210, 'workflow:instance:terminate',      '终止流程实例',   'API',       '/200/210/213/',  3, 3, 1, 'system'),
    (214, 1, 210, 'workflow:task:todo',               '查看待办任务',   'API',       '/200/210/214/',  3, 4, 1, 'system'),
    (215, 1, 210, 'workflow:approval:complete',       '完成审批任务',   'API',       '/200/210/215/',  3, 5, 1, 'system'),
    (216, 1, 210, 'workflow:approval:add-signer',     '审批加签',       'API',       '/200/210/216/',  3, 6, 1, 'system'),
    (217, 1, 210, 'workflow:approval:remove-signer',  '审批减签',       'API',       '/200/210/217/',  3, 7, 1, 'system'),
    (218, 1, 210, 'workflow:approval:delegate',       '审批委托',       'API',       '/200/210/218/',  3, 8, 1, 'system'),
    (220, 1, 200, 'workflow:stats',                   '统计看板',       'MENU',      '/200/220/',      2, 4, 1, 'system'),
    (221, 1, 220, 'workflow:stats:admin',             '管理端统计',     'API',       '/200/220/221/',  3, 1, 1, 'system'),
    (222, 1, 200, 'workflow:identity',                '身份管理',       'API',       '/200/222/',      2, 5, 1, 'system'),
    (223, 1, 222, 'workflow:identity:list',           '查询身份数据',   'API',       '/200/222/223/',  3, 1, 1, 'system'),
    (224, 1, 200, 'workflow:model',                   '流程模型',       'MENU',      '/200/224/',      2, 1, 1, 'system'),
    (225, 1, 224, 'workflow:model:list',              '查询模型列表',   'API',       '/200/224/225/',  3, 1, 1, 'system'),
    (226, 1, 224, 'workflow:model:create',            '创建模型',       'API',       '/200/224/226/',  3, 2, 1, 'system'),
    (227, 1, 224, 'workflow:model:update',            '更新模型',       'API',       '/200/224/227/',  3, 3, 1, 'system'),
    (228, 1, 224, 'workflow:model:validate',          '校验模型',       'API',       '/200/224/228/',  3, 4, 1, 'system'),
    (229, 1, 224, 'workflow:model:publish',           '发布模型',       'API',       '/200/224/229/',  3, 5, 1, 'system'),
    (230, 1, 224, 'workflow:model:delete',            '删除模型',       'API',       '/200/224/230/',  3, 6, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 200), (1, 201), (1, 202), (1, 203), (1, 204), (1, 205),
    (1, 210), (1, 211), (1, 212), (1, 213), (1, 214), (1, 215),
    (1, 216), (1, 217), (1, 218), (1, 220), (1, 221),
    (1, 222), (1, 223), (1, 224), (1, 225), (1, 226), (1, 227),
    (1, 228), (1, 229), (1, 230);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7),
    (2, 11), (2, 15), (2, 19), (2, 23),
    (2, 27), (2, 28),
    (2, 51), (2, 52), (2, 56),
    (2, 61), (2, 62),
    (2, 70), (2, 71),
    (2, 201), (2, 202), (2, 210), (2, 211), (2, 214);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (10, 211), (10, 212), (10, 213), (10, 214), (10, 215), (10, 216), (10, 217), (10, 218),
    (11, 211), (11, 212), (11, 213), (11, 214), (11, 215), (11, 216), (11, 217), (11, 218),
    (12, 211), (12, 212), (12, 213), (12, 214), (12, 215), (12, 216), (12, 217), (12, 218);

INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (300, 1, 0,   'crm',                      '客户关系管理', 'DIRECTORY', '/300/',         1, 7, 1, 'system'),
    (301, 1, 300, 'crm:overview',             'CRM概览',      'MENU',      '/300/301/',     2, 1, 1, 'system'),
    (302, 1, 301, 'crm:overview:list',        '查看CRM概览',  'API',       '/300/301/302/', 3, 1, 1, 'system'),
    (310, 1, 300, 'crm:lead',                 '线索管理',     'MENU',      '/300/310/',     2, 2, 1, 'system'),
    (311, 1, 310, 'crm:lead:list',            '查看线索',     'API',       '/300/310/311/', 3, 1, 1, 'system'),
    (312, 1, 310, 'crm:lead:create',          '创建线索',     'API',       '/300/310/312/', 3, 2, 1, 'system'),
    (313, 1, 310, 'crm:lead:update',          '更新线索',     'API',       '/300/310/313/', 3, 3, 1, 'system'),
    (314, 1, 310, 'crm:lead:delete',          '删除线索',     'API',       '/300/310/314/', 3, 4, 1, 'system'),
    (315, 1, 310, 'crm:lead:assign',          '分配线索',     'API',       '/300/310/315/', 3, 5, 1, 'system'),
    (316, 1, 310, 'crm:lead:disqualify',      '判定无效线索', 'API',       '/300/310/316/', 3, 6, 1, 'system'),
    (317, 1, 310, 'crm:lead:convert',         '转换线索',     'API',       '/300/310/317/', 3, 7, 1, 'system'),
    (320, 1, 300, 'crm:customer',             '客户管理',     'MENU',      '/300/320/',     2, 3, 1, 'system'),
    (321, 1, 320, 'crm:customer:list',        '查看客户',     'API',       '/300/320/321/', 3, 1, 1, 'system'),
    (322, 1, 320, 'crm:customer:create',      '创建客户',     'API',       '/300/320/322/', 3, 2, 1, 'system'),
    (323, 1, 320, 'crm:customer:update',      '更新客户',     'API',       '/300/320/323/', 3, 3, 1, 'system'),
    (324, 1, 320, 'crm:customer:delete',      '删除客户',     'API',       '/300/320/324/', 3, 4, 1, 'system'),
    (325, 1, 320, 'crm:customer:status',      '变更客户状态', 'API',       '/300/320/325/', 3, 5, 1, 'system'),
    (326, 1, 320, 'crm:customer:transfer',    '转移客户',     'API',       '/300/320/326/', 3, 6, 1, 'system'),
    (327, 1, 320, 'crm:customer:blacklist',   '客户黑名单',   'API',       '/300/320/327/', 3, 7, 1, 'system'),
    (332, 1, 300, 'crm:contact',              '联系人管理',   'MENU',      '/300/332/',     2, 4, 1, 'system'),
    (328, 1, 332, 'crm:contact:list',         '查看联系人',   'API',       '/300/332/328/', 3, 1, 1, 'system'),
    (329, 1, 332, 'crm:contact:create',       '创建联系人',   'API',       '/300/332/329/', 3, 2, 1, 'system'),
    (330, 1, 332, 'crm:contact:update',       '更新联系人',   'API',       '/300/332/330/', 3, 3, 1, 'system'),
    (331, 1, 332, 'crm:contact:delete',       '删除联系人',   'API',       '/300/332/331/', 3, 4, 1, 'system'),
    (340, 1, 300, 'crm:opportunity',          '商机管理',     'MENU',      '/300/340/',     2, 5, 1, 'system'),
    (341, 1, 340, 'crm:opportunity:list',     '查看商机',     'API',       '/300/340/341/', 3, 1, 1, 'system'),
    (342, 1, 340, 'crm:opportunity:create',   '创建商机',     'API',       '/300/340/342/', 3, 2, 1, 'system'),
    (343, 1, 340, 'crm:opportunity:update',   '更新商机',     'API',       '/300/340/343/', 3, 3, 1, 'system'),
    (344, 1, 340, 'crm:opportunity:delete',   '删除商机',     'API',       '/300/340/344/', 3, 4, 1, 'system'),
    (345, 1, 340, 'crm:opportunity:assign',   '分配商机',     'API',       '/300/340/345/', 3, 5, 1, 'system'),
    (346, 1, 340, 'crm:opportunity:stage',    '推进商机阶段', 'API',       '/300/340/346/', 3, 6, 1, 'system'),
    (347, 1, 340, 'crm:opportunity:reopen',   '重开商机',     'API',       '/300/340/347/', 3, 7, 1, 'system'),
    (360, 1, 300, 'crm:activity',             '跟进管理',     'MENU',      '/300/360/',     2, 6, 1, 'system'),
    (361, 1, 360, 'crm:activity:list',        '查看跟进',     'API',       '/300/360/361/', 3, 1, 1, 'system'),
    (362, 1, 360, 'crm:activity:create',      '创建跟进',     'API',       '/300/360/362/', 3, 2, 1, 'system'),
    (363, 1, 360, 'crm:activity:update',      '更新跟进',     'API',       '/300/360/363/', 3, 3, 1, 'system'),
    (364, 1, 360, 'crm:activity:delete',      '删除跟进',     'API',       '/300/360/364/', 3, 4, 1, 'system'),
    (365, 1, 360, 'crm:activity:complete',    '完成跟进',     'API',       '/300/360/365/', 3, 5, 1, 'system'),
    (366, 1, 360, 'crm:activity:cancel',      '取消跟进',     'API',       '/300/360/366/', 3, 6, 1, 'system'),
    (380, 1, 300, 'crm:owner:list',           '查看负责人选项','API',       '/300/380/',     2, 7, 1, 'system'),
    (381, 1, 300, 'crm:pii:view',             '查看完整联系信息','API',     '/300/381/',     2, 8, 1, 'system');

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (20, 1, 'CRM_ADMIN',     'CRM管理员', 'TENANT',         20, 1, 'system'),
    (21, 1, 'SALES_MANAGER', '销售经理',  'DEPT_AND_BELOW', 21, 1, 'system'),
    (22, 1, 'SALES_REP',     '销售代表',  'SELF',           22, 1, 'system'),
    (23, 1, 'CRM_VIEWER',    'CRM只读员', 'TENANT',         23, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'CRM_ADMIN', 'SALES_MANAGER')
  AND (p.permission_code = 'crm' OR p.permission_code LIKE 'crm:%');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'SALES_REP'
  AND p.permission_code IN (
      'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
      'crm:overview:list','crm:lead:list','crm:lead:create','crm:lead:update',
      'crm:lead:disqualify','crm:lead:convert',
      'crm:customer:list','crm:customer:create','crm:customer:update','crm:customer:status',
      'crm:contact:list','crm:contact:create','crm:contact:update',
      'crm:opportunity:list','crm:opportunity:create','crm:opportunity:update',
      'crm:opportunity:stage','crm:opportunity:reopen',
      'crm:activity:list','crm:activity:create','crm:activity:update',
      'crm:activity:complete','crm:activity:cancel','crm:owner:list','crm:pii:view'
  );
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'CRM_VIEWER'
  AND p.permission_code IN (
      'crm','crm:overview','crm:lead','crm:customer','crm:contact','crm:opportunity','crm:activity',
      'crm:overview:list','crm:lead:list','crm:customer:list','crm:contact:list',
      'crm:opportunity:list','crm:activity:list'
  );

INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (400, 1, 0,   'srm',                          '供应商关系管理',  'DIRECTORY', '/400/',             1, 8, 1, 'system'),
    (401, 1, 400, 'srm:overview',                   'SRM概览',        'MENU',      '/400/401/',         2, 1, 1, 'system'),
    (402, 1, 401, 'srm:overview:list',              '查看SRM概览',    'API',       '/400/401/402/',     3, 1, 1, 'system'),
    (410, 1, 400, 'srm:supplier',                   '供应商管理',      'MENU',      '/400/410/',         2, 2, 1, 'system'),
    (411, 1, 410, 'srm:supplier:list',              '查看供应商',      'API',       '/400/410/411/',     3, 1, 1, 'system'),
    (412, 1, 410, 'srm:supplier:create',            '创建供应商',      'API',       '/400/410/412/',     3, 2, 1, 'system'),
    (413, 1, 410, 'srm:supplier:update',            '更新供应商',      'API',       '/400/410/413/',     3, 3, 1, 'system'),
    (414, 1, 410, 'srm:supplier:delete',            '删除供应商',      'API',       '/400/410/414/',     3, 4, 1, 'system'),
    (415, 1, 410, 'srm:supplier:withdraw',         '撤回审批流程',    'API',       '/400/410/415/',     3, 5, 1, 'system'),
    (416, 1, 410, 'srm:supplier:cancel',           '取消审批流程',    'API',       '/400/410/416/',     3, 6, 1, 'system'),
    (417, 1, 410, 'srm:supplier:suspend',           '冻结供应商',      'API',       '/400/410/417/',     3, 7, 1, 'system'),
    (418, 1, 410, 'srm:supplier:resume',            '解冻供应商',      'API',       '/400/410/418/',     3, 8, 1, 'system'),
    (419, 1, 410, 'srm:supplier:blacklist',         '供应商黑名单',    'API',       '/400/410/419/',     3, 9, 1, 'system'),
    (462, 1, 410, 'srm:supplier:restore',           '恢复供应商',      'API',       '/400/410/462/',     3, 10, 1, 'system'),
    (463, 1, 410, 'srm:supplier:eliminate',         '淘汰供应商',      'API',       '/400/410/463/',     3, 11, 1, 'system'),
    (464, 1, 410, 'srm:supplier:transfer',          '转移供应商负责人', 'API',       '/400/410/464/',     3, 12, 1, 'system'),
    (420, 1, 400, 'srm:evaluation',                 '绩效评估',        'MENU',      '/400/420/',         2, 3, 1, 'system'),
    (421, 1, 420, 'srm:evaluation:list',            '查看评估',        'API',       '/400/420/421/',     3, 1, 1, 'system'),
    (422, 1, 420, 'srm:evaluation:create',          '创建评估',        'API',       '/400/420/422/',     3, 2, 1, 'system'),
    (423, 1, 420, 'srm:evaluation:view',            '查看评估详情',    'API',       '/400/420/423/',     3, 3, 1, 'system'),
    (430, 1, 400, 'srm:risk',                       '风险管理',        'MENU',      '/400/430/',         2, 4, 1, 'system'),
    (431, 1, 430, 'srm:risk:list',                  '查看风险',        'API',       '/400/430/431/',     3, 1, 1, 'system'),
    (432, 1, 430, 'srm:risk:update',                '更新风险指标',    'API',       '/400/430/432/',     3, 2, 1, 'system'),
    (433, 1, 430, 'srm:risk:assess',                '创建风险评估',    'API',       '/400/430/433/',     3, 3, 1, 'system'),
    (434, 1, 400, 'srm:risk:config',                '风险指标配置',    'MENU',      '/400/434/',         2, 5, 1, 'system'),
    (435, 1, 434, 'srm:risk:config:list',           '查看指标配置',    'API',       '/400/434/435/',     3, 1, 1, 'system'),
    (436, 1, 434, 'srm:risk:config:update',         '修改指标配置',    'API',       '/400/434/436/',     3, 2, 1, 'system'),
    (440, 1, 400, 'srm:portal',                     '供应商门户',      'DIRECTORY', '/400/440/',         2, 5, 1, 'system'),
    (441, 1, 440, 'srm:portal:enroll',              '门户入驻',        'API',       '/400/440/441/',     3, 1, 1, 'system'),
    (442, 1, 440, 'srm:portal:profile',             '企业信息',        'MENU',      '/400/440/442/',     3, 2, 1, 'system'),
    (443, 1, 440, 'srm:portal:evaluation',          '绩效评估',        'MENU',      '/400/440/443/',     3, 3, 1, 'system'),
    (444, 1, 440, 'srm:portal:quotation',           '询价报价',        'MENU',      '/400/440/444/',     3, 4, 1, 'system'),
    (450, 1, 400, 'srm:invite',                     '邀请管理',        'MENU',      '/400/450/',         2, 6, 1, 'system'),
    (451, 1, 450, 'srm:invite:create',              '创建邀请',        'API',       '/400/450/451/',     3, 1, 1, 'system'),
    (452, 1, 450, 'srm:invite:list',                '查看邀请',        'API',       '/400/450/452/',     3, 2, 1, 'system'),
    (453, 1, 450, 'srm:invite:revoke',              '撤销邀请',        'API',       '/400/450/453/',     3, 3, 1, 'system'),
    (482, 1, 450, 'srm:portal:invite',              '管理门户邀请',    'API',       '/400/450/482/',     3, 4, 1, 'system'),
    (460, 1, 400, 'srm:owner:list',                 '查看负责人选项',  'API',       '/400/460/',         2, 7, 1, 'system'),
    (461, 1, 400, 'srm:pii:view',                   '查看完整银行信息','API',       '/400/461/',         2, 8, 1, 'system'),
    -- 联系人权限
    (470, 1, 410, 'srm:contact:list',               '查看联系人',      'API',       '/400/410/470/',     3, 12, 1, 'system'),
    (471, 1, 410, 'srm:contact:create',             '创建联系人',      'API',       '/400/410/471/',     3, 13, 1, 'system'),
    (480, 1, 410, 'srm:contact:update',             '更新联系人',      'API',       '/400/410/480/',     3, 14, 1, 'system'),
    (481, 1, 410, 'srm:contact:delete',             '删除联系人',      'API',       '/400/410/481/',     3, 15, 1, 'system'),
    -- 资质权限
    (472, 1, 410, 'srm:qualification:list',          '查看资质',        'API',       '/400/410/472/',     3, 14, 1, 'system'),
    (473, 1, 410, 'srm:qualification:create',        '创建资质',        'API',       '/400/410/473/',     3, 15, 1, 'system'),
    (474, 1, 410, 'srm:qualification:update',        '更新资质',        'API',       '/400/410/474/',     3, 16, 1, 'system'),
    (475, 1, 410, 'srm:qualification:delete',        '删除资质',        'API',       '/400/410/475/',     3, 17, 1, 'system'),
    -- 银行账户权限
    (476, 1, 410, 'srm:bank-account:list',            '查看银行账户',    'API',       '/400/410/476/',     3, 18, 1, 'system'),
    (477, 1, 410, 'srm:bank-account:create',          '创建银行账户',    'API',       '/400/410/477/',     3, 19, 1, 'system'),
    (478, 1, 410, 'srm:bank-account:update',          '更新银行账户',    'API',       '/400/410/478/',     3, 20, 1, 'system'),
    (479, 1, 410, 'srm:bank-account:delete',          '删除银行账户',    'API',       '/400/410/479/',     3, 21, 1, 'system');

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (30, 1, 'SRM_ADMIN',           'SRM管理员',       'TENANT',         30, 1, 'system'),
    (31, 1, 'PROCUREMENT_MANAGER', '采购经理',        'DEPT_AND_BELOW', 31, 1, 'system'),
    (32, 1, 'PROCUREMENT_STAFF',   '采购员',          'SELF',           32, 1, 'system'),
    (33, 1, 'SUPPLIER',            '供应商',          'SELF',           33, 1, 'system'),
    (34, 1, 'SRM_MANAGER',         '采购经理',        'DEPT_AND_BELOW', 34, 1, 'system'),
    (35, 1, 'SRM_COMPLIANCE',      '合规负责人',      'DEPT_AND_BELOW', 35, 1, 'system'),
    (36, 1, 'SRM_DIRECTOR',        '高管',            'TENANT',         36, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'SRM_ADMIN', 'PROCUREMENT_MANAGER')
  AND (p.permission_code = 'srm' OR p.permission_code LIKE 'srm:%')
  AND (r.role_code = 'SUPER_ADMIN'
       OR p.permission_code NOT IN (
           'srm:portal:enroll', 'srm:portal:profile',
           'srm:portal:evaluation', 'srm:portal:quotation'
       ));

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'PROCUREMENT_STAFF'
  AND p.permission_code IN (
      'srm','srm:overview','srm:supplier','srm:evaluation','srm:risk',
      'srm:overview:list','srm:supplier:list','srm:supplier:create','srm:supplier:update',
      'srm:evaluation:list','srm:evaluation:create','srm:evaluation:view',
      'srm:risk:list','srm:risk:update','srm:risk:assess','srm:risk:config','srm:risk:config:list','srm:risk:config:update',
      'srm:contact:list','srm:contact:create','srm:contact:update','srm:contact:delete',
      'srm:qualification:list','srm:qualification:create','srm:qualification:update','srm:qualification:delete',
      'srm:bank-account:list','srm:bank-account:create','srm:bank-account:update','srm:bank-account:delete',
      'srm:owner:list'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'USER'
  AND p.permission_code IN ('srm', 'srm:portal', 'srm:portal:enroll');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1 AND r.role_code = 'SUPPLIER'
  AND p.permission_code IN (
      'srm', 'srm:portal', 'srm:portal:profile',
      'srm:portal:evaluation', 'srm:portal:quotation'
  );

INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (500, 1, 0,   'procurement',                         '采购管理',     'DIRECTORY', '/500/',         1, 8, 1, 'system'),
    (505, 1, 500, 'procurement:overview',                '采购概览',     'MENU',      '/500/505/',     2, 1, 1, 'system'),
    (506, 1, 505, 'procurement:overview:list',           '查看采购概览', 'API',       '/500/505/506/', 3, 1, 1, 'system'),
    (510, 1, 500, 'procurement:material',                '物料目录',     'MENU',      '/500/510/',     2, 2, 1, 'system'),
    (511, 1, 510, 'procurement:material:list',           '查看物料',     'API',       '/500/510/511/', 3, 1, 1, 'system'),
    (512, 1, 510, 'procurement:material:create',         '创建物料',     'API',       '/500/510/512/', 3, 2, 1, 'system'),
    (513, 1, 510, 'procurement:material:update',         '更新物料',     'API',       '/500/510/513/', 3, 3, 1, 'system'),
    (514, 1, 510, 'procurement:material:delete',         '删除物料',     'API',       '/500/510/514/', 3, 4, 1, 'system'),
    (520, 1, 500, 'procurement:requisition',             '请购管理',     'MENU',      '/500/520/',     2, 3, 1, 'system'),
    (521, 1, 520, 'procurement:requisition:list',        '查看请购',     'API',       '/500/520/521/', 3, 1, 1, 'system'),
    (522, 1, 520, 'procurement:requisition:create',      '创建请购',     'API',       '/500/520/522/', 3, 2, 1, 'system'),
    (523, 1, 520, 'procurement:requisition:update',      '更新请购',     'API',       '/500/520/523/', 3, 3, 1, 'system'),
    (524, 1, 520, 'procurement:requisition:delete',      '删除请购',     'API',       '/500/520/524/', 3, 4, 1, 'system'),
    (525, 1, 520, 'procurement:requisition:submit',      '提交请购',     'API',       '/500/520/525/', 3, 5, 1, 'system'),
    (526, 1, 520, 'procurement:requisition:approve',     '审批请购视图', 'API',       '/500/520/526/', 3, 6, 1, 'system'),
    (527, 1, 520, 'procurement:requisition:cancel',      '取消请购',     'API',       '/500/520/527/', 3, 7, 1, 'system'),
    (530, 1, 500, 'procurement:approval-route',          '审批路由',     'MENU',      '/500/530/',     2, 4, 1, 'system'),
    (531, 1, 530, 'procurement:approval-route:list',     '查看审批路由', 'API',       '/500/530/531/', 3, 1, 1, 'system'),
    (532, 1, 530, 'procurement:approval-route:create',   '创建审批路由', 'API',       '/500/530/532/', 3, 2, 1, 'system'),
    (533, 1, 530, 'procurement:approval-route:update',   '更新审批路由', 'API',       '/500/530/533/', 3, 3, 1, 'system'),
    (534, 1, 530, 'procurement:approval-route:delete',   '删除审批路由', 'API',       '/500/530/534/', 3, 4, 1, 'system'),
    (540, 1, 500, 'procurement:rfq',                     '询价管理',     'MENU',      '/500/540/',     2, 5, 1, 'system'),
    (541, 1, 540, 'procurement:rfq:list',                '查看询价',     'API',       '/500/540/541/', 3, 1, 1, 'system'),
    (542, 1, 540, 'procurement:rfq:create',              '创建询价',     'API',       '/500/540/542/', 3, 2, 1, 'system'),
    (543, 1, 540, 'procurement:rfq:update',              '更新询价',     'API',       '/500/540/543/', 3, 3, 1, 'system'),
    (544, 1, 540, 'procurement:rfq:delete',              '删除询价',     'API',       '/500/540/544/', 3, 4, 1, 'system'),
    (545, 1, 540, 'procurement:rfq:send',                '发送询价',     'API',       '/500/540/545/', 3, 5, 1, 'system'),
    (546, 1, 540, 'procurement:rfq:award',               '询价定点',     'API',       '/500/540/546/', 3, 6, 1, 'system'),
    (547, 1, 540, 'procurement:rfq:cancel',              '取消询价',     'API',       '/500/540/547/', 3, 7, 1, 'system'),
    (550, 1, 500, 'procurement:purchase-order',          '采购订单',     'MENU',      '/500/550/',     2, 6, 1, 'system'),
    (551, 1, 550, 'procurement:purchase-order:list',     '查看采购订单', 'API',       '/500/550/551/', 3, 1, 1, 'system'),
    (553, 1, 550, 'procurement:purchase-order:update',   '更新采购订单', 'API',       '/500/550/553/', 3, 3, 1, 'system'),
    (554, 1, 550, 'procurement:purchase-order:delete',   '删除采购订单', 'API',       '/500/550/554/', 3, 4, 1, 'system'),
    (555, 1, 550, 'procurement:purchase-order:send',     '发送采购订单', 'API',       '/500/550/555/', 3, 5, 1, 'system'),
    (556, 1, 550, 'procurement:purchase-order:confirm',  '确认采购订单', 'API',       '/500/550/556/', 3, 6, 1, 'system'),
    (557, 1, 550, 'procurement:purchase-order:cancel',   '取消采购订单', 'API',       '/500/550/557/', 3, 7, 1, 'system'),
    (560, 1, 500, 'procurement:goods-receipt',           '采购收货',     'MENU',      '/500/560/',     2, 7, 1, 'system'),
    (561, 1, 560, 'procurement:goods-receipt:list',      '查看采购收货', 'API',       '/500/560/561/', 3, 1, 1, 'system'),
    (562, 1, 560, 'procurement:goods-receipt:create',    '创建采购收货', 'API',       '/500/560/562/', 3, 2, 1, 'system'),
    (563, 1, 560, 'procurement:goods-receipt:confirm',   '确认采购收货', 'API',       '/500/560/563/', 3, 3, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'PROCUREMENT_MANAGER')
  AND (p.permission_code = 'procurement' OR p.permission_code LIKE 'procurement:%');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code = 'PROCUREMENT_MANAGER'
  AND p.permission_code IN (
      'workflow', 'workflow:instance', 'workflow:task:todo',
      'workflow:approval:complete', 'workflow:model:list'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code = 'PROCUREMENT_STAFF'
  AND p.permission_code IN (
      'procurement', 'procurement:overview', 'procurement:overview:list',
      'procurement:material', 'procurement:requisition',
      'procurement:material:list', 'procurement:material:create',
      'procurement:material:update', 'procurement:material:delete',
      'procurement:requisition:list', 'procurement:requisition:create',
      'procurement:requisition:update', 'procurement:requisition:delete',
      'procurement:requisition:submit', 'procurement:requisition:cancel',
      'procurement:rfq', 'procurement:purchase-order', 'procurement:goods-receipt',
      'procurement:rfq:list', 'procurement:rfq:create', 'procurement:rfq:update',
      'procurement:rfq:delete', 'procurement:rfq:send',
      'procurement:rfq:award', 'procurement:rfq:cancel',
      'procurement:purchase-order:list',
      'procurement:purchase-order:update', 'procurement:purchase-order:delete',
      'procurement:purchase-order:send', 'procurement:purchase-order:confirm',
      'procurement:purchase-order:cancel',
      'procurement:goods-receipt:list', 'procurement:goods-receipt:create',
      'procurement:goods-receipt:confirm'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code = 'EMPLOYEE'
  AND p.permission_code IN (
      'procurement', 'procurement:material', 'procurement:material:list',
      'procurement:requisition', 'procurement:requisition:list',
      'procurement:requisition:create', 'procurement:requisition:update',
      'procurement:requisition:delete', 'procurement:requisition:submit',
      'procurement:requisition:cancel'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('TEAM_LEADER', 'DEPT_LEADER')
  AND p.permission_code IN (
      'procurement', 'procurement:material', 'procurement:material:list',
      'procurement:requisition', 'procurement:requisition:list',
      'procurement:requisition:create', 'procurement:requisition:update',
      'procurement:requisition:delete', 'procurement:requisition:submit',
      'procurement:requisition:approve', 'procurement:requisition:cancel'
  );

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (100, 1, 1, '销售部', 'DEPT', 'sales-dept', '/1/100/', 2, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (101, 1, 100, '销售1组', 'TEAM', 'sales-team-1', '/1/100/101/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (102, 1, 100, '销售2组', 'TEAM', 'sales-team-2', '/1/100/102/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (200, 1, 1, '市场部', 'DEPT', 'marketing-dept', '/1/200/', 2, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (300, 1, 1, '财务部', 'DEPT', 'finance-dept', '/1/300/', 2, 3, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (301, 1, 300, '会计组', 'TEAM', 'finance-accounting', '/1/300/301/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (302, 1, 300, '审计组', 'TEAM', 'finance-audit', '/1/300/302/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (303, 1, 300, '税务组', 'TEAM', 'finance-tax', '/1/300/303/', 3, 3, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (400, 1, 1, '技术部', 'DEPT', 'tech-dept', '/1/400/', 2, 4, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (401, 1, 400, '前端组', 'TEAM', 'tech-frontend', '/1/400/401/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (402, 1, 400, '后端组', 'TEAM', 'tech-backend', '/1/400/402/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (403, 1, 400, '测试组', 'TEAM', 'tech-qa', '/1/400/403/', 3, 3, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (404, 1, 400, '运维组', 'TEAM', 'tech-ops', '/1/400/404/', 3, 4, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (500, 1, 1, '法律部', 'DEPT', 'legal-dept', '/1/500/', 2, 5, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (501, 1, 500, '合规组', 'TEAM', 'legal-compliance', '/1/500/501/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (502, 1, 500, '法务组', 'TEAM', 'legal-affairs', '/1/500/502/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (600, 1, 1, '人事部', 'DEPT', 'hr-dept', '/1/600/', 2, 6, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (601, 1, 600, '招聘组', 'TEAM', 'hr-recruit', '/1/600/601/', 3, 1, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (602, 1, 600, '培训组', 'TEAM', 'hr-training', '/1/600/602/', 3, 2, 1, 'system');

INSERT IGNORE INTO sys_org_unit (id, tenant_id, parent_id, name, type, unit_code, path, depth, sort, status, create_by)
VALUES (603, 1, 600, '薪酬组', 'TEAM', 'hr-compensation', '/1/600/603/', 3, 3, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (10, 1, 'EMPLOYEE', '普通员工', 'SELF', 10, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (11, 1, 'TEAM_LEADER', '工作组组长', 'DEPT', 11, 1, 'system');

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES (12, 1, 'DEPT_LEADER', '部门领导', 'DEPT_AND_BELOW', 12, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (100, 1, 'zhangsan', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '张三', 1, 101, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (101, 1, 'lisi', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '李四', 1, 101, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (102, 1, 'lisi2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '李四2', 1, 101, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (103, 1, 'wangwu', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '王五', 1, 102, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (104, 1, 'wangwu2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '王五2', 1, 102, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (105, 1, 'zhaoliu', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '赵六', 1, 100, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (106, 1, 'zhaoliu2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '赵六2', 1, 100, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (107, 1, 'qianqi', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '钱七', 1, 200, 1, 'system');

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, gender, primary_unit_id, status, create_by)
VALUES (108, 1, 'qianqi2', '$2b$10$b2rvxsi2LxHSZxzOuf4jOemAbYsmWGIWW/OhjzGBWWeg60lW/oLCa', '钱七2', 1, 200, 1, 'system');

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 100, 10, 101, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 101, 11, 101, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 102, 11, 101, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 103, 11, 102, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 104, 11, 102, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 105, 12, 100, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 106, 12, 100, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 107, 12, 200, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role_scope (tenant_id, user_id, role_id, unit_id, scope_mode, status) VALUES (1, 108, 12, 200, 'SAME_UNIT', 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (100, 10);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (101, 11);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (102, 11);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (103, 11);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (104, 11);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (105, 12);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (106, 12);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (107, 12);

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (108, 12);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (100, 101, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (101, 101, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (102, 101, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (103, 102, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (104, 102, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (105, 100, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (106, 100, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (107, 200, 1);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (108, 200, 1);

INSERT IGNORE INTO sys_user (id, tenant_id, username, password, nickname, email, phone, gender, primary_unit_id, status, create_by)
VALUES (200, 1, 'supplier1', '$2b$10$TWIuwQVfxgsioXe/2O9cgOXtuZwOREr1IBkgTj2.nhA1NSlnad1oa', '王建国', 'wangjianguo@huaxin-precision.com', '13912345678', 1, 1, 1, 'system');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (200, 33);

INSERT IGNORE INTO sys_user_unit (user_id, unit_id, is_primary) VALUES (200, 1, 1);

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (40, 1, 'ASSET_ADMIN',   '资产管理员', 'TENANT',         40, 1, 'system'),
    (41, 1, 'ASSET_MANAGER', '资产经理',   'DEPT_AND_BELOW', 41, 1, 'system'),
    (42, 1, 'ASSET_USER',    '资产使用人', 'SELF',           42, 1, 'system');

INSERT IGNORE INTO sys_permission
    (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (700, 1,   0, 'asset',                       '资产管理',         'DIRECTORY', '/700/',         1, 9, 1, 'system'),
    (710, 1, 700, 'asset:overview',              '资产概览',         'MENU',      '/700/710/',     2, 1, 1, 'system'),
    (711, 1, 710, 'asset:overview:list',         '查看资产概览',     'API',       '/700/710/711/', 3, 1, 1, 'system'),
    (720, 1, 700, 'asset:asset',                 '资产台账',         'MENU',      '/700/720/',     2, 2, 1, 'system'),
    (721, 1, 720, 'asset:asset:list',            '查看资产台账',     'API',       '/700/720/721/', 3, 1, 1, 'system'),
    (722, 1, 720, 'asset:asset:self',            '查看我的资产',     'API',       '/700/720/722/', 3, 2, 1, 'system'),
    (723, 1, 720, 'asset:asset:create',          '创建资产',         'API',       '/700/720/723/', 3, 3, 1, 'system'),
    (724, 1, 720, 'asset:asset:update',          '更新资产',         'API',       '/700/720/724/', 3, 4, 1, 'system'),
    (725, 1, 720, 'asset:asset:delete',          '删除资产',         'API',       '/700/720/725/', 3, 5, 1, 'system'),
    (726, 1, 720, 'asset:asset:allocate',        '分配资产',         'API',       '/700/720/726/', 3, 6, 1, 'system'),
    (727, 1, 720, 'asset:asset:accept',          '确认领用资产',     'API',       '/700/720/727/', 3, 7, 1, 'system'),
    (728, 1, 720, 'asset:asset:return',          '退还资产',         'API',       '/700/720/728/', 3, 8, 1, 'system'),
    (729, 1, 720, 'asset:asset:maintenance',     '维护资产状态',     'API',       '/700/720/729/', 3, 9, 1, 'system'),
    (740, 1, 700, 'asset:transfer',              '资产调拨',         'MENU',      '/700/740/',     2, 3, 1, 'system'),
    (741, 1, 740, 'asset:transfer:list',         '查看资产调拨',     'API',       '/700/740/741/', 3, 1, 1, 'system'),
    (742, 1, 740, 'asset:transfer:create',       '创建资产调拨',     'API',       '/700/740/742/', 3, 2, 1, 'system'),
    (743, 1, 740, 'asset:transfer:approve',      '审批资产调拨视图', 'API',       '/700/740/743/', 3, 3, 1, 'system'),
    (744, 1, 740, 'asset:transfer:complete',     '完成资产调拨',     'API',       '/700/740/744/', 3, 4, 1, 'system'),
    (745, 1, 740, 'asset:transfer:cancel',       '取消资产调拨',     'API',       '/700/740/745/', 3, 5, 1, 'system'),
    (746, 1, 740, 'asset:transfer:retry',        '重试调拨流程',     'API',       '/700/740/746/', 3, 6, 1, 'system'),
    (750, 1, 700, 'asset:disposal',              '资产处置',         'MENU',      '/700/750/',     2, 4, 1, 'system'),
    (751, 1, 750, 'asset:disposal:list',         '查看资产处置',     'API',       '/700/750/751/', 3, 1, 1, 'system'),
    (752, 1, 750, 'asset:disposal:create',       '创建资产处置',     'API',       '/700/750/752/', 3, 2, 1, 'system'),
    (753, 1, 750, 'asset:disposal:approve',      '审批资产处置视图', 'API',       '/700/750/753/', 3, 3, 1, 'system'),
    (754, 1, 750, 'asset:disposal:complete',     '完成资产处置',     'API',       '/700/750/754/', 3, 4, 1, 'system'),
    (755, 1, 750, 'asset:disposal:cancel',       '取消资产处置',     'API',       '/700/750/755/', 3, 5, 1, 'system'),
    (756, 1, 750, 'asset:disposal:retry',        '重试处置流程',     'API',       '/700/750/756/', 3, 6, 1, 'system');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = 1
  AND role.role_code IN ('SUPER_ADMIN', 'ASSET_ADMIN', 'ASSET_MANAGER')
  AND (permission.permission_code = 'asset' OR permission.permission_code LIKE 'asset:%');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = 1
  AND role.role_code IN ('ASSET_ADMIN', 'ASSET_MANAGER')
  AND permission.permission_code IN (
      'workflow', 'workflow:instance', 'workflow:task:todo',
      'workflow:approval:complete', 'workflow:model:list'
  );

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = 1 AND role.role_code = 'ASSET_USER'
  AND permission.permission_code IN (
      'asset', 'asset:asset', 'asset:asset:self',
      'asset:asset:accept', 'asset:asset:return'
  );
