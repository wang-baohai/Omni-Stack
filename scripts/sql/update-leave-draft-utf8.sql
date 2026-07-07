SET NAMES utf8mb4;
USE omni_workflow;
UPDATE wf_process_model_version SET bpmn_xml = N'<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN20"
  xmlns:bpmndi="http://www.omg.org/spec/BPMNDI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:omni="http://omni.com/workflow"
  id="Definitions_leave"
  targetNamespace="http://flowable.org/test"
  xsi:schemaLocation="http://www.omg.org/spec/BPMN20 http://www.omg.org/spec/BPMN20/bpmn20.xsd">

  <process id="leave-approval" name="请假审批（3级会签）" isExecutable="true">
    <documentation>3级会签审批请假流程：直属领导 → 部门领导 → 跨部门领导</documentation>

    <!-- 开始事件 -->
    <startEvent id="start" name="提交请假申请" />

    <!-- ========== 第1级：直属领导审批（发起人所在组织 TEAM_LEADER，会签） ========== -->
    <userTask id="direct-leader-approve" name="直属领导审批">
      <documentation>发起人所在组织的组长（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"TEAM_LEADER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) > 0 || approvedCount >= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <!-- 排他网关：第1级结果判断 -->
    <exclusiveGateway id="gw-level1" name="第1级结果" />

    <!-- ========== 第2级：部门领导审批（上级组织，角色自选，会签） ========== -->
    <userTask id="dept-leader-approve" name="部门领导审批">
      <documentation>发起人上级组织的部门领导（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"PARENT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) > 0 || approvedCount >= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <exclusiveGateway id="gw-level2" name="第2级结果" />

    <!-- ========== 第3级：跨部门领导审批（指定组织，角色自选，会签） ========== -->
    <userTask id="cross-dept-approve" name="跨部门领导审批">
      <documentation>指定组织的领导（正副职）会签审批，设计者从全量组织树中选择</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"ABSOLUTE_UNIT","anchorParams":{"unitIds":[5]},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) > 0 || approvedCount >= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <exclusiveGateway id="gw-level3" name="第3级结果" />

    <!-- 结束事件 -->
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />

    <!-- ===== 流程连线 ===== -->

    <!-- 开始 → 第1级 -->
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="direct-leader-approve" />

    <!-- 第1级 → 网关1 -->
    <sequenceFlow id="flow-l1-to-gw" sourceRef="direct-leader-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="dept-leader-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

    <!-- 第2级 → 网关2 -->
    <sequenceFlow id="flow-l2-to-gw" sourceRef="dept-leader-approve" targetRef="gw-level2" />
    <sequenceFlow id="flow-l2-pass" sourceRef="gw-level2" targetRef="cross-dept-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l2-reject" sourceRef="gw-level2" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

    <!-- 第3级 → 网关3 -->
    <sequenceFlow id="flow-l3-to-gw" sourceRef="cross-dept-approve" targetRef="gw-level3" />
    <sequenceFlow id="flow-l3-pass" sourceRef="gw-level3" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l3-reject" sourceRef="gw-level3" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

  </process>

  <!-- BPMN 图形布局 -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_leave">
    <bpmndi:BPMNPlane id="BPMNPlane_leave" bpmnElement="leave-approval">

      <!-- 开始事件 -->
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="100" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="80" y="343" width="76" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- 第1级：直属领导 -->
      <bpmndi:BPMNShape id="l1_di" bpmnElement="direct-leader-approve">
        <dc:Bounds x="200" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <!-- 网关1 -->
      <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true">
        <dc:Bounds x="420" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="405" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- 第2级：部门领导 -->
      <bpmndi:BPMNShape id="l2_di" bpmnElement="dept-leader-approve">
        <dc:Bounds x="540" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <!-- 网关2 -->
      <bpmndi:BPMNShape id="gw2_di" bpmnElement="gw-level2" isMarkerVisible="true">
        <dc:Bounds x="760" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="745" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- 第3级：跨部门领导 -->
      <bpmndi:BPMNShape id="l3_di" bpmnElement="cross-dept-approve">
        <dc:Bounds x="880" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <!-- 网关3 -->
      <bpmndi:BPMNShape id="gw3_di" bpmnElement="gw-level3" isMarkerVisible="true">
        <dc:Bounds x="1100" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1085" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- 审批通过 -->
      <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved">
        <dc:Bounds x="1220" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1208" y="343" width="60" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- 审批驳回 -->
      <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected">
        <dc:Bounds x="785" y="450" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="773" y="493" width="60" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <!-- 连线：开始 → 第1级 -->
      <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start">
        <di:waypoint x="136" y="318" />
        <di:waypoint x="200" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 第1级 → 网关1 -->
      <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw">
        <di:waypoint x="350" y="318" />
        <di:waypoint x="420" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 网关1 → 第2级（通过） -->
      <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass">
        <di:waypoint x="470" y="318" />
        <di:waypoint x="540" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 网关1 → 驳回 -->
      <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject">
        <di:waypoint x="445" y="343" />
        <di:waypoint x="445" y="468" />
        <di:waypoint x="785" y="468" />
      </bpmndi:BPMNEdge>

      <!-- 第2级 → 网关2 -->
      <bpmndi:BPMNEdge id="flow_l2_gw_di" bpmnElement="flow-l2-to-gw">
        <di:waypoint x="690" y="318" />
        <di:waypoint x="760" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 网关2 → 第3级（通过） -->
      <bpmndi:BPMNEdge id="flow_l2_pass_di" bpmnElement="flow-l2-pass">
        <di:waypoint x="810" y="318" />
        <di:waypoint x="880" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 网关2 → 驳回 -->
      <bpmndi:BPMNEdge id="flow_l2_reject_di" bpmnElement="flow-l2-reject">
        <di:waypoint x="785" y="343" />
        <di:waypoint x="785" y="450" />
      </bpmndi:BPMNEdge>

      <!-- 第3级 → 网关3 -->
      <bpmndi:BPMNEdge id="flow_l3_gw_di" bpmnElement="flow-l3-to-gw">
        <di:waypoint x="1030" y="318" />
        <di:waypoint x="1100" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 网关3 → 审批通过 -->
      <bpmndi:BPMNEdge id="flow_l3_pass_di" bpmnElement="flow-l3-pass">
        <di:waypoint x="1150" y="318" />
        <di:waypoint x="1220" y="318" />
      </bpmndi:BPMNEdge>

      <!-- 网关3 → 驳回 -->
      <bpmndi:BPMNEdge id="flow_l3_reject_di" bpmnElement="flow-l3-reject">
        <di:waypoint x="1125" y="343" />
        <di:waypoint x="1125" y="468" />
        <di:waypoint x="821" y="468" />
      </bpmndi:BPMNEdge>

    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>
' WHERE id = 3;
