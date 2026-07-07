-- 查看当前 reject 流程连线的实际内容
SELECT id, 
  SUBSTRING(bpmn_xml, LOCATE('sequenceFlow id="flow-l1-reject"', bpmn_xml), 200) as reject1
FROM wf_process_model_version WHERE id = 3;
