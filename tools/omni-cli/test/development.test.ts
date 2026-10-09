import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import YAML from 'yaml';
import {
  composeDownArguments,
  declaredOverrideNames,
  developmentCommandEnvironment,
  developmentEnvironment,
  normalizeProjectName,
  parseOverrideAssignments,
  planDevelopmentSelection,
  projectArgs,
  showModuleLogs,
  validateVolumeDeletion,
} from '../src/development.js';
import { implicitProjectName } from '../src/doctor.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const liteSelection = { id: 'x', kind: 'preset' as const, profile: 'core', moduleIds: [], services: ['omni-auth'], lite: true };
const fullSelection = { ...liteSelection, id: 'y', lite: false };

describe('development selection', () => {
  it('plans the core preset without optional job or message infrastructure', () => {
    const selection = planDevelopmentSelection(workspaceRoot, { preset: 'core' });

    assert.equal(selection.lite, true);
    assert.deepEqual(new Set(selection.services), new Set([
      'mysql', 'omni-db-migrator', 'redis',
      'omni-auth', 'omni-base', 'omni-gateway', 'omni-frontend',
    ]));
    assert.equal(selection.services.includes('rocketmq-broker'), false);
    assert.equal(selection.services.includes('xxl-job-admin'), false);
    assert.equal(selection.services.includes('nacos'), false);
  });

  it('plans CRM without any supply-chain service', () => {
    const selection = planDevelopmentSelection(workspaceRoot, { module: 'crm' });

    assert.equal(selection.lite, true);
    assert.ok(selection.services.includes('omni-crm'));
    for (const omitted of ['omni-srm', 'omni-procurement', 'omni-asset']) {
      assert.equal(selection.services.includes(omitted), false);
    }
  });

  it('plans the public frontend without starting backend or infrastructure services', () => {
    const selection = planDevelopmentSelection(workspaceRoot, { module: 'frontend' });

    assert.equal(selection.lite, true);
    assert.deepEqual(selection.services, ['omni-frontend']);
    const environment = developmentCommandEnvironment(workspaceRoot, selection);
    assert.equal(environment.MYSQL_ROOT_PASSWORD, 'unused-in-frontend-only-mode');
    assert.equal(environment.OMNI_INTERNAL_API_TOKEN, 'unused-in-frontend-only-mode');
  });

  it('plans Asset with its complete supply-chain and runtime dependencies', () => {
    const selection = planDevelopmentSelection(workspaceRoot, { module: 'asset' });

    assert.equal(selection.lite, false);
    for (const required of [
      'omni-workflow', 'omni-srm', 'omni-procurement', 'omni-asset',
      'rocketmq-namesrv', 'rocketmq-broker', 'xxl-job-admin',
    ]) assert.ok(selection.services.includes(required), `缺少 ${required}`);
  });

  it('runs the same preset in either mode so lite/full share one contract', () => {
    const lite = planDevelopmentSelection(workspaceRoot, { preset: 'core', mode: 'lite' });
    const full = planDevelopmentSelection(workspaceRoot, { preset: 'core', mode: 'full' });

    assert.equal(lite.lite, true);
    assert.equal(full.lite, false);
    assert.deepEqual([...lite.services].sort(), [...full.services.filter((s) => s !== 'nacos')].sort());
    assert.ok(full.services.includes('nacos'), 'full 模式必须保留 Nacos 才能对齐同一契约');
    assert.deepEqual(full.moduleIds, lite.moduleIds);
  });

  it('refuses lite for closures that need realtime consumers instead of swallowing errors', () => {
    assert.throws(
      () => planDevelopmentSelection(workspaceRoot, { preset: 'supply-chain', mode: 'lite' }),
      /实时消费者|lite/,
    );
    assert.throws(
      () => planDevelopmentSelection(workspaceRoot, { module: 'srm', mode: 'lite' }),
      /实时消费者|lite/,
    );
  });

  it('rejects unknown modes and keeps frontend-only out of full mode', () => {
    assert.throws(
      () => planDevelopmentSelection(workspaceRoot, { preset: 'core', mode: 'turbo' as 'full' }),
      /未知模式/,
    );
    assert.throws(
      () => planDevelopmentSelection(workspaceRoot, { module: 'frontend', mode: 'full' }),
      /frontend-only/,
    );
  });

  it('sets explicit lite degradation switches without secrets', () => {
    const selection = planDevelopmentSelection(workspaceRoot, { preset: 'workflow' });
    const environment = developmentEnvironment(selection);

    assert.equal(environment.XXL_JOB_ENABLED, 'false');
    assert.equal(environment.OMNI_MQ_RELAY_ENABLED, 'false');
    assert.equal(environment.OMNI_MQ_AUTODETECT, 'false');
    assert.equal(environment.NACOS_CLIENT_ENABLED, 'false');
    assert.equal(environment.OMNI_WORKFLOW_FUNCTION_DEFINITION, '');
    assert.equal(Object.keys(environment).some((key) => /PASSWORD|TOKEN|SECRET/.test(key)), false);
  });

  it('does not synthesize full-mode switches so .env stays authoritative', () => {
    const environment = developmentEnvironment(fullSelection);

    for (const key of ['XXL_JOB_ENABLED', 'OMNI_MQ_RELAY_ENABLED', 'OMNI_MQ_AUTODETECT', 'OMNI_RUNTIME_PROFILE']) {
      assert.equal(environment[key], undefined, `full 模式不应强行设置 ${key}`);
    }
    assert.deepEqual(Object.keys(environment), []);
  });

  it('enables OTLP export and full local sampling only when observability is requested', () => {
    const disabled = developmentCommandEnvironment(
      workspaceRoot, planDevelopmentSelection(workspaceRoot, { preset: 'core' }),
    );
    const enabled = developmentCommandEnvironment(
      workspaceRoot, planDevelopmentSelection(workspaceRoot, { preset: 'core' }), true,
    );

    assert.equal(disabled.OTLP_EXPORT_ENABLED, undefined);
    assert.equal(enabled.OTLP_EXPORT_ENABLED, 'true');
    assert.equal(enabled.TRACING_SAMPLING_PROBABILITY, '1.0');
    assert.equal(enabled.MIGRATION_PUSHGATEWAY_ENABLED, 'true');
    assert.equal(enabled.MIGRATION_PUSHGATEWAY_ADDRESS, 'pushgateway:9091');
    assert.equal(Object.keys(enabled).some((key) => /PASSWORD|TOKEN|SECRET/.test(key)), false);
  });

  it('provides a Nacos-free static discovery profile to every runnable backend', () => {
    for (const moduleId of [
      'auth', 'base', 'workflow', 'crm', 'srm', 'procurement', 'asset', 'gateway',
    ]) {
      const content = readFileSync(resolve(
        workspaceRoot,
        'omni-backend',
        `omni-${moduleId}`,
        'src/main/resources/application-lite.yml',
      ), 'utf8');
      const document = YAML.parse(content) as {
        spring: { cloud: {
          nacos: { discovery: { enabled: boolean }; config: { enabled: boolean } };
          discovery: { client: { simple: { instances: Record<string, unknown[]> } } };
        } };
      };

      assert.equal(document.spring.cloud.nacos.discovery.enabled, false);
      assert.equal(document.spring.cloud.nacos.config.enabled, false);
      assert.ok(document.spring.cloud.discovery.client.simple.instances['omni-auth']?.length);
    }
  });

  it('rejects missing or ambiguous targets before starting Docker', () => {
    assert.throws(() => planDevelopmentSelection(workspaceRoot, {}), /必须且只能指定/);
    assert.throws(
      () => planDevelopmentSelection(workspaceRoot, { preset: 'core', module: 'crm' }),
      /必须且只能指定/,
    );
  });
});

