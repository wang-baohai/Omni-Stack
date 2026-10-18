import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { relative, resolve, sep } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { parse } from 'yaml';
import { applyPresetGeneration, planPresetGeneration, validateGeneratedPreset } from '../src/preset-generator.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const temporaryRoots: string[] = [];

afterEach(() => {
  for (const root of temporaryRoots.splice(0)) {
    const child = relative(resolve(tmpdir()), resolve(root));
    assert.ok(child && child !== '..' && !child.startsWith(`..${sep}`), `拒绝清理系统临时目录之外的路径: ${root}`);
    if (existsSync(root)) rmSync(root, { recursive: true, force: true });
  }
});

describe('preset generator', () => {
  it('creates and validates a clean core workspace atomically', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const target = resolve(temporaryRoot, 'core-project');
    const plan = planPresetGeneration(workspaceRoot, 'core', target);

    assert.deepEqual(plan.omittedModules, ['workflow', 'crm', 'srm', 'procurement', 'asset', 'xxl-job', 'rocketmq']);
    const transientDirectory = resolve(workspaceRoot, 'docs/.vitepress/.temp');
    const transientFile = resolve(transientDirectory, `preset-test-${process.pid}.txt`);
    mkdirSync(transientDirectory, { recursive: true });
    writeFileSync(transientFile, 'VitePress 构建中间文件，不属于规范源', { flag: 'wx' });
    let lock;
    try {
      lock = applyPresetGeneration(workspaceRoot, plan);
    } finally {
      rmSync(transientFile, { force: true });
    }

    assert.equal(lock.preset.id, 'core');
    for (const field of ['sourceDigest', 'catalogDigest', 'presetDigest', 'scaffoldDigest', 'docsDigest', 'aiDigest'] as const) {
      assert.match(lock[field], /^[0-9a-f]{64}$/, `scaffold.lock 缺少数值合法的 ${field}`);
    }
    const digest = (path: string) => createHash('sha256').update(readFileSync(path)).digest('hex');
    assert.equal(lock.catalogDigest, digest(resolve(target, 'scaffold/catalog/modules.yaml')));
    assert.equal(lock.presetDigest, digest(resolve(target, 'scaffold/presets/core.yaml')));
    assert.notEqual(lock.sourceDigest, lock.catalogDigest);
    assert.equal(validateGeneratedPreset(target, 'core').modules.length, 7);
    assert.equal(existsSync(resolve(target, 'omni-backend/omni-crm')), false);
    for (const suffix of ['', '.en', '.jp', '.kr']) {
      const readme = readFileSync(resolve(target, `README${suffix}.md`), 'utf8');
      assert.doesNotMatch(readme, /^- .*\b(?:Workflow|XXL-JOB|CRM|SRM|Procurement|Asset)\b/m, 'core README 不得宣传未交付业务能力');
    }
    assert.equal(existsSync(resolve(target, 'omni-frontend/src/views/crm')), false);
    assert.equal(existsSync(resolve(target, 'scaffold.lock')), true);
    assert.ok(existsSync(resolve(target, 'ai/adapters/bootstrap.mjs')));
    assert.ok(existsSync(resolve(target, 'docs/.vitepress/config.mjs')));
    assert.equal(existsSync(resolve(target, 'docs/.vitepress/source')), false);
    assert.equal(existsSync(resolve(target, 'docs/.vitepress/.temp')), false);
    mkdirSync(resolve(target, 'docs/.vitepress/.temp'), { recursive: true });
    writeFileSync(resolve(target, 'docs/.vitepress/.temp/runtime.js'), '临时构建结果改变不得使文档规范摘要失效');
    assert.equal(validateGeneratedPreset(target, 'core').modules.length, 7);
    assert.equal(existsSync(resolve(target, 'omni-frontend/tsconfig.tsbuildinfo')), false);
    assert.equal(existsSync(resolve(target, 'scripts/fixtures/run-workflow-scope-fixture.ps1')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/workflow-teaching.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/workflow-teaching-ui.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/tests/workflow')), false);
    assert.equal(existsSync(resolve(target, 'docs/reference/evidence/workflow-2026-10-10.json')), false);
    const frontendPackage = JSON.parse(readFileSync(resolve(target, 'omni-frontend/package.json'), 'utf8')) as { scripts: Record<string, string> };
    assert.doesNotMatch(frontendPackage.scripts['test:unit']!, /tests\/workflow/);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/crm-teaching.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/crm-teaching-ui.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'docs/reference/evidence/crm-2026-10-10.json')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/scheduling.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/scheduling-teaching-ui.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/workflow-negative-teaching.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/fixtures/xxl-audit.ts')), false);
    assert.equal(existsSync(resolve(target, 'docs/reference/evidence/scheduling-2026-10-10.json')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/e2e-docs/flows/supply-chain-teaching.flows.spec.ts')), false);
    assert.equal(existsSync(resolve(target, 'docs/reference/evidence/supply-chain-2026-10-09.json')), false);
    assert.equal(existsSync(resolve(target, 'docs/guides/asset-flow.jp.md')), false);
    assert.equal(existsSync(resolve(target, 'docs/images/zh-CN/procurement-overview.png')), false);
    assert.ok(existsSync(resolve(target, 'docs/images/zh-CN/auth-login.png')), '保留认证正文与取证依赖的图片');
    const docsManifest = parse(readFileSync(resolve(target, 'docs/docs-manifest.yaml'), 'utf8')) as { documents: Array<{ module: string }>; release_policy: { ai_review_authorization: { reference: string; reference_sha256: string } } };
    assert.equal(docsManifest.documents.some((entry) => ['crm', 'srm', 'procurement', 'asset', 'workflow'].includes(entry.module)), false);
    const authorization = docsManifest.release_policy.ai_review_authorization;
    assert.equal(digest(resolve(target, authorization.reference)), authorization.reference_sha256, '生成工程必须保留有效授权依据的原始摘要');
    assert.equal(readFileSync(resolve(target, authorization.reference), 'utf8'), readFileSync(resolve(workspaceRoot, authorization.reference), 'utf8'));
    const screenshots = parse(readFileSync(resolve(target, 'omni-frontend/e2e-docs/screenshot-manifest.yaml'), 'utf8')) as { screenshots: Array<{ module: string; route: string }> };
    assert.equal(screenshots.screenshots.some((entry) => entry.route.startsWith('/admin/procurement/')), false);
    assert.equal(screenshots.screenshots.some((entry) => entry.module === 'scheduling-workspace'), false, 'XXL-JOB 未交付时不得残留其截图登记');
    const coverage = parse(readFileSync(resolve(target, 'omni-frontend/e2e-docs/screenshot-coverage.yaml'), 'utf8')) as { modules: Array<{ id: string; required_flows: string[]; status: string }> };
    assert.equal(coverage.modules.some((entry) => entry.id === 'scheduling-workspace'), false, 'XXL-JOB 未交付时不得保留调度覆盖要求');
    const permissionCoverage = coverage.modules.find((entry) => entry.id === 'permissions-exceptions')!;
    assert.equal(permissionCoverage.required_flows.includes('procurement-admin-scope'), false);
    assert.equal(permissionCoverage.required_flows.includes('supplier-scope'), false);
    assert.ok(permissionCoverage.required_flows.includes('forbidden-403'), '保留适用于 core 的拒绝场景要求');
    const sourceCoverage = parse(readFileSync(resolve(workspaceRoot, 'omni-frontend/e2e-docs/screenshot-coverage.yaml'), 'utf8')) as { modules: Array<{ id: string; status: string }> };
    assert.equal(permissionCoverage.status, sourceCoverage.modules.find((entry) => entry.id === 'permissions-exceptions')!.status, '裁剪必须保留源清单的真实验收状态，不能擅自提高状态');
    const backendDockerfile = readFileSync(resolve(target, 'docker/backend/Dockerfile'), 'utf8');
    assert.match(backendDockerfile, /^# syntax=docker\/dockerfile:1\.7/);
    assert.match(backendDockerfile, /COPY omni-backend\/mvnw omni-backend\/pom\.xml \.\//);
    assert.match(backendDockerfile, /sh \.\/mvnw -s docker-settings\.xml package -pl '!omni-db-migrator'/);
    assert.ok(existsSync(resolve(target, 'omni-backend/.mvn/wrapper/maven-wrapper.properties')));
    const migratorDockerfile = readFileSync(resolve(target, 'docker/migrator/Dockerfile'), 'utf8');
    assert.match(migratorDockerfile, /^# syntax=docker\/dockerfile:1\.7/);
    const frontendDockerfile = readFileSync(resolve(target, 'docker/frontend/Dockerfile'), 'utf8');
    assert.match(frontendDockerfile, /^# syntax=docker\/dockerfile:1\.7/);
    const composeConfiguration = readFileSync(resolve(target, 'compose.infra.yaml'), 'utf8');
    assert.match(composeConfiguration, /mysqladmin ping --protocol=tcp -h 127\.0\.0\.1/);
    assert.doesNotMatch(composeConfiguration, /mysqladmin ping -h localhost/);
    const homeWorkspace = readFileSync(resolve(target, 'omni-frontend/src/views/home/index.vue'), 'utf8');
    assert.doesNotMatch(homeWorkspace, /positiveInteger/);
    const coreLocale = readFileSync(resolve(target, 'omni-frontend/src/locales/zh-CN.ts'), 'utf8');
    assert.doesNotMatch(coreLocale, /^  (?:workflow|portalLogin|portalRegister|portal|procurementApprovalRules): \{$/m);
    const authSeed = readFileSync(resolve(target, 'scripts/sql/seed/auth.sql'), 'utf8').replace(/\r\n?/g, '\n');
    assert.doesNotMatch(authSeed, /'(?:workflow|crm|srm|procurement|asset)(?::|')/);
    const seedManifest = parse(readFileSync(resolve(target, 'database/seed/manifest.yaml'), 'utf8')) as {
      sources: Array<{ id: string; sha256: string }>;
    };
    const expectedDigest = createHash('sha256').update(authSeed, 'utf8').digest('hex');
    assert.equal(seedManifest.sources.find((source) => source.id === 'auth-bootstrap')?.sha256, expectedDigest);

    const aiSkillPath = resolve(target, 'ai/skills/omni-change/SKILL.md');
    const originalSkill = readFileSync(aiSkillPath, 'utf8');
    writeFileSync(aiSkillPath, originalSkill + '\nChanged\n');
    assert.throws(() => validateGeneratedPreset(target, 'core'), /aiDigest/);
    writeFileSync(aiSkillPath, originalSkill);
    const siteConfig = resolve(target, 'docs/.vitepress/config.mjs');
    const originalConfig = readFileSync(siteConfig, 'utf8');
    writeFileSync(siteConfig, originalConfig + '\n// 修改隐藏目录里的站点配置\n');
    assert.throws(() => validateGeneratedPreset(target, 'core'), /docsDigest/);
    writeFileSync(siteConfig, originalConfig);
    writeFileSync(resolve(target, 'scaffold/catalog/modules.yaml'), '# 注入漂移\n' + readFileSync(resolve(target, 'scaffold/catalog/modules.yaml'), 'utf8'), 'utf8');
    assert.throws(() => validateGeneratedPreset(target, 'core'), /catalogDigest/);
  });

  it('rejects a custom preset that omits required Compose runtime dependencies', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const presetFile = resolve(temporaryRoot, 'auth-only.yaml');
    writeFileSync(presetFile, [
      'id: auth-only',
      'version: "1.0.0"',
      'displayName: 仅认证',
      'description: 故意缺失 MySQL 与 Redis。',
      'modules: [auth]',
      '',
    ].join('\n'), 'utf8');
    const target = resolve(temporaryRoot, 'auth-only-project');

    assert.throws(() => planPresetGeneration(workspaceRoot, presetFile, target), /Compose 运行时依赖/);
    assert.equal(existsSync(target), false);
  });

  it('rolls the staging tree back when pruning fails halfway', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const parent = resolve(temporaryRoot, 'out');
    mkdirSync(parent);
    const target = resolve(parent, 'broken-project');
    const plan = planPresetGeneration(workspaceRoot, 'core', target);
    const tampered = { ...plan, resolved: { ...plan.resolved, resolvedModules: [] as string[] } };

    let rollbackMessage = '';
    try {
      applyPresetGeneration(workspaceRoot, tampered);
    } catch (error) {
      rollbackMessage = (error as Error).message;
    }
    assert.match(rollbackMessage, /预设工程生成失败，staging 已清理/);
    assert.equal(existsSync(target), false);
    assert.deepEqual(
      readdirSync(parent).filter((entry) => entry.includes('preset-staging')),
      [],
      'staging 目录未回滚清理',
    );
  });

  it('refuses to generate through a symlinked output directory', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const realParent = resolve(temporaryRoot, 'real-parent');
    mkdirSync(realParent);
    const link = resolve(temporaryRoot, 'link');
    try {
      symlinkSync(realParent, link, 'junction');
    } catch {
      return; // 当前环境不允许创建链接，记为 unknown
    }
    assert.throws(
      () => planPresetGeneration(workspaceRoot, 'core', resolve(link, 'core-project')),
      /符号链接|越界/,
    );
    assert.equal(existsSync(resolve(realParent, 'core-project')), false);
  });

  it('rejects a non-empty output directory before copying', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const target = resolve(temporaryRoot, 'occupied');
    mkdirSync(target);
    writeFileSync(resolve(target, 'user-file.txt'), 'owned by user', 'utf8');

    assert.throws(() => planPresetGeneration(workspaceRoot, 'core', target), /必须不存在或为空/);
  });

  it('removes business-only approval helpers from the workflow preset', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const target = resolve(temporaryRoot, 'workflow-project');

    applyPresetGeneration(workspaceRoot, planPresetGeneration(workspaceRoot, 'workflow', target));

    assert.ok(existsSync(resolve(target, 'scripts/fixtures/run-workflow-scope-fixture.ps1')));

    const homeWorkspace = readFileSync(resolve(target, 'omni-frontend/src/views/home/index.vue'), 'utf8');
    assert.doesNotMatch(homeWorkspace, /positiveInteger/);
    assert.doesNotMatch(homeWorkspace, /businessFormLoading/);
    assert.match(homeWorkspace, /completeApproval/);
    const workflowLocale = readFileSync(resolve(target, 'omni-frontend/src/locales/zh-CN.ts'), 'utf8');
    assert.match(workflowLocale, /^  workflow: \{$/m);
    assert.doesNotMatch(workflowLocale, /^  (?:portalLogin|portalRegister|portal|procurementApprovalRules): \{$/m);
    const authConfiguration = readFileSync(
      resolve(target, 'omni-backend/omni-auth/src/main/resources/application.yml'),
      'utf8',
    );
    assert.doesNotMatch(authConfiguration, /srm-domain-event|portalRoleAssignFunction/);
    assert.doesNotMatch(authConfiguration, /rocketmq:/);
    assert.match(authConfiguration, /autodetect: false/);
    const composeConfiguration = readFileSync(resolve(target, 'compose.apps.yaml'), 'utf8');
    assert.match(composeConfiguration, /XXL_JOB_EXECUTOR_ENABLED: "false"/);
    assert.doesNotMatch(composeConfiguration, /XXL_JOB_ACCESS_TOKEN|ROCKETMQ_NAME_SERVER/);
    const workflowSeed = readFileSync(resolve(target, 'scripts/sql/seed/workflow.sql'), 'utf8');
    assert.doesNotMatch(workflowSeed, /supplier-onboarding|procurement-approval|asset-transfer|asset-disposal/);
    const seedManifest = parse(readFileSync(resolve(target, 'database/seed/manifest.yaml'), 'utf8')) as {
      assertions: Array<{ id: string; expectedRows: number; expectedSha256: string }>;
    };
    const workflowModelAssertion = seedManifest.assertions.find((assertion) =>
      assertion.id === 'workflow-model-catalog');
    assert.equal(workflowModelAssertion?.expectedRows, 1);
    assert.equal(workflowModelAssertion?.expectedSha256,
      'e21926aecd8eb01a6300e8e54a9ab21beab7a85a3c10a01bc145ec3ce00e42fb');
    assert.equal(existsSync(resolve(
      target,
      'omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/RequiredWorkflowModelInitializer.java',
    )), false);
    const gatewayConfiguration = readFileSync(
      resolve(target, 'omni-backend/omni-gateway/src/main/resources/application.yml'),
      'utf8',
    );
    assert.doesNotMatch(gatewayConfiguration, /\/omni-(?:crm|srm|procurement|asset)\//);
  });

  it('materializes a dependency-valid custom preset and filters shared workflow models', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-generator-'));
    temporaryRoots.push(temporaryRoot);
    const presetFile = resolve(temporaryRoot, 'supplier.yaml');
    writeFileSync(presetFile, [
      'id: supplier-workspace',
      'version: "1.0.0"',
      'displayName: 供应商工作台',
      'description: 自定义供应商与工作流组合。',
      'modules: [srm, gateway, mysql, redis, nacos]',
      '',
    ].join('\n'), 'utf8');
    const target = resolve(temporaryRoot, 'supplier-project');

    const lock = applyPresetGeneration(workspaceRoot, planPresetGeneration(workspaceRoot, presetFile, target));

    assert.equal(lock.preset.id, 'supplier-workspace');
    assert.equal(existsSync(resolve(target, 'scaffold/presets/supplier-workspace.yaml')), true);
    const initializer = readFileSync(resolve(
      target,
      'omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/RequiredWorkflowModelInitializer.java',
    ), 'utf8');
    assert.match(initializer, /supplier-onboarding/);
    assert.doesNotMatch(initializer, /procurement-approval|asset-transfer|asset-disposal/);
    const workflowSeed = readFileSync(resolve(target, 'scripts/sql/seed/workflow.sql'), 'utf8');
    assert.match(workflowSeed, /supplier-onboarding/);
    assert.doesNotMatch(workflowSeed, /procurement-approval|asset-transfer|asset-disposal/);
    const seedManifest = parse(readFileSync(resolve(target, 'database/seed/manifest.yaml'), 'utf8')) as {
      assertions: Array<{ id: string; expectedRows: number; expectedSha256: string }>;
    };
    const workflowModelAssertion = seedManifest.assertions.find((assertion) =>
      assertion.id === 'workflow-model-catalog');
    assert.equal(workflowModelAssertion?.expectedRows, 2);
    assert.equal(workflowModelAssertion?.expectedSha256,
      '3b20530b150a385b5a9eb48d189ee3a8f03ff53ac3f0b4c30f261db290a22c0b');
  });
});
