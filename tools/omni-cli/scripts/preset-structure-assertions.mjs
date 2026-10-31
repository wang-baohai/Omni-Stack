/**
 * 生成物结构断言（WP-07.S01 从 preset-golden.mjs 最小提取）。
 *
 * 原断言含义不变：模块残留、Maven、Compose、Gateway、种子、MQ、i18n、工作流模型。
 * 本模块只增加本次改造所需的固定检查：前端 `@/` 导入可解析、
 * 导航测试仍挂在生成物的 test:unit 入口、workflow 缺失时不留伪菜单与审批静态路由。
 */
import assert from 'node:assert/strict'
import { existsSync, globSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { parseDocument } from 'yaml'
import { loadComposeApplication } from '../dist/src/compose-model.js'

/** 生成物结构断言主入口。 */
export function validateGeneratedStructure(target, catalog, selected) {
  validateNoOmittedResiduals(target, catalog, selected)
  assertFrontendImportsResolve(target)
  assertNavigationTestsWired(target)
}

export function validateNoOmittedResiduals(target, catalog, selected) {
  const selectedDefinitions = catalog.modules.filter((module) => selected.has(module.id))
  const omittedDefinitions = catalog.modules.filter((module) => !selected.has(module.id))
  const retainedDocs = new Set(selectedDefinitions.flatMap((module) => module.docs))
  const retainedChangelogs = new Set(selectedDefinitions.flatMap((module) => module.database.changelogs))

  for (const module of omittedDefinitions) {
    for (const artifact of module.backendModules) {
      assert.equal(existsSync(resolve(target, 'omni-backend', artifact)), false, `残留 Maven 模块：${artifact}`)
    }
    for (const pattern of [
      ...module.frontend.viewGlobs,
      ...module.frontend.componentGlobs,
      ...module.frontend.apiGlobs,
    ]) {
      assert.deepEqual(globSync(pattern, { cwd: target }), [], `残留前端资源：${pattern}`)
    }
    for (const document of module.docs) {
      if (!retainedDocs.has(document)) assert.equal(existsSync(resolve(target, document)), false, `残留文档：${document}`)
    }
    for (const changelog of module.database.changelogs) {
      if (!retainedChangelogs.has(changelog)) {
        assert.equal(existsSync(resolve(target, changelog)), false, `残留数据库变更：${changelog}`)
      }
    }
  }

  const parentPom = readFileSync(resolve(target, 'omni-backend/pom.xml'), 'utf8')
  const actualMavenModules = [...parentPom.matchAll(/<module>([^<]+)<\/module>/g)].map((match) => match[1])
  assert.deepEqual(
    [...actualMavenModules].sort(),
    unique(selectedDefinitions.flatMap((module) => module.backendModules)).sort(),
    'Maven 模块矩阵漂移',
  )

  const compose = loadComposeApplication(target)
  const actualComposeServices = Object.keys(compose.services)
  assert.deepEqual(
    [...actualComposeServices].sort(),
    unique(selectedDefinitions.flatMap((module) => module.composeServices)).sort(),
    'Compose 服务矩阵漂移',
  )
  for (const [serviceName, service] of Object.entries(compose.services)) {
    const dependencies = Array.isArray(service.depends_on) ? service.depends_on : Object.keys(service.depends_on ?? {})
    for (const dependency of dependencies) {
      assert.ok(actualComposeServices.includes(dependency), `${serviceName} 依赖已裁掉的 Compose 服务 ${dependency}`)
    }
  }

  const gateway = parseDocument(readFileSync(
    resolve(target, 'omni-backend/omni-gateway/src/main/resources/application.yml'),
    'utf8',
  )).toJS()
  const gatewayRoutes = gateway.spring.cloud.gateway.server.webflux.routes
  assert.deepEqual(gatewayRoutes.map((route) => route.id), unique(selectedDefinitions.flatMap((module) => module.gatewayRoutes)), 'Gateway 路由矩阵漂移')
  const omittedServiceNames = new Set(omittedDefinitions.flatMap((module) => module.backendModules))
  for (const route of gatewayRoutes) {
    const rendered = JSON.stringify(route)
    for (const serviceName of omittedServiceNames) assert.doesNotMatch(rendered, new RegExp(`/${escapeRegExp(serviceName)}/`))
  }

  const seedManifest = parseDocument(readFileSync(resolve(target, 'database/seed/manifest.yaml'), 'utf8')).toJS()
  const expectedSeedIds = new Set(selectedDefinitions.flatMap((module) => module.database.seedSourceIds))
  assert.deepEqual(new Set((seedManifest.sources ?? []).map((source) => source.id)), expectedSeedIds, '数据库种子矩阵漂移')
  const authSeed = readFileSync(resolve(target, 'scripts/sql/seed/auth.sql'), 'utf8')
  for (const root of omittedDefinitions.flatMap((module) => module.permissionRoots)) {
    assert.doesNotMatch(authSeed, new RegExp(`'${escapeRegExp(root)}(?::|')`), `残留权限根：${root}`)
  }

  const removedProducerDestinations = new Set(omittedDefinitions.flatMap((module) => module.mq.producers)
    .filter((destination) => !selectedDefinitions.some((module) => module.mq.producers.includes(destination))))
  for (const application of globSync('omni-backend/*/src/main/resources/application.yml', { cwd: target })) {
    const content = readFileSync(resolve(target, application), 'utf8')
    for (const destination of removedProducerDestinations) {
      assert.doesNotMatch(content, new RegExp(`destination:\\s*${escapeRegExp(destination)}(?:\\s|$)`), `残留 MQ 绑定：${destination}`)
    }
  }

  const retainedI18nPrefixes = new Set(selectedDefinitions.flatMap((module) => module.frontend.i18nPrefixes))
  const removedI18nPrefixes = new Set(omittedDefinitions.flatMap((module) => module.frontend.i18nPrefixes)
    .filter((prefix) => !retainedI18nPrefixes.has(prefix)))
  for (const locale of globSync('omni-frontend/src/locales/*.ts', { cwd: target })) {
    const content = readFileSync(resolve(target, locale), 'utf8')
    for (const prefix of removedI18nPrefixes) {
      assert.doesNotMatch(content, new RegExp(`^  ${escapeRegExp(prefix)}: \\{$`, 'm'), `残留国际化前缀：${prefix}`)
    }
  }

  const workflowSeedPath = resolve(target, 'scripts/sql/seed/workflow.sql')
  if (existsSync(workflowSeedPath)) {
    const workflowSeed = readFileSync(workflowSeedPath, 'utf8')
    const businessModels = new Map([
      ['supplier-onboarding', 'srm'],
      ['procurement-approval', 'procurement'],
      ['asset-transfer', 'asset'],
      ['asset-disposal', 'asset'],
    ])
    for (const [modelKey, owner] of businessModels) {
      if (!selected.has(owner)) assert.doesNotMatch(workflowSeed, new RegExp(escapeRegExp(modelKey)), `残留工作流模型：${modelKey}`)
    }
  }

  if (!selected.has('workflow')) {
    const router = readFileSync(resolve(target, 'omni-frontend/src/router/index.ts'), 'utf8')
    assert.doesNotMatch(router, /WorkspaceApprovals/, 'workflow 缺失时不得残留审批静态路由')
    const menu = readFileSync(resolve(target, 'omni-frontend/src/constants/menu.ts'), 'utf8')
    assert.doesNotMatch(menu, /^\s+'workflow(?::|':)/m, 'workflow 缺失时不得残留伪模块菜单')
  }
}

