-- Omni-Stack 正式幂等种子；由 09a29fe 基线数据机械提取并改为 INSERT IGNORE。
-- 结构由 Liquibase YAML 管理；本文件禁止包含 DDL、账号、授权或存储过程。

INSERT IGNORE INTO proc_tenant_config
    (tenant_id, currency_code, initialized_time, version, deleted, create_by)
VALUES
    (1, 'CNY', NOW(), 0, 0, 'system')
AS new;

INSERT IGNORE INTO proc_material_category
    (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
VALUES
    (1, 0, 'IT_DEVICE',     'IT 设备', 10, 1, 0, 0, 'system'),
    (1, 0, 'OFFICE_SUPPLY', '办公用品', 20, 1, 0, 0, 'system'),
    (1, 0, 'RAW_MATERIAL',  '原材料', 30, 1, 0, 0, 'system'),
    (1, 0, 'OTHER',         '其他', 40, 1, 0, 0, 'system')
AS new;

INSERT IGNORE INTO proc_material_category
    (tenant_id, parent_id, category_code, category_name, sort, status, version, deleted, create_by)
SELECT 1, c.id, v.code, v.name, v.sort, 1, 0, 0, 'system'
FROM proc_material_category c
CROSS JOIN (
    SELECT 'IT_DEVICE' AS parent_code, 'LAPTOP' AS code, '笔记本电脑' AS name, 10 AS sort
    UNION ALL SELECT 'IT_DEVICE', 'MONITOR', '显示器', 20
    UNION ALL SELECT 'IT_DEVICE', 'PERIPHERAL', '外设配件', 30
    UNION ALL SELECT 'OFFICE_SUPPLY', 'STATIONERY', '文具', 10
    UNION ALL SELECT 'OFFICE_SUPPLY', 'PAPER', '纸张耗材', 20
    UNION ALL SELECT 'RAW_MATERIAL', 'METAL', '金属材料', 10
    UNION ALL SELECT 'RAW_MATERIAL', 'ELECTRONIC', '电子元器件', 20
    UNION ALL SELECT 'RAW_MATERIAL', 'PLASTIC', '塑料材料', 30
    UNION ALL SELECT 'OTHER', 'SERVICE', '服务', 10
) v
WHERE c.tenant_id = 1 AND c.parent_id = 0 AND c.category_code = v.parent_code AND c.deleted = 0;
