SET NAMES utf8mb4;
SELECT LEFT(bpmn_xml, 600) AS header FROM omni_workflow.wf_process_model_version WHERE model_id = 3 ORDER BY update_time DESC LIMIT 1;