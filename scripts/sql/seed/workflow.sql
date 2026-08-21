-- Omni-Stack 正式幂等种子；由 09a29fe 基线数据机械提取并改为 INSERT IGNORE。
-- 结构由 Liquibase YAML 管理；本文件禁止包含 DDL、账号、授权或存储过程。

INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (1, 1, 'leave', '请假审批（3级会签）', 'leave', 1, 1, 'system');

INSERT IGNORE INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (1, 1, 1, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:omni="http://omni.com/workflow"
  id="Definitions_leave"
  targetNamespace="http://flowable.org/test"
  xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">

  <process id="leave" name="请假审批（3级会签）" isExecutable="true">
    <documentation>3级会签审批请假流程：直属领导 → 部门领导 → 跨部门领导</documentation>
    <startEvent id="start" name="提交请假申请" flowable:initiator="initiator" />
    <userTask id="direct-leader-approve" name="直属领导审批" flowable:assignee="${userId}">
      <documentation>发起人所在组织的组长（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"TEAM_LEADER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="第1级结果" default="flow-l1-reject" />
    <userTask id="dept-leader-approve" name="部门领导审批" flowable:assignee="${userId}">
      <documentation>发起人上级组织的部门领导（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level2" name="第2级结果" default="flow-l2-reject" />
    <userTask id="cross-dept-approve" name="跨部门领导审批" flowable:assignee="${userId}">
      <documentation>指定组织的领导（正副职）会签审批，设计者从全量组织树中选择</documentation>
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"ABSOLUTE_UNIT","anchorParams":{"unitIds":[200]},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level3" name="第3级结果" default="flow-l3-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="direct-leader-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="direct-leader-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="dept-leader-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
    <sequenceFlow id="flow-l2-to-gw" sourceRef="dept-leader-approve" targetRef="gw-level2" />
    <sequenceFlow id="flow-l2-pass" sourceRef="gw-level2" targetRef="cross-dept-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l2-reject" sourceRef="gw-level2" targetRef="end-rejected" />
    <sequenceFlow id="flow-l3-to-gw" sourceRef="cross-dept-approve" targetRef="gw-level3" />
    <sequenceFlow id="flow-l3-pass" sourceRef="gw-level3" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l3-reject" sourceRef="gw-level3" targetRef="end-rejected" />
  </process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_leave">
    <bpmndi:BPMNPlane id="BPMNPlane_leave" bpmnElement="leave">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="100" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="80" y="343" width="76" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="l1_di" bpmnElement="direct-leader-approve">
        <dc:Bounds x="200" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true">
        <dc:Bounds x="420" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="405" y="350" width="80" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="l2_di" bpmnElement="dept-leader-approve">
        <dc:Bounds x="540" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="gw2_di" bpmnElement="gw-level2" isMarkerVisible="true">
        <dc:Bounds x="760" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="745" y="350" width="80" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="l3_di" bpmnElement="cross-dept-approve">
        <dc:Bounds x="880" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="gw3_di" bpmnElement="gw-level3" isMarkerVisible="true">
        <dc:Bounds x="1100" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel><dc:Bounds x="1085" y="350" width="80" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved">
        <dc:Bounds x="1220" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="1208" y="343" width="60" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected">
        <dc:Bounds x="785" y="450" width="36" height="36" />
        <bpmndi:BPMNLabel><dc:Bounds x="773" y="493" width="60" height="14" /></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start">
        <di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw">
        <di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass">
        <di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject">
        <di:waypoint x="445" y="343" /><di:waypoint x="445" y="468" /><di:waypoint x="785" y="468" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l2_gw_di" bpmnElement="flow-l2-to-gw">
        <di:waypoint x="690" y="318" /><di:waypoint x="760" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l2_pass_di" bpmnElement="flow-l2-pass">
        <di:waypoint x="810" y="318" /><di:waypoint x="880" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l2_reject_di" bpmnElement="flow-l2-reject">
        <di:waypoint x="785" y="343" /><di:waypoint x="785" y="450" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l3_gw_di" bpmnElement="flow-l3-to-gw">
        <di:waypoint x="1030" y="318" /><di:waypoint x="1100" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l3_pass_di" bpmnElement="flow-l3-pass">
        <di:waypoint x="1150" y="318" /><di:waypoint x="1220" y="318" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_l3_reject_di" bpmnElement="flow-l3-reject">
        <di:waypoint x="1125" y="343" /><di:waypoint x="1125" y="468" /><di:waypoint x="821" y="468" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>',
NULL);

INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (2, 1, 'procurement-approval', '采购申请审批', 'purchase', 1, 2, 'system');

