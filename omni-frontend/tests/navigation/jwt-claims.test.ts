import assert from 'node:assert/strict'
import { test } from 'node:test'
import { getPermissionsFromToken } from '../../src/utils/jwt.ts'

/** 构造只供解码边界测试的JWT；真实签名校验仍由网关执行。 */
function token(scope: unknown): string {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'RS256' })}.${encode({ sub: '210', tenant_id: 1, scope })}.test-signature`
}

test('普通JWT数组权限与SAS标准字符串权限得到相同精确权限码', () => {
  const permissions = ['workflow:task:todo', 'workflow:approval:complete']
  assert.deepEqual(getPermissionsFromToken(token(permissions)), permissions)
  const sasPermissions = getPermissionsFromToken(token(permissions.join(' ')))
  assert.deepEqual(sasPermissions, permissions)
  assert.equal(sasPermissions.some((value) => value === 'workflow:approval:complete'), true)
  assert.equal(sasPermissions.includes('system:user:create'), false)
})

test('空scope及非法形状失败关闭，不把对象数字或数组内混入值转换为权限', () => {
  for (const scope of ['', '   ', null, undefined, 12, { permission: 'system:user:create' }, ['workflow', 12]]) {
    assert.deepEqual(getPermissionsFromToken(token(scope)), [])
  }
  assert.deepEqual(getPermissionsFromToken('not-a-jwt'), [])
})
