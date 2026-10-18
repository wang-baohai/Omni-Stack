import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { parse } from 'yaml'
import { reviewErrors, translationLocales } from './translation-review.mjs'

const root = resolve(import.meta.dirname, '../../..')
const preset = process.argv[2]
assert.ok(['core', 'workflow', 'crm', 'supply-chain', 'full'].includes(preset))
const checks = []
function run(command, args, cwd = root) {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', stdio: 'inherit' })
  const passed = !result.error && result.status === 0
  checks.push({ command, args, status: passed ? 'PASS' : 'FAIL', exit_code: result.status,
    ...(result.error ? { error: result.error.message } : {}) })
  return passed
}
assert.ok(run('npm', ['run', 'build'], resolve(root, 'tools/omni-cli')), 'CLI 构建失败，后续检查缺少依赖')
const { validateGeneratedPreset } = await import('../dist/src/preset-generator.js')
const lock = validateGeneratedPreset(root, preset)
for (const scope of ['links', 'i18n-content', 'readme', 'sensitive', 'screenshots']) {
  run(process.execPath, ['tools/omni-cli/scripts/docs-quality.mjs', `--scope=${scope}`])
}
run(process.execPath, ['tools/omni-cli/scripts/preset-docs.mjs', '--check'])
run(process.execPath, ['tools/omni-cli/scripts/docs-review-queue.mjs', '--check'])
if (run(process.execPath, ['--test', 'ai/adapters/bootstrap.test.mjs'])) {
  for (const tool of ['codex', 'qoder']) {
    if (run(process.execPath, ['ai/adapters/bootstrap.mjs', `--tool=${tool}`, '--apply'])) {
      run(process.execPath, ['ai/adapters/bootstrap.mjs', `--tool=${tool}`, '--check'])
    }
  }
}
const manifest = parse(readFileSync(resolve(root, 'docs/docs-manifest.yaml'), 'utf8'))
const expected = manifest.documents.flatMap((document) => (document.language_scope || 'full') === 'full'
  ? translationLocales.flatMap((locale) => reviewErrors(document, locale, document.translations[locale],
    readFileSync(resolve(root, document.source), 'utf8'), readFileSync(resolve(root, document.translations[locale].path), 'utf8'), {
      policy: manifest.release_policy, readEvidence: (path) => { try { return readFileSync(resolve(root, path), 'utf8') } catch { return null } },
    }))
  : [])
const strict = spawnSync(process.execPath, ['tools/omni-cli/scripts/docs-quality.mjs', '--scope=i18n'], { cwd: root, encoding: 'utf8' })
const actual = strict.stderr.split(/\r?\n/).filter((line) => line.startsWith('- ')).map((line) => line.slice(2))
try {
  assert.equal(strict.status, expected.length ? 1 : 0, '翻译复核发布门必须保留真实结果')
  assert.deepEqual(actual.sort(), expected.sort(), '严格门出现非翻译复核问题，不得当作预期阻塞忽略')
  checks.push({ command: 'i18n-review-contract', status: 'PASS', exit_code: strict.status, translation_review_errors: expected.length })
} catch (error) {
  checks.push({ command: 'i18n-review-contract', status: 'FAIL', exit_code: strict.status, error: error.message })
}
if (run('npm', ['ci', '--prefer-offline'], resolve(root, 'docs'))) {
  run('npm', ['test'], resolve(root, 'docs'))
  run('npm', ['run', 'build'], resolve(root, 'docs'))
}
try {
  assert.equal(validateGeneratedPreset(root, preset).docsDigest, lock.docsDigest, '构建产物不应改变规范文档摘要')
  checks.push({ command: 'post-build-delivery-digest', status: 'PASS' })
} catch (error) {
  checks.push({ command: 'post-build-delivery-digest', status: 'FAIL', error: error.message })
}
const passed = checks.every(check => check.status === 'PASS')
console.log(JSON.stringify({ preset, status: passed ? 'automated-delivery-pass' : 'automated-delivery-fail', modules: lock.modules.length,
  source_checkout_mounted: false, translation_review_errors: expected.length, release_ready: passed && expected.length === 0, checks }))
// 独立检查继续收集结果，任何严格门失败仍使整体验证失败。
if (!passed) process.exitCode = 1
