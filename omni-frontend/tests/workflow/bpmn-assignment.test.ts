/** 真实 moddle XML 往返回归：监听器大小写、候选分派与重复编辑后的扩展保留。 */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { BpmnModdle } from 'bpmn-moddle'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import { MODDLE_EXTENSIONS, readAssignment, writeAssignment } from '../../src/composables/useBpmnExtension.ts'
import type { BpmnElement, BpmnModdleElement } from '../../src/types/bpmn.ts'

test('approval XML round trip retains executable assignment and unrelated listeners', async () => {
  const moddle = new BpmnModdle(MODDLE_EXTENSIONS)
  const task = moddle.create('bpmn:UserTask', { id: 'approval' })
  const ext = moddle.create('bpmn:ExtensionElements', { values: [
    moddle.create('flowable:TaskListener', { event: 'create', delegateExpression: '${scopedRoleAssignmentListener}' }),
    moddle.create('flowable:ExecutionListener', { event: 'end', delegateExpression: '${customAuditListener}' }),
  ] })
  task.set('extensionElements', ext)
  const element: BpmnElement = { id: 'approval', businessObject: task }
  const modeler = {
    get(name: string) {
      if (name === 'moddle') return moddle
      if (name === 'modeling') return { updateProperties(_element: BpmnElement, properties: Record<string, unknown>) {
        for (const [key, value] of Object.entries(properties)) task.set(key, value)
      } }
      throw new Error(`Unexpected modeler service ${name}`)
    },
  } as unknown as BpmnModeler
  const config = { assignmentType: 'SCOPED_ROLE', roleCode: 'REVIEWER', anchorType: 'PARENT', anchorParams: {}, scopeMode: 'SAME_UNIT', fallbackStrategy: 'ERROR', approvalMode: 'ALL' }
  writeAssignment(modeler, element, config)
  writeAssignment(modeler, element, { ...config, approvalMode: 'ANY' })
  const process = moddle.create('bpmn:Process', { id: 'ApprovalRoundTrip', isExecutable: true, flowElements: [task] })
  const definitions = moddle.create('bpmn:Definitions', { targetNamespace: 'http://omni.com/workflow', rootElements: [process] })
  const { xml } = await moddle.toXML(definitions)
  assert.match(xml, /<flowable:executionListener event="start" delegateExpression="\$\{scopedRoleAssignmentListener\}"/)
  assert.doesNotMatch(xml, /flowable:(?:ExecutionListener|TaskListener|taskListener)/)
  assert.equal((xml.match(/delegateExpression="\$\{scopedRoleAssignmentListener\}"/g) || []).length, 1)
  assert.match(xml, /delegateExpression="\$\{customAuditListener\}"/)
  assert.match(xml, /flowable:assignee="\$\{userId\}"/)
  assert.match(xml, /flowable:collection="candidateUserIds"/)
  assert.match(xml, /flowable:elementVariable="userId"/)
  assert.match(xml, /requiredApprovals/)
  const parsed = await moddle.fromXML(xml)
  assert.deepEqual(parsed.warnings, [])
  const restored = parsed.rootElement.rootElements[0].flowElements[0]
  assert.equal(readAssignment({ id: 'approval', businessObject: restored })?.approvalMode, 'ANY')
  const listeners = (restored.extensionElements.values as BpmnModdleElement[]).filter(x => x.$type === 'flowable:ExecutionListener')
  assert.equal(listeners.length, 2, JSON.stringify(restored.extensionElements.values.map((x: BpmnModdleElement) => ({ type: x.$type, expression: x.delegateExpression, attrs: x.$attrs }))))
  assert.equal(restored.loopCharacteristics.isSequential, false)
  assert.equal(restored.loopCharacteristics.get('flowable:collection'), 'candidateUserIds')
})