INSERT IGNORE INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (2, 1, 2, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_procurement_approval" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="procurement-approval" name="采购申请审批" isExecutable="true">
    <documentation>采购经理审批采购申请</documentation>
    <startEvent id="start" name="提交" flowable:initiator="initiator" />
    <userTask id="proc-manager-approve" name="采购经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"PROCUREMENT_MANAGER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="审批结果" default="flow-l1-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="proc-manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="proc-manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_procurement_approval"><bpmndi:BPMNPlane id="BPMNPlane_procurement_approval" bpmnElement="procurement-approval">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="proc-manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="540" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="445" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="450" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (3, 1, 'asset-transfer', '资产调拨审批', 'ASSET_TRANSFER', 1, 3, 'system');

INSERT IGNORE INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (3, 1, 3, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_asset_transfer" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="asset-transfer" name="资产调拨审批" isExecutable="true">
    <documentation>资产经理审批资产调拨</documentation>
    <startEvent id="start" name="提交" flowable:initiator="initiator" />
    <userTask id="asset-manager-approve" name="资产经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"ASSET_MANAGER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="审批结果" default="flow-l1-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="asset-manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="asset-manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_asset_transfer"><bpmndi:BPMNPlane id="BPMNPlane_asset_transfer" bpmnElement="asset-transfer">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="asset-manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="540" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="445" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="450" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (4, 1, 'asset-disposal', '资产处置审批', 'ASSET_DISPOSAL', 1, 4, 'system');

INSERT IGNORE INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (4, 1, 4, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_asset_disposal" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="asset-disposal" name="资产处置审批" isExecutable="true">
    <documentation>资产经理审批资产处置</documentation>
    <startEvent id="start" name="提交" flowable:initiator="initiator" />
    <userTask id="asset-manager-approve" name="资产经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"ASSET_MANAGER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="审批结果" default="flow-l1-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="asset-manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="asset-manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_asset_disposal"><bpmndi:BPMNPlane id="BPMNPlane_asset_disposal" bpmnElement="asset-disposal">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="asset-manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="540" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="445" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="450" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);

INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (5, 1, 'supplier-onboarding', '供应商准入审批', 'SRM_SUPPLIER_ONBOARDING', 1, 5, 'system');

INSERT IGNORE INTO wf_process_model_version (id, tenant_id, model_id, version, status, bpmn_xml, designer_json)
VALUES (5, 1, 5, 1, 'DRAFT',
'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="http://flowable.org/bpmn" xmlns:omni="http://omni.com/workflow" id="Definitions_supplier_onboarding" targetNamespace="http://flowable.org/test" xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">
  <process id="supplier-onboarding" name="供应商准入审批（3级会签）" isExecutable="true">
    <documentation>3级会签审批供应商准入流程：采购经理 → 合规负责人 → 高管</documentation>
    <startEvent id="start" name="提交供应商准入" flowable:initiator="initiator" />
    <userTask id="manager-approve" name="采购经理审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"SRM_MANAGER","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level1" name="第1级结果" default="flow-l1-reject" />
    <userTask id="compliance-approve" name="合规负责人审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"SRM_COMPLIANCE","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level2" name="第2级结果" default="flow-l2-reject" />
    <userTask id="director-approve" name="高管审批" flowable:assignee="${userId}">
      <extensionElements>
        <flowable:executionListener event="start" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"SRM_DIRECTOR","anchorType":"ABSOLUTE_UNIT","anchorParams":{"unitIds":[1]},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="candidateUserIds" flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <exclusiveGateway id="gw-level3" name="第3级结果" default="flow-l3-reject" />
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="manager-approve" />
    <sequenceFlow id="flow-l1-to-gw" sourceRef="manager-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="compliance-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected" />
    <sequenceFlow id="flow-l2-to-gw" sourceRef="compliance-approve" targetRef="gw-level2" />
    <sequenceFlow id="flow-l2-pass" sourceRef="gw-level2" targetRef="director-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l2-reject" sourceRef="gw-level2" targetRef="end-rejected" />
    <sequenceFlow id="flow-l3-to-gw" sourceRef="director-approve" targetRef="gw-level3" />
    <sequenceFlow id="flow-l3-pass" sourceRef="gw-level3" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l3-reject" sourceRef="gw-level3" targetRef="end-rejected" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_supplier_onboarding"><bpmndi:BPMNPlane id="BPMNPlane_supplier_onboarding" bpmnElement="supplier-onboarding">
    <bpmndi:BPMNShape id="start_di" bpmnElement="start"><dc:Bounds x="100" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l1_di" bpmnElement="manager-approve"><dc:Bounds x="200" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true"><dc:Bounds x="420" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l2_di" bpmnElement="compliance-approve"><dc:Bounds x="540" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw2_di" bpmnElement="gw-level2" isMarkerVisible="true"><dc:Bounds x="760" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="l3_di" bpmnElement="director-approve"><dc:Bounds x="880" y="278" width="150" height="80" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="gw3_di" bpmnElement="gw-level3" isMarkerVisible="true"><dc:Bounds x="1100" y="293" width="50" height="50" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved"><dc:Bounds x="1220" y="300" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected"><dc:Bounds x="785" y="450" width="36" height="36" /></bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start"><di:waypoint x="136" y="318" /><di:waypoint x="200" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw"><di:waypoint x="350" y="318" /><di:waypoint x="420" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass"><di:waypoint x="470" y="318" /><di:waypoint x="540" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject"><di:waypoint x="445" y="343" /><di:waypoint x="445" y="468" /><di:waypoint x="785" y="468" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l2_gw_di" bpmnElement="flow-l2-to-gw"><di:waypoint x="690" y="318" /><di:waypoint x="760" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l2_pass_di" bpmnElement="flow-l2-pass"><di:waypoint x="810" y="318" /><di:waypoint x="880" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l2_reject_di" bpmnElement="flow-l2-reject"><di:waypoint x="785" y="343" /><di:waypoint x="785" y="450" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l3_gw_di" bpmnElement="flow-l3-to-gw"><di:waypoint x="1030" y="318" /><di:waypoint x="1100" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l3_pass_di" bpmnElement="flow-l3-pass"><di:waypoint x="1150" y="318" /><di:waypoint x="1220" y="318" /></bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="flow_l3_reject_di" bpmnElement="flow-l3-reject"><di:waypoint x="1125" y="343" /><di:waypoint x="1125" y="468" /><di:waypoint x="821" y="468" /></bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>
</definitions>',
NULL);
