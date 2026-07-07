-- 修复已发布记录的版本号：从 0 改为 1
UPDATE wf_process_model_version SET version = 1 WHERE status = 'PUBLISHED' AND version = 0;

SELECT id, tenant_id, version, status, engine_version FROM wf_process_model_version WHERE status = 'PUBLISHED';
