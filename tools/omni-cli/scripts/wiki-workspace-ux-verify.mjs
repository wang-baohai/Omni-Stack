/**
 * WP-07 固定产物位验证入口（仅本次验证需要，不扩展为通用平台）。
 *
 * 用法（Docker 内）：
 *   node scripts/wiki-workspace-ux-verify.mjs --mode=structure --preset=core
 *   node scripts/wiki-workspace-ux-verify.mjs --mode=build --preset=core
 *
 * 固定根：源码 /validation/source，生成物 /validation/current-preset。
 * 本入口不建容器/网络/卷，不执行迁移或种子；build 模式只运行生成物自身的 JDK25 Wrapper、
 * 前端 npm ci/build/lint 与纯 Compose 解析。
 */
import assert from 'node:assert/strict'
import { existsSync, lstatSync, readFileSync, realpathSync, rmSync, statSync } from 'node:fs'
import { isAbsolute, relative, resolve, sep } from 'node:path'
import { spawnSync } from 'node:child_process'
import { loadCatalog } from '../dist/src/catalog.js'
import { loadComposeApplication } from '../dist/src/compose-model.js'
import {
  applyPresetGeneration,
  planPresetGeneration,
  validateGeneratedPreset,
} from '../dist/src/preset-generator.js'
import { validateGeneratedStructure } from './preset-structure-assertions.mjs'

export const FIXED_SOURCE_ROOT = '/validation/source'
export const FIXED_TARGET_ROOT = '/validation/current-preset'
export const OFFICIAL_PRESETS = ['core', 'workflow', 'crm', 'supply-chain', 'full']
const STANDARD_HOST_PORTS = new Set([3000, 3306, 6379, 8080, 8100, 8101, 8102, 8103, 8104, 8105, 8106, 8107, 8848, 8858, 9848, 9876, 10909, 10911, 10912, 4173])

/** 严格解析参数：只接受 --mode=structure|build 与一个真实正式预设。 */
export function parseArguments(args) {
  const values = { mode: undefined, preset: undefined }
  for (const argument of args) {
    if (argument.startsWith('--mode=')) {
      if (values.mode) throw new Error('--mode 只能出现一次')
      values.mode = argument.slice('--mode='.length)
    } else if (argument.startsWith('--preset=')) {
      if (values.preset) throw new Error('--preset 只能出现一次')
      values.preset = argument.slice('--preset='.length)
    } else {
      throw new Error(`未知参数：${argument}`)
    }
  }
  if (values.mode !== 'structure' && values.mode !== 'build') throw new Error('--mode 必须是 structure 或 build')
  if (!values.preset || !OFFICIAL_PRESETS.includes(values.preset)) throw new Error(`--preset 必须是正式预设：${OFFICIAL_PRESETS.join(',')}`)
  return values
}

/** 固定根校验：只允许精确的源码根与产物根，拒绝相对路径、宿主路径与符号链接逃逸。 */
export function assertFixedRoots(source, target) {
  if (!isAbsolute(source) || !isAbsolute(target)) throw new Error('固定根必须是绝对路径')
  if (resolve(source) !== FIXED_SOURCE_ROOT) throw new Error(`源码根必须是 ${FIXED_SOURCE_ROOT}`)
  if (resolve(target) !== FIXED_TARGET_ROOT) throw new Error(`产物根必须是 ${FIXED_TARGET_ROOT}`)
  const sourceState = lstatSync(source)
  if (!sourceState.isDirectory() || sourceState.isSymbolicLink()) throw new Error('源码根必须是真实目录且不是符号链接')
  if (!existsSync(resolve(source, 'scaffold/catalog/modules.yaml'))) throw new Error('源码根缺少 scaffold/catalog/modules.yaml')
  const parent = resolve(target, '..')
  if (realpathSync(parent) !== parent) throw new Error('产物根的父目录不得是符号链接')
  if (existsSync(target)) {
    const targetState = lstatSync(target)
    if (!targetState.isDirectory() || targetState.isSymbolicLink()) throw new Error('产物根必须是真实目录且不是符号链接')
    if (realpathSync(target) !== target) throw new Error('产物根不得通过符号链接指向别处')
  }
}

/** 只清理由本入口持有的固定产物根。 */
export function resetTarget(target) {
  if (resolve(target) !== FIXED_TARGET_ROOT) throw new Error(`拒绝清理非固定产物根：${target}`)
  if (!existsSync(target)) return
  const child = relative(FIXED_TARGET_ROOT, resolve(target))
  if (child !== '') throw new Error(`拒绝清理固定产物根之外的内容：${target}`)
  if (realpathSync(target) !== resolve(target)) throw new Error('产物根真实路径与固定根不一致')
  rmSync(target, { recursive: true, force: true })
}

/** 结构模式：生成、校验锁、运行提取后的全部结构断言。 */
export function runStructure({ source, target, preset }) {
  if (existsSync(target)) resetTarget(target)
  const plan = planPresetGeneration(source, preset, target)
  applyPresetGeneration(source, plan)
  const lock = validateGeneratedPreset(target, preset)
  const catalog = loadCatalog(target)
  const selected = new Set(lock.modules.map((module) => module.id))
  validateGeneratedStructure(target, catalog, selected)
  return { preset, modules: lock.modules.length, services: Object.keys(loadComposeApplication(target).services).length }
}

