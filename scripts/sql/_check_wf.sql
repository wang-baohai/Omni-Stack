SET NAMES utf8mb4;

-- Flowable 流程实例数据
SELECT COUNT(*) as cnt, 'ACT_RU_EXECUTION(running)' as tbl FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_ IS NOT NULL AND PARENT_ID_ IS NULL;
SELECT COUNT(*) as cnt, 'ACT_RU_TASK(active tasks)' as tbl FROM ACT_RU_TASK;
SELECT COUNT(*) as cnt, 'ACT_HI_PROCINST(all instances)' as tbl FROM ACT_HI_PROCINST;
SELECT COUNT(*) as cnt, 'ACT_HI_TASKINST(all tasks)' as tbl FROM ACT_HI_TASKINST;
SELECT COUNT(*) as cnt, 'ACT_HI_ACTINST(all activities)' as tbl FROM ACT_HI_ACTINST;
SELECT COUNT(*) as cnt, 'ACT_RE_PROCDEF(deployed defs)' as tbl FROM ACT_RE_PROCDEF;
SELECT COUNT(*) as cnt, 'ACT_RE_DEPLOYMENT(deployments)' as tbl FROM ACT_RE_DEPLOYMENT;

-- 查看 wf_process_instance
SELECT COUNT(*) as cnt, 'wf_process_instance' as tbl FROM wf_process_instance;
SELECT COUNT(*) as cnt, 'wf_model_version' as tbl FROM wf_process_model_version;

-- 如果有流程实例看看详情
SELECT id, process_instance_id, model_version_id, business_key, title, status FROM wf_process_instance ORDER BY id DESC LIMIT 10;