describe('development volume and project safety', () => {
  it('requires explicit flag, confirmation and an explicit project before deleting volumes', () => {
    assert.doesNotThrow(() => validateVolumeDeletion(false, false));
    assert.doesNotThrow(() => validateVolumeDeletion(false, true));
    assert.throws(() => validateVolumeDeletion(true, false, 'wp011-row-1'), /confirm-delete-volumes/);
    assert.throws(() => validateVolumeDeletion(true, true, undefined), /project-name/);
    assert.throws(() => validateVolumeDeletion(true, true, ''), /project-name/);
    assert.doesNotThrow(() => validateVolumeDeletion(true, true, 'wp011-row-1'));
  });

  it('keeps named volumes by default and never appends orphans or prune flags', () => {
    const defaults = composeDownArguments(false, undefined);
    assert.equal(defaults.includes('-v'), false);
    const withProject = composeDownArguments(false, 'wp011-row-1');
    assert.deepEqual(withProject.slice(1, 3), ['--project-name', 'wp011-row-1']);
    for (const args of [defaults, withProject, composeDownArguments(true, 'wp011-row-1')]) {
      const text = args.join(' ');
      assert.equal(text.includes('--remove-orphans'), false);
      assert.equal(text.includes('prune'), false);
      assert.equal(text.includes('--rmi'), false);
    }
    assert.deepEqual(composeDownArguments(true, 'wp011-row-1').at(-1), '-v');
  });

  it('normalizes project names and rejects unsafe ones without echoing them', () => {
    assert.equal(normalizeProjectName(undefined), undefined);
    assert.equal(normalizeProjectName('wp011_row-1'), 'wp011_row-1');
    assert.deepEqual(projectArgs('abc'), ['--project-name', 'abc']);
    assert.deepEqual(projectArgs(undefined), []);
    for (const bad of ['AB-C', 'a', '-lead', 'has space', 'semi;colon', 'rm -rf /']) {
      assert.throws(() => normalizeProjectName(bad), /project-name/, `应拒绝 ${bad}`);
    }
    assert.throws(() => normalizeProjectName('semi;colon'), (error: Error) => {
      assert.equal(error.message.includes(';'), false, '非法字符不得回显');
      return true;
    });
  });

  it('derives the implicit project name only from the directory name', () => {
    assert.equal(implicitProjectName(resolve(workspaceRoot, 'tools', 'omni-cli')), 'omni-cli');
    assert.equal(implicitProjectName(resolve(workspaceRoot, '..', 'WP011', 'S08', 'core')), 'core');
  });
});

