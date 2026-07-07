-- 修复 BPMN XML 校验错误：process id 对齐 + 排他网关 default 属性
USE omni_workflow;

UPDATE wf_process_model_version
SET bpmn_xml = REPLACE(
    REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    bpmn_xml,
                    'process id="leave-approval"',
                    'process id="leave"'
                ),
                'bpmnElement="leave-approval"',
                'bpmnElement="leave"'
            ),
            '<exclusiveGateway id="gw-level1"',
            '<exclusiveGateway id="gw-level1" default="flow-l1-reject"'
        ),
        '<exclusiveGateway id="gw-level2"',
        '<exclusiveGateway id="gw-level2" default="flow-l2-reject"'
    ),
    '<exclusiveGateway id="gw-level3"',
    '<exclusiveGateway id="gw-level3" default="flow-l3-reject"'
)
WHERE id IN (3, 4);
