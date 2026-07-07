SET NAMES utf8mb4;
USE omni_workflow;
SELECT SUBSTRING(bpmn_xml, LOCATE('process id', bpmn_xml), 80) as process_snippet FROM wf_process_model_version WHERE id=3;