import assert from 'node:assert/strict'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 只执行已复现默认种子缺陷对应的单个新增 changeSet；不调用整套 migrator。
const context = createLocalContext()
const mode = process.argv[2]
assert.ok(['apply', 'verify'].includes(mode))
const xml = readFileSync('/validation/source/omni-backend/omni-db-migrator/target/surefire-reports/TEST-com.omni.dbmigrator.seed.SeedManifestLoaderTest.xml', 'utf8')
const match = xml.match(/<property name="surefire\.test\.class\.path" value="([^"]*)"/)
assert.ok(match, '先完成实际 Maven 测试并取得当前 test classpath')
const classpath = match[1].replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&')
  .split(':').filter(Boolean)
for (const path of classpath) assert.ok((path.startsWith('/validation/source/omni-backend/') || path.startsWith('/validation/cache/m2/')) && existsSync(path))
const result = spawnSync('java', ['--enable-native-access=ALL-UNNAMED', '-Djava.io.tmpdir=/validation/test-fixtures/tmp',
  '-cp', classpath.join(':'), 'com.omni.dbmigrator.seed.SrmApproverSeedFixture', mode], { env: process.env, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 })
writeFileSync(`${privateRoot}/srm-approver-seed-${mode}-raw.json`, JSON.stringify({ exit_code: result.status, stdout: result.stdout, stderr: result.stderr }), { mode: 0o600 })
let safeOutput = `${result.stdout || ''}\n${result.stderr || ''}\nexit_code=${result.status}\n`
for (const [key, value] of Object.entries(context.config)) {
  if (/password|secret|key|seed/i.test(key) && typeof value === 'string' && value.length > 3) safeOutput = safeOutput.split(value).join('<redacted>')
}
safeOutput = safeOutput.replace(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '<redacted-jwt>')
writeFileSync(`/validation/evidence/codex-2026-10-22/wp07-s03/srm-approver-seed-${mode}.log`, safeOutput)
assert.equal(result.status, 0, '受控增量失败；受限诊断已保存，不隐藏失败或修改业务状态')
console.log(JSON.stringify({ result: 'SRM_APPROVER_SEED_VERIFIED', mode, new_changelog_only: true, shared_user_unchanged: true }))
