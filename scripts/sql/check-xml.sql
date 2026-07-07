SET NAMES utf8mb4;
USE omni_workflow;
SELECT SUBSTRING(bpmn_xml, 1, 500) as xml_head FROM wf_process_model_version WHERE id=3;