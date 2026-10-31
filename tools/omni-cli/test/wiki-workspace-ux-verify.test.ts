import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { relative, resolve, sep } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { findWorkspaceRoot } from '../src/workspace.js';

const verifyModule = await import('../../scripts/wiki-workspace-ux-verify.mjs' as string) as {
  parseArguments(args: string[]): { mode: string; preset: string };
  assertFixedRoots(source: string, target: string): void;
  resetTarget(target: string): void;
  runStructure(options: { source: string; target: string; preset: string }): { modules: number; services: number };
  publishedHostPort(entry: unknown): number | null;
  assertStandardServicePorts(serviceName: string, ports: unknown[]): void;
  OFFICIAL_PRESETS: string[];
};
const assertionsModule = await import('../../scripts/preset-structure-assertions.mjs' as string) as {
  PLAN_INPUT_EXCLUDES: string[];
  validateGeneratedStructure(target: string, catalog: unknown, selected: Set<string>): void;
};
const { loadCatalog } = await import('../src/catalog.js');
const { validateGeneratedPreset } = await import('../src/preset-generator.js');

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const temporaryRoots: string[] = [];

afterEach(() => {
  for (const root of temporaryRoots.splice(0)) {
    const child = relative(resolve(tmpdir()), resolve(root));
    assert.ok(child && child !== '..' && !child.startsWith(`..${sep}`), `拒绝清理系统临时目录之外的路径: ${root}`);
    if (existsSync(root)) rmSync(root, { recursive: true, force: true });
  }
});

describe('wiki-workspace-ux verify entry', () => {
  it('accepts only structure|build with one official preset', () => {
    assert.deepEqual(verifyModule.parseArguments(['--mode=structure', '--preset=core']), { mode: 'structure', preset: 'core' });
    assert.deepEqual(verifyModule.parseArguments(['--mode=build', '--preset=full']), { mode: 'build', preset: 'full' });
    assert.throws(() => verifyModule.parseArguments(['--preset=core']), /--mode/);
    assert.throws(() => verifyModule.parseArguments(['--mode=fast', '--preset=core']), /--mode/);
    assert.throws(() => verifyModule.parseArguments(['--mode=structure']), /--preset/);
    assert.throws(() => verifyModule.parseArguments(['--mode=structure', '--preset=demo']), /--preset/);
    assert.throws(() => verifyModule.parseArguments(['--mode=structure', '--preset=core', '--keep']), /未知参数/);
    assert.throws(() => verifyModule.parseArguments(['--mode=structure', '--mode=build', '--preset=core']), /只能出现一次/);
  });

  it('rejects host paths and non-fixed roots', () => {
    assert.throws(() => verifyModule.assertFixedRoots(process.cwd(), '/validation/current-preset'), /源码根/);
    assert.throws(() => verifyModule.assertFixedRoots('/validation/source', process.cwd()), /产物根/);
    assert.throws(() => verifyModule.assertFixedRoots('relative/source', '/validation/current-preset'), /绝对路径/);
    assert.throws(() => verifyModule.resetTarget(process.cwd()), /非固定产物根/);
  });

  it('generates core with no plan inputs, no workflow residuals and resolvable imports', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-wux-verify-'));
    temporaryRoots.push(temporaryRoot);
    const target = resolve(temporaryRoot, 'core');
    const result = verifyModule.runStructure({ source: workspaceRoot, target, preset: 'core' });
    assert.equal(result.modules, 7);

    for (const rel of assertionsModule.PLAN_INPUT_EXCLUDES) {
      assert.equal(existsSync(resolve(target, rel)), false, `生成物不得携带计划输入：${rel}`);
    }
    const router = readFileSync(resolve(target, 'omni-frontend/src/router/index.ts'), 'utf8');
    assert.match(router, /name: 'WorkspaceOverview'/, '工作台概览必须成为默认子路由');
    assert.doesNotMatch(router, /WorkspaceApprovals/, 'workflow 缺失时不得残留审批静态路由');
    assert.equal(existsSync(resolve(target, 'omni-frontend/src/views/workspace/approvals')), false);
    assert.equal(existsSync(resolve(target, 'omni-frontend/src/views/workspace/index.vue')), true);
    assert.equal(existsSync(resolve(target, 'omni-frontend/src/api/workflow.ts')), true, '共享统计客户端必须随 base 保留');
    const pkg = JSON.parse(readFileSync(resolve(target, 'omni-frontend/package.json'), 'utf8')) as { scripts: Record<string, string> };
    assert.match(pkg.scripts['test:unit']!, /tests\/navigation\/\*\.test\.ts/);
    const menu = readFileSync(resolve(target, 'omni-frontend/src/constants/menu.ts'), 'utf8');
    assert.doesNotMatch(menu, /^\s+'workflow(?::|':)/m);

    const lock = validateGeneratedPreset(target, 'core');
    const catalog = loadCatalog(target);
    writeFileSync(resolve(target, 'omni-frontend/src/broken-import.ts'), "import { missing } from '@/api/does-not-exist'\n");
    assert.throws(
      () => assertionsModule.validateGeneratedStructure(target, catalog, new Set(lock.modules.map((module) => module.id))),
      /无法解析的 @\/ 导入/,
    );
  });

  it('reads published host ports instead of the container port in short and long forms', () => {
    assert.equal(verifyModule.publishedHostPort('127.0.0.1:8102:8080'), 8102);
    assert.equal(verifyModule.publishedHostPort('8102:8080/tcp'), 8102);
    assert.equal(verifyModule.publishedHostPort('127.0.0.1:${OMNI_GATEWAY_HOST_PORT:-8102}:8080'), 8102);
    assert.equal(verifyModule.publishedHostPort({ target: 8080, published: '8102', host_ip: '127.0.0.1', protocol: 'tcp' }), 8102);
    assert.equal(verifyModule.publishedHostPort('8080'), null, '仅容器端口时没有宿主发布端口');
    assert.equal(verifyModule.publishedHostPort({ target: 8080 }), null);
    assert.throws(() => verifyModule.publishedHostPort('127.0.0.1:${OMNI_GATEWAY_HOST_PORT}:8080'), /缺少默认值/);
    assert.throws(() => verifyModule.publishedHostPort('::1:8102:8080'), /无法可靠解析/);
  });

  it('rejects host-port offsets while accepting each service standard mapping', () => {
    verifyModule.assertStandardServicePorts('omni-gateway', ['127.0.0.1:8102:8080']);
    verifyModule.assertStandardServicePorts('omni-gateway', ['${OMNI_GATEWAY_HOST_PORT:-8102}:8080']);
    assert.throws(() => verifyModule.assertStandardServicePorts('omni-gateway', ['127.0.0.1:18102:8080']), /非标准宿主端口 18102/);
    assert.throws(() => verifyModule.assertStandardServicePorts('omni-auth', ['127.0.0.1:8101:8080']), /非标准宿主端口 8101/);
    verifyModule.assertStandardServicePorts('xxl-job-admin', ['127.0.0.1:${OMNI_XXL_JOB_HOST_PORT:-18080}:8080']);
    assert.throws(() => verifyModule.assertStandardServicePorts('xxl-job-admin', ['127.0.0.1:8080:8080']), /非标准宿主端口/);
    verifyModule.assertStandardServicePorts('rocketmq-broker', ['127.0.0.1:10909:10909', '127.0.0.1:10911:10911']);
    assert.throws(() => verifyModule.assertStandardServicePorts('unknown-service', ['127.0.0.1:18102:8080']), /非标准宿主端口/);
  });
});
