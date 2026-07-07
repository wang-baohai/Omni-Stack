UPDATE wf_process_model_version
SET bpmn_xml = '<?xml version="1.0" encoding="UTF-8"?>
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

  <process id="leave-approval" name="请假审批（4级会签）" isExecutable="true">
    <documentation>4级会签审批请假流程：直属领导 → 同部门其他组领导 → 部门领导 → 跨部门领导</documentation>

    <!-- 开始事件 -->
    <startEvent id="start" name="提交请假申请" />

    <!-- ========== 第1级：直属领导审批（后端1组组长，会签） ========== -->
    <userTask id="direct-leader-approve" name="直属领导审批">
      <documentation>后端1组的组长（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"TEAM_LEADER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) &gt; 0 || approvedCount &gt;= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <!-- 排他网关：第1级结果判断 -->
    <exclusiveGateway id="gw-level1" name="第1级结果" />

    <!-- ========== 第2级：同部门其他组领导审批（架构1组组长，会签） ========== -->
    <userTask id="sibling-leader-approve" name="同部门其他组领导审批">
      <documentation>架构1组的组长（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"TEAM_LEADER","anchorType":"SIBLING_BY_CODE","anchorParams":{"siblingCode":"arch-1"},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) &gt; 0 || approvedCount &gt;= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <exclusiveGateway id="gw-level2" name="第2级结果" />

    <!-- ========== 第3级：部门领导审批（技术研发部领导，会签） ========== -->
    <userTask id="dept-leader-approve" name="部门领导审批">
      <documentation>技术研发部的领导（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"PARENT_BY_TYPE","anchorParams":{"targetType":"DEPT"},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) &gt; 0 || approvedCount &gt;= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <exclusiveGateway id="gw-level3" name="第3级结果" />

    <!-- ========== 第4级：跨部门领导审批（人事部领导，会签） ========== -->
    <userTask id="cross-dept-approve" name="跨部门领导审批">
      <documentation>人事部的领导（正副职）会签审批</documentation>
      <extensionElements>
        <flowable:taskListener event="create" delegateExpression="${scopedRoleAssignmentListener}" />
        <omni:assignment>{"roleCode":"DEPT_LEADER","anchorType":"DEPT_BY_CODE","anchorParams":{"deptCode":"hr-dept"},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ALL"}</omni:assignment>
        <flowable:executionListener event="start" expression="${execution.setVariable(''approvedCount'', 0)}" />
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="candidateUserIds"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">${sum(!approved ? 1 : 0) &gt; 0 || approvedCount &gt;= nrOfInstances}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>

    <exclusiveGateway id="gw-level4" name="第4级结果" />

    <!-- 结束事件 -->
    <endEvent id="end-approved" name="审批通过" />
    <endEvent id="end-rejected" name="审批驳回" />

    <!-- ===== 流程连线 ===== -->
    <sequenceFlow id="flow-start" sourceRef="start" targetRef="direct-leader-approve" />

    <sequenceFlow id="flow-l1-to-gw" sourceRef="direct-leader-approve" targetRef="gw-level1" />
    <sequenceFlow id="flow-l1-pass" sourceRef="gw-level1" targetRef="sibling-leader-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l1-reject" sourceRef="gw-level1" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow id="flow-l2-to-gw" sourceRef="sibling-leader-approve" targetRef="gw-level2" />
    <sequenceFlow id="flow-l2-pass" sourceRef="gw-level2" targetRef="dept-leader-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l2-reject" sourceRef="gw-level2" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow id="flow-l3-to-gw" sourceRef="dept-leader-approve" targetRef="gw-level3" />
    <sequenceFlow id="flow-l3-pass" sourceRef="gw-level3" targetRef="cross-dept-approve">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l3-reject" sourceRef="gw-level3" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow id="flow-l4-to-gw" sourceRef="cross-dept-approve" targetRef="gw-level4" />
    <sequenceFlow id="flow-l4-pass" sourceRef="gw-level4" targetRef="end-approved">
      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="flow-l4-reject" sourceRef="gw-level4" targetRef="end-rejected">
      <conditionExpression xsi:type="tFormalExpression">${approved == false}</conditionExpression>
    </sequenceFlow>

  </process>

  <!-- BPMN 图形布局 -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_leave">
    <bpmndi:BPMNPlane id="BPMNPlane_leave" bpmnElement="leave-approval">

      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="100" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="80" y="343" width="76" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="l1_di" bpmnElement="direct-leader-approve">
        <dc:Bounds x="200" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="gw1_di" bpmnElement="gw-level1" isMarkerVisible="true">
        <dc:Bounds x="420" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="405" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="l2_di" bpmnElement="sibling-leader-approve">
        <dc:Bounds x="540" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="gw2_di" bpmnElement="gw-level2" isMarkerVisible="true">
        <dc:Bounds x="760" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="745" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="l3_di" bpmnElement="dept-leader-approve">
        <dc:Bounds x="880" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="gw3_di" bpmnElement="gw-level3" isMarkerVisible="true">
        <dc:Bounds x="1100" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1085" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="l4_di" bpmnElement="cross-dept-approve">
        <dc:Bounds x="1220" y="278" width="150" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="gw4_di" bpmnElement="gw-level4" isMarkerVisible="true">
        <dc:Bounds x="1440" y="293" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1425" y="350" width="80" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="end_approved_di" bpmnElement="end-approved">
        <dc:Bounds x="1560" y="300" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1548" y="343" width="60" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="end_rejected_di" bpmnElement="end-rejected">
        <dc:Bounds x="785" y="450" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="773" y="493" width="60" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNEdge id="flow_start_di" bpmnElement="flow-start">
        <di:waypoint x="136" y="318" />
        <di:waypoint x="200" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l1_gw_di" bpmnElement="flow-l1-to-gw">
        <di:waypoint x="350" y="318" />
        <di:waypoint x="420" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l1_pass_di" bpmnElement="flow-l1-pass">
        <di:waypoint x="470" y="318" />
        <di:waypoint x="540" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l1_reject_di" bpmnElement="flow-l1-reject">
        <di:waypoint x="445" y="343" />
        <di:waypoint x="445" y="468" />
        <di:waypoint x="785" y="468" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l2_gw_di" bpmnElement="flow-l2-to-gw">
        <di:waypoint x="690" y="318" />
        <di:waypoint x="760" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l2_pass_di" bpmnElement="flow-l2-pass">
        <di:waypoint x="810" y="318" />
        <di:waypoint x="880" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l2_reject_di" bpmnElement="flow-l2-reject">
        <di:waypoint x="785" y="343" />
        <di:waypoint x="785" y="450" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l3_gw_di" bpmnElement="flow-l3-to-gw">
        <di:waypoint x="1030" y="318" />
        <di:waypoint x="1100" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l3_pass_di" bpmnElement="flow-l3-pass">
        <di:waypoint x="1150" y="318" />
        <di:waypoint x="1220" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l3_reject_di" bpmnElement="flow-l3-reject">
        <di:waypoint x="1125" y="343" />
        <di:waypoint x="1125" y="468" />
        <di:waypoint x="821" y="468" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l4_gw_di" bpmnElement="flow-l4-to-gw">
        <di:waypoint x="1370" y="318" />
        <di:waypoint x="1440" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l4_pass_di" bpmnElement="flow-l4-pass">
        <di:waypoint x="1490" y="318" />
        <di:waypoint x="1560" y="318" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_l4_reject_di" bpmnElement="flow-l4-reject">
        <di:waypoint x="1465" y="343" />
        <di:waypoint x="1465" y="468" />
        <di:waypoint x="821" y="468" />
      </bpmndi:BPMNEdge>

    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>'
WHERE id = 3;
