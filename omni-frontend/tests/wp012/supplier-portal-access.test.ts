/** 门户入驻不能因默认 USER 同时拥有后台只读权限而被路由拒绝。 */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { hasManagementAccess, hasSupplierPortalAccess } from '../../src/utils/access.ts'

test('默认 USER 的后台只读权限与门户入驻权限可以并存', () => {
  const permissions = ['system:user:list', 'base:operlog:list', 'workflow:task:todo', 'srm:portal:enroll']
  assert.equal(hasManagementAccess(permissions, ['USER']), true)
  assert.equal(hasSupplierPortalAccess(permissions, ['USER']), true)
})

test('只有后台权限且没有入驻权限的 USER 不能进入门户', () => {
  assert.equal(hasSupplierPortalAccess(['system:user:list'], ['USER']), false)
})

test('已入驻的 USER 与 SUPPLIER 账号继续访问独立门户', () => {
  assert.equal(hasSupplierPortalAccess(['srm:portal:profile'], ['USER', 'SUPPLIER']), true)
  assert.equal(hasManagementAccess(['system:user:list'], ['USER', 'SUPPLIER']), false)
})

test('内部账号的显式入驻权限不因已有管理权限失效', () => {
  assert.equal(hasSupplierPortalAccess(['srm:portal:enroll', 'srm:supplier:list'], ['USER', 'SRM_ADMIN']), true)
})