/** 构建模式：结构通过后，构建生成物自身并核对标准端口。 */
export function runBuild({ source, target, preset }) {
  const structure = runStructure({ source, target, preset })
  const java = run('java', ['-version'], target, {}, true)
  assert.match(`${java.stdout}\n${java.stderr}`, /version "25/, '生成物构建要求 JDK 25')
  run(process.platform === 'win32' ? 'mvnw.cmd' : './mvnw', ['clean', 'install'], resolve(target, 'omni-backend'))
  run('npm', ['ci'], resolve(target, 'omni-frontend'))
  run('npm', ['run', 'build'], resolve(target, 'omni-frontend'))
  run('npm', ['run', 'lint'], resolve(target, 'omni-frontend'))
  const compose = loadComposeApplication(target)
  for (const [serviceName, service] of Object.entries(compose.services)) {
    assertStandardServicePorts(serviceName, service.ports ?? [])
  }
  return { ...structure, built: true }
}

const STANDARD_SERVICE_HOST_PORTS = new Map([
  ['omni-auth', new Set([8100])],
  ['omni-base', new Set([8101])],
  ['omni-gateway', new Set([8102])],
  ['omni-workflow', new Set([8103])],
  ['omni-crm', new Set([8104])],
  ['omni-srm', new Set([8105])],
  ['omni-procurement', new Set([8106])],
  ['omni-asset', new Set([8107])],
  ['omni-frontend', new Set([3000])],
  ['nacos', new Set([8080, 8848, 9848])],
  ['mysql', new Set([3306])],
  ['redis', new Set([6379])],
  ['xxl-job-admin', new Set([18080])],
  ['rocketmq-namesrv', new Set([19876])],
  ['rocketmq-broker', new Set([10909, 10911, 10912])],
])

/** 解析端口变量默认值；缺少默认值的变量无法可靠求值，明确失败。 */
function resolvePortValue(value) {
  const text = String(value).trim()
  const variable = text.match(/^\$\{([A-Z0-9_]+)(?::-([^}]+))?\}$/)
  if (!variable) return text
  if (variable[2] === undefined) throw new Error(`无法解析端口变量 ${text}：缺少默认值`)
  return variable[2]
}

/** 按冒号拆分端口映射，但保留 `${VAR:-default}` 内部的冒号。 */
function splitPortMapping(text) {
  const parts = []
  let current = ''
  let depth = 0
  for (const character of text) {
    if (character === '{') depth += 1
    else if (character === '}') depth -= 1
    if (character === ':' && depth === 0) {
      parts.push(current)
      current = ''
    } else {
      current += character
    }
  }
  parts.push(current)
  return parts
}

/** 取实际发布到宿主机的端口；仅容器端口（无 published）返回 null。 */
export function publishedHostPort(entry) {
  if (entry !== null && typeof entry === 'object') {
    if (entry.published === undefined || entry.published === null || entry.published === '') return null
    const resolved = resolvePortValue(entry.published)
    assert.match(resolved, /^\d+$/, `无法解析 published 端口 ${resolved}`)
    return Number(resolved)
  }
  const text = String(entry).trim().replace(/\/[a-z]+$/i, '')
  if (text === '') throw new Error('空端口映射')
  const parts = splitPortMapping(text)
  if (parts.length === 1) return null
  if (parts.length === 2) {
    const host = resolvePortValue(parts[0])
    assert.match(host, /^\d+$/, `无法解析宿主端口 ${host}`)
    return Number(host)
  }
  if (parts.length === 3) {
    const host = resolvePortValue(parts[1])
    assert.match(host, /^\d+$/, `无法解析宿主端口 ${host}`)
    return Number(host)
  }
  throw new Error(`无法可靠解析端口映射 ${text}（仅支持 short/long 的单宿主地址）`)
}

/** 校验服务发布的是该服务的标准宿主端口；未知服务回退到全局标准端口集。 */
export function assertStandardServicePorts(serviceName, ports) {
  for (const entry of ports) {
    const hostPort = publishedHostPort(entry)
    if (hostPort === null) continue
    const expected = STANDARD_SERVICE_HOST_PORTS.get(serviceName)
    if (expected) {
      assert.ok(expected.has(hostPort), `${serviceName} 使用非标准宿主端口 ${hostPort}（期望 ${[...expected].join('/')}）`)
    } else {
      assert.ok(STANDARD_HOST_PORTS.has(hostPort), `${serviceName} 使用非标准宿主端口 ${hostPort}`)
    }
  }
}

function run(command, args, cwd, extraEnvironment = {}, capture = false) {
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio: capture ? 'pipe' : 'inherit',
    maxBuffer: 64 * 1024 * 1024,
  })
  if (result.error) throw result.error
  assert.equal(result.status, 0, `命令失败：${command} ${args.join(' ')}`)
  return { stdout: result.stdout ?? '', stderr: result.stderr ?? '' }
}

function main() {
  assertFixedRoots(FIXED_SOURCE_ROOT, FIXED_TARGET_ROOT)
  const argumentsMap = parseArguments(process.argv.slice(2))
  const runner = argumentsMap.mode === 'build' ? runBuild : runStructure
  const result = runner({ source: FIXED_SOURCE_ROOT, target: FIXED_TARGET_ROOT, preset: argumentsMap.preset })
  assert.ok(existsSync(resolve(FIXED_TARGET_ROOT, 'scaffold.lock')), '生成物缺少 scaffold.lock')
  assert.ok(statSync(resolve(FIXED_TARGET_ROOT, 'scaffold.lock')).isFile(), 'scaffold.lock 必须是文件')
  console.log(`WIKI_WORKSPACE_UX_VERIFY_PASS mode=${argumentsMap.mode} preset=${argumentsMap.preset} modules=${result.modules} services=${result.services}`)
}

if (import.meta.url === `file://${process.argv[1]?.replaceAll('\\', '/')}`) {
  try {
    main()
  } catch (error) {
    console.error(`WIKI_WORKSPACE_UX_VERIFY_FAIL ${error instanceof Error ? error.message : String(error)}`)
    process.exitCode = 1
  }
}
