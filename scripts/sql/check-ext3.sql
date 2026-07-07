SET NAMES utf8mb4;
SELECT SUBSTRING(bpmn_xml, LOCATE('direct-leader-approve', bpmn_xml) - 50, 600) AS snippet FROM omni_workflow.wf_process_model_version WHERE id = 3;