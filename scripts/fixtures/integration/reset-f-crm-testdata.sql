-- WP-008 FC 运行前重置：清空 tenant 2002 的事务型 CRM 测试数据，保证计数断言跨运行确定性。
-- 保留：crm_tenant_config / crm_pipeline / crm_pipeline_stage（tenant 2002 种子配置）与全部 tenant=1 种子
-- （FC 跨租户负例读取 tenant-1 crm_lead/crm_customer）。仅作用于 F 隔离库 omni_crm。
-- 子表先删，避免潜在外键顺序问题；sys_mq_message 仅清 tenant 2002（relay 在测试内已禁用）。
SET FOREIGN_KEY_CHECKS := 0;
DELETE FROM crm_opportunity_stage_history WHERE tenant_id = 2002;
DELETE FROM crm_activity                  WHERE tenant_id = 2002;
DELETE FROM crm_opportunity                WHERE tenant_id = 2002;
DELETE FROM crm_contact                     WHERE tenant_id = 2002;
DELETE FROM crm_lead_conversion             WHERE tenant_id = 2002;
DELETE FROM crm_lead                        WHERE tenant_id = 2002;
DELETE FROM crm_customer                    WHERE tenant_id = 2002;
DELETE FROM crm_owner_change_log              WHERE tenant_id = 2002;
DELETE FROM sys_mq_message                  WHERE tenant_id = 2002;
SET FOREIGN_KEY_CHECKS := 1;