/** 生成物内所有 `@/` 导入必须能解析到真实文件（core 无缺模块静态导入）。 */
export function assertFrontendImportsResolve(target) {
  const sourceRoot = resolve(target, 'omni-frontend/src')
  const missing = []
  for (const file of listSourceFiles(sourceRoot)) {
    const content = readFileSync(file, 'utf8')
    for (const match of content.matchAll(/(?:from\s+|import\()['"](@\/[^'"]+)['"]/g)) {
      if (!resolvesAlias(target, match[1])) missing.push(`${relative(target, file).replaceAll('\\', '/')} -> ${match[1]}`)
    }
  }
  assert.deepEqual(missing, [], `生成物存在无法解析的 @/ 导入：${missing.slice(0, 10).join('; ')}`)
}

/** 新导航测试必须仍挂在生成物的 test:unit 入口且文件存在。 */
export function assertNavigationTestsWired(target) {
  const pkg = JSON.parse(readFileSync(resolve(target, 'omni-frontend/package.json'), 'utf8'))
  assert.match(pkg.scripts['test:unit'], /tests\/navigation\/\*\.test\.ts/, '生成物 test:unit 未包含导航测试')
  assert.ok(globSync('omni-frontend/tests/navigation/*.test.ts', { cwd: target }).length >= 5, '生成物缺少导航测试文件')
}

function listSourceFiles(directory, out = []) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) listSourceFiles(path, out)
    else if (/\.(ts|vue)$/.test(entry.name)) out.push(path)
  }
  return out
}

function resolvesAlias(target, specifier) {
  const base = resolve(target, 'omni-frontend/src', specifier.slice(2))
  return [base, `${base}.ts`, `${base}.vue`, `${base}/index.ts`, `${base}/index.vue`]
    .some((candidate) => existsSync(candidate) && statSync(candidate).isFile())
}

function unique(values) {
  return [...new Set(values)]
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
