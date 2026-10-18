import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { mkdtempSync, renameSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { relative, resolve, sep } from 'node:path'
import { applyPresetGeneration, planPresetGeneration, validateGeneratedPreset } from '../dist/src/preset-generator.js'

const root = resolve(import.meta.dirname, '../../..')
const image = process.env.OMNI_DELIVERY_TOOLS_IMAGE || 'omni-stack-tools:local'
const runId = randomUUID().replaceAll('-', '').slice(0, 12)
const label = `omni.preset-delivery=${runId}`
const npmCache = `omni-preset-delivery-${runId}-npm-cache`
const temporary = mkdtempSync(resolve(tmpdir(), 'omni-preset-delivery-'))
const presets = ['core', 'workflow', 'crm', 'supply-chain', 'full']
const results = []
let retainedWork = false
function docker(args, capture = false) {
  const result = spawnSync('docker', args, { encoding: 'utf8', ...(capture ? {} : { stdio: 'inherit' }) })
  if (result.error) throw result.error
  assert.equal(result.status, 0, `独立容器检查失败：docker ${args[0]}`)
  return result.stdout?.trim()
}
docker(['image', 'inspect', image], true)
docker(['volume', 'create', '--label', label, npmCache], true)
try {
  for (const preset of presets) {
    try {
      const initial = resolve(temporary, `generated-${preset}`), moved = resolve(temporary, `moved-${preset}`)
      applyPresetGeneration(root, planPresetGeneration(root, preset, initial))
      renameSync(initial, moved)
      validateGeneratedPreset(moved, preset)
      const name = `omni-preset-delivery-${runId}-${preset}`, volume = `${name}-files`
      docker(['volume', 'create', '--label', label, volume], true)
      let created = false
      try {
        docker(['create', '--name', name, '--label', label, '--mount', `type=volume,source=${volume},target=/workspace`,
          '--mount', `type=volume,source=${npmCache},target=/root/.npm`,
          '--workdir', '/workspace', image, 'sh', '-c',
          `npm --prefix tools/omni-cli ci --prefer-offline && node tools/omni-cli/scripts/verify-preset-delivery.mjs ${preset}`], true)
        created = true
        docker(['cp', `${moved}/.`, `${name}:/workspace`])
        const mounts = JSON.parse(docker(['inspect', '--format', '{{json .Mounts}}', name], true))
        assert.equal(mounts.length, 2, '验证容器只挂载交付卷和本次拥有的依赖缓存')
        for (const mount of mounts) {
          assert.equal(mount.Type, 'volume', '源仓库 bind 挂载不得进入独立验证')
          assert.ok([volume, npmCache].includes(mount.Name))
        }
        console.log(`Independent preset delivery: ${preset}; source checkout disconnected; container=${name}`)
        docker(['start', '--attach', name])
        assert.equal(docker(['inspect', '--format', '{{.State.ExitCode}}', name], true), '0')
      } finally {
        if (created) {
          assert.equal(docker(['inspect', '--format', '{{index .Config.Labels "omni.preset-delivery"}}', name], true), runId)
          // 观察中断不等于工作停止；仍运行时保留具体句柄，禁止强制清理或启动下一项。
          if (docker(['inspect', '--format', '{{.State.Running}}', name], true) === 'true') {
            retainedWork = true
            throw new Error(`验证进程仍运行，保留容器 ${name}、卷 ${volume} 和缓存 ${npmCache}`)
          }
          docker(['rm', name], true)
        }
        assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "omni.preset-delivery"}}', volume], true), runId)
        docker(['volume', 'rm', volume], true)
      }
      results.push({ preset, status: 'PASS' })
    } catch (error) {
      results.push({ preset, status: 'FAIL', error: error.message })
      console.error(`Independent preset delivery failed: ${preset}; ${error.message}`)
      if (retainedWork) break
    }
  }
} finally {
  assert.equal(docker(['volume', 'inspect', '--format', '{{index .Labels "omni.preset-delivery"}}', npmCache], true), runId)
  if (!retainedWork) docker(['volume', 'rm', npmCache], true)
  const child = relative(resolve(tmpdir()), temporary)
  assert.ok(child && child !== '..' && !child.startsWith(`..${sep}`) && child.startsWith('omni-preset-delivery-'))
  rmSync(temporary, { recursive: true, force: true })
}
console.log(JSON.stringify({ kind: 'independent-preset-delivery-matrix', retained_work: retainedWork, results }))
assert.ok(results.length === presets.length && results.every(result => result.status === 'PASS'), '五预设独立交付矩阵仍有失败；严格门未放宽')
console.log('Independent documentation and AI delivery: all five presets passed automated checks. Translation release readiness follows each generated project’s actual review evidence and authorized policy.')
