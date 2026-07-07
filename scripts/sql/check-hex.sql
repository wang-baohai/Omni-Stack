SELECT id,
  bpmn_xml LIKE '%exclusiveGateway id="gw-level1" default="flow-l1-reject"%' as gw1_default,
  bpmn_xml LIKE '%exclusiveGateway id="gw-level2" default="flow-l2-reject"%' as gw2_default,
  bpmn_xml LIKE '%exclusiveGateway id="gw-level3" default="flow-l3-reject"%' as gw3_default,
  bpmn_xml LIKE '%flow-l1-reject%conditionExpression%' as l1_cond,
  bpmn_xml LIKE '%flow-l2-reject%conditionExpression%' as l2_cond,
  bpmn_xml LIKE '%flow-l3-reject%conditionExpression%' as l3_cond
FROM wf_process_model_version WHERE id IN (3, 4);