describe('development port and volume-name parameterization', () => {
  it('discovers every overridable host port and volume name from Compose', () => {
    const names = declaredOverrideNames(workspaceRoot);

    assert.ok(names.includes('OMNI_MYSQL_HOST_PORT'), '必须能按行偏移 MySQL 宿主端口');
    assert.ok(names.includes('OMNI_GATEWAY_HOST_PORT'));
    assert.ok(names.includes('OMNI_FRONTEND_HOST_PORT'));
    assert.ok(names.includes('OMNI_MYSQL_VOLUME_NAME'), '必须能按行改卷名以隔离数据');
    assert.ok(names.length >= 15, `发现的覆盖点过少: ${names.length}`);
  });

  it('accepts whitelisted overrides and keeps their values out of error text', () => {
    const environment = parseOverrideAssignments([
      'OMNI_MYSQL_HOST_PORT=23306', 'OMNI_MYSQL_VOLUME_NAME=wp011-row1-mysql',
    ], workspaceRoot);

    assert.equal(environment.OMNI_MYSQL_HOST_PORT, '23306');
    assert.equal(environment.OMNI_MYSQL_VOLUME_NAME, 'wp011-row1-mysql');
  });

  it('rejects secret variables, unknown names and malformed values before Docker runs', () => {
    const cases: Array<[string[], RegExp]> = [
      [['MYSQL_ROOT_PASSWORD=x'], /不允许覆盖/],
      [['OMNI_INTERNAL_API_TOKEN=secret-token'], /不允许覆盖/],
      [['SOME_RANDOM_NAME=1'], /不允许覆盖/],
      [['OMNI_NOT_DECLARED_HOST_PORT=1'], /Compose 未声明/],
      [['OMNI_MYSQL_HOST_PORT'], /NAME=VALUE/],
      [['OMNI_MYSQL_HOST_PORT=abc'], /端口号/],
      [['OMNI_MYSQL_HOST_PORT=70000'], /端口号/],
      [['OMNI_MYSQL_VOLUME_NAME=bad/name'], /只允许/],
      [['OMNI_MYSQL_VOLUME_NAME=ok\r\nINJECTED=1'], /非法字符|端口号|只允许/],
    ];
    for (const [assignments, pattern] of cases) {
      assert.throws(
        () => parseOverrideAssignments(assignments, workspaceRoot),
        pattern,
        `应拒绝 ${assignments[0]}`,
      );
    }
  });

  it('merges overrides into the up environment without dropping lite switches', () => {
    const selection = planDevelopmentSelection(workspaceRoot, { preset: 'core' });
    const environment = developmentCommandEnvironment(workspaceRoot, selection, false, {
      overrides: ['OMNI_MYSQL_HOST_PORT=23306'],
    });

    assert.equal(environment.OMNI_MYSQL_HOST_PORT, '23306');
    assert.equal(environment.NACOS_CLIENT_ENABLED, 'false');
  });

  it('requires a non-negative integer log tail before invoking Docker', () => {
    assert.throws(() => showModuleLogs(workspaceRoot, 'crm', false, -1), /tail/);
    assert.throws(() => showModuleLogs(workspaceRoot, 'crm', false, 1.5), /tail/);
    assert.throws(() => showModuleLogs(workspaceRoot, 'nosuchmodule'), /未知模块/);
  });
});
