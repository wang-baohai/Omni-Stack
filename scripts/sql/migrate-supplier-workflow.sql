-- ============================================================
-- 供应商准入工作流改造 — 增量迁移脚本
-- ============================================================

-- 1. srm_supplier 表增加工作流字段
USE omni_srm;

-- 使用存储过程安全添加列（幂等）
DROP PROCEDURE IF EXISTS add_supplier_wf_columns;
DELIMITER $$
CREATE PROCEDURE add_supplier_wf_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='omni_srm' AND table_name='srm_supplier' AND column_name='workflow_request_id') THEN
        ALTER TABLE srm_supplier
          ADD COLUMN workflow_request_id VARCHAR(64) DEFAULT NULL COMMENT 'Workflow启动请求幂等键',
          ADD COLUMN workflow_business_key VARCHAR(128) DEFAULT NULL COMMENT 'Workflow业务键 格式supplierId:attempt',
          ADD COLUMN workflow_model_version_id BIGINT DEFAULT NULL COMMENT '本轮选定的已发布流程模型版本ID',
          ADD COLUMN process_instance_id VARCHAR(64) DEFAULT NULL COMMENT 'Workflow流程实例ID',
          ADD COLUMN workflow_start_status VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/PENDING/FAILED/STARTED',
          ADD COLUMN workflow_completed_time DATETIME DEFAULT NULL COMMENT 'Workflow审批完成时间',
          ADD COLUMN approval_attempt INT NOT NULL DEFAULT 0 COMMENT '当前审批轮次',
          ADD COLUMN approved_time DATETIME DEFAULT NULL COMMENT '审批通过时间';
    END IF;
END$$
DELIMITER ;
CALL add_supplier_wf_columns();
DROP PROCEDURE IF EXISTS add_supplier_wf_columns;

-- 2. srm_event_inbox 表
CREATE TABLE IF NOT EXISTS srm_event_inbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    source_service  VARCHAR(64) NOT NULL,
    aggregate_type  VARCHAR(64) DEFAULT NULL,
    aggregate_id    VARCHAR(128) DEFAULT NULL,
    payload         JSON NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    processed_time  DATETIME DEFAULT NULL,
    error_message   VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_srm_inbox_event (tenant_id, event_id),
    INDEX idx_srm_inbox_status (tenant_id, status, create_time),
    CONSTRAINT chk_srm_inbox_status CHECK (status IN ('RECEIVED','PROCESSED','IGNORED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SRM领域事件收件箱';

-- 3. 新增3个审批角色
USE omni_auth;

INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, data_scope, sort, status, create_by)
VALUES
    (34, 1, 'SRM_MANAGER',     '采购经理',     'DEPT_AND_BELOW', 34, 1, 'system'),
    (35, 1, 'SRM_COMPLIANCE',  '合规负责人',   'DEPT_AND_BELOW', 35, 1, 'system'),
    (36, 1, 'SRM_DIRECTOR',    '高管',         'TENANT',         36, 1, 'system');

-- 4. 权限: approve/reject → withdraw/cancel
UPDATE sys_permission SET permission_code = 'srm:supplier:withdraw', permission_name = '撤回审批流程'
WHERE id = 415 AND tenant_id = 1;

UPDATE sys_permission SET permission_code = 'srm:supplier:cancel', permission_name = '取消审批流程'
WHERE id = 416 AND tenant_id = 1;

-- 5. 工作流分类字典新增 supplier
USE omni_base;
INSERT IGNORE INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, sort, status, create_by)
VALUES (1, 'workflow_category', 'supplier', '供应商审批', 8, 1, 'system');

-- 6. 供应商准入审批流程模型
USE omni_workflow;

INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key, model_name, category, status, current_draft_version_id, create_by)
VALUES (5, 1, 'supplier-onboarding', '供应商准入审批', 'supplier', 1, 5, 'system');

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

-- 7. 角色权限更新: SUPER_ADMIN/SRM_ADMIN/PROCUREMENT_MANAGER 获得 withdraw/cancel
USE omni_auth;
-- 先删除旧的 approve/reject 权限分配
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'SRM_ADMIN', 'PROCUREMENT_MANAGER')
  AND p.permission_code IN ('srm:supplier:approve', 'srm:supplier:reject');

DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.tenant_id = 1
  AND r.role_code = 'PROCUREMENT_STAFF'
  AND p.permission_code IN ('srm:supplier:approve', 'srm:supplier:reject');

-- 重新分配: 使用动态查询，让 srm:supplier:withdraw 和 srm:supplier:cancel 自动被 'srm:%' 模式匹配获取
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code IN ('SUPER_ADMIN', 'SRM_ADMIN', 'PROCUREMENT_MANAGER')
  AND p.permission_code IN ('srm:supplier:withdraw', 'srm:supplier:cancel');

-- 采购员也获得 withdraw/cancel
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.tenant_id = r.tenant_id
WHERE r.tenant_id = 1
  AND r.role_code = 'PROCUREMENT_STAFF'
  AND p.permission_code IN ('srm:supplier:withdraw', 'srm:supplier:cancel');
