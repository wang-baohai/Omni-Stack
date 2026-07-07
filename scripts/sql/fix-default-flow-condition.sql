-- 修复排他网关 default 分支带 conditionExpression
-- 策略：对每条 DRAFT 记录，用 LOCATE 定位 reject sequenceFlow，
-- 截取 ">" 之前的属性部分 + "/>" 闭合，丢弃中间的 conditionExpression

-- Step 1: 检查 reject 连线的精确位置
SELECT id,
  LOCATE('flow-l1-reject', bpmn_xml) as pos_l1,
  LOCATE('flow-l2-reject', bpmn_xml) as pos_l2,
  LOCATE('flow-l3-reject', bpmn_xml) as pos_l3
FROM wf_process_model_version WHERE status = 'DRAFT';
