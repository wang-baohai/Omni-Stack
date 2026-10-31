import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  isDeploymentModuleEnabled,
  isDeploymentPermissionEnabled,
  isDeploymentRouteEnabled,
} from '../../src/utils/deployment-modules.ts'

const optional = ['workflow', 'crm', 'srm', 'procurement', 'asset']

test('未指定部署选择的现有全量构建保留可选模块', () => {
  for (const id of optional) {
    assert.equal(isDeploymentModuleEnabled(id), true)
    assert.equal(isDeploymentPermissionEnabled(`${id}:read`), true)
  }
})

test('core 即使面对全量权限也不暴露五类可选模块', () => {
  const selection = 'platform,auth,base,gateway,mysql,redis,frontend'
  for (const id of optional) {
    assert.equal(isDeploymentModuleEnabled(id, selection), false)
    assert.equal(isDeploymentPermissionEnabled(id, selection), false)
    assert.equal(isDeploymentPermissionEnabled(`${id}:read`, selection), false)
  }
  for (const code of ['system:user', 'base:dict', 'job:system-job']) {
    assert.equal(isDeploymentPermissionEnabled(code, selection), true)
  }
})

test('五预设的闭包只保留实际已选业务模块', () => {
  for (const selected of [[], ['workflow'], ['crm'], ['workflow', 'srm', 'procurement', 'asset'], optional]) {
    const selection = ['platform', 'auth', 'base', ...selected].join(',')
    for (const id of optional) {
      assert.equal(isDeploymentPermissionEnabled(`${id}:read`, selection), selected.includes(id))
    }
  }
})

test('精确模块 ID 匹配不接受同前缀或权限码作为模块选择', () => {
  assert.equal(isDeploymentModuleEnabled('workflow', 'workflow-other,workflow:read'), false)
  assert.equal(isDeploymentModuleEnabled('workflow', 'base, workflow ,auth'), true)
  assert.equal(isDeploymentPermissionEnabled('crm:lead', 'platform'), false)
})

test('供应商三个独立入口随 SRM 部署关闭，其他公开与保护路由不受影响', () => {
  for (const path of ['/supplier-portal', '/portal-login', '/portal-register']) {
    assert.equal(isDeploymentRouteEnabled(path, 'platform,auth,base'), false)
    assert.equal(isDeploymentRouteEnabled(path, 'platform,auth,base,srm'), true)
  }
  for (const path of ['/', '/login', '/callback', '/workspace', '/:pathMatch(.*)*']) {
    assert.equal(isDeploymentRouteEnabled(path, 'platform,auth,base'), true)
  }
})
