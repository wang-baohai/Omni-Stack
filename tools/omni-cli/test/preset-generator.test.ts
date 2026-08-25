import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
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
    const lock = applyPresetGeneration(workspaceRoot, plan);

    assert.equal(lock.preset.id, 'core');
    assert.equal(validateGeneratedPreset(target, 'core').modules.length, 7);
    assert.equal(existsSync(resolve(target, 'omni-backend/omni-crm')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/src/views/crm')), false);
    assert.equal(existsSync(resolve(target, 'scaffold.lock')), true);
    const backendDockerfile = readFileSync(resolve(target, 'docker/backend/Dockerfile'), 'utf8');
    assert.match(backendDockerfile, /^# syntax=docker\/dockerfile:1\.7/);
    assert.match(backendDockerfile, /COPY omni-backend\/mvnw omni-backend\/pom\.xml \.\//);
    assert.match(backendDockerfile, /mvn package -pl '!omni-db-migrator'/);
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
