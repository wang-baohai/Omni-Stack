import assert from 'node:assert/strict';
import { copyFileSync, cpSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import {
  doctorExitCode,
  parseConfiguredEnvironmentNames,
  parseJavaMajor,
  requiredComposeEnvironmentNames,
  runDoctor,
} from '../src/doctor.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const tempRoot = resolve(import.meta.dirname, '..', '.tmp-agentC');

describe('doctor', () => {
  it('extracts required Compose names without reading values', () => {
    const names = requiredComposeEnvironmentNames(workspaceRoot);

    assert.ok(names.includes('MYSQL_ROOT_PASSWORD'));
    assert.ok(names.includes('OMNI_INTERNAL_API_TOKEN'));
    assert.equal(names.some((name) => name.includes('=')), false);
  });

  it('scans every Compose file that participates in interpolation', () => {
    const fixture = resolve(tempRoot, 'scan-coverage');
    rmSync(fixture, { recursive: true, force: true });
    mkdirSync(fixture, { recursive: true });
    writeFileSync(resolve(fixture, 'compose.infra.yaml'), 'services: {}\n');
    writeFileSync(resolve(fixture, 'compose.apps.yaml'), 'services: {}\n');
    writeFileSync(resolve(fixture, 'compose.observability.yaml'),
      'services:\n  grafana:\n    environment:\n      P: ${GRAFANA_ONLY_SECRET:?missing}\n');
    writeFileSync(resolve(fixture, 'compose.yaml'),
      'services:\n  extra:\n    environment:\n      Q: ${ROOT_ONLY_SECRET:?missing}\n');

    const names = requiredComposeEnvironmentNames(fixture);

    assert.deepEqual(names, ['GRAFANA_ONLY_SECRET', 'ROOT_ONLY_SECRET']);
  });

  it('reports only configured env names and rejects placeholders and comments', () => {
    const names = parseConfiguredEnvironmentNames([
      'MYSQL_ROOT_PASSWORD=local-only',
      'OMNI_DB_PASSWORD=',
      'REDIS_PASSWORD=<replace-me>',
      'NACOS_AUTH_TOKEN=value',
      'XXL_JOB_ACCESS_TOKEN=replace-with-a-random-token',
      '# COMMENTED_SECRET=should-not-count',
    ].join('\n'));

    assert.deepEqual(names, new Set(['MYSQL_ROOT_PASSWORD', 'NACOS_AUTH_TOKEN']));
  });

  it('parses modern and legacy Java version formats', () => {
    assert.equal(parseJavaMajor('java version "25.0.2" 2026-01-20 LTS'), 25);
    assert.equal(parseJavaMajor('java version "1.8.0_402"'), 8);
    assert.equal(parseJavaMajor('not java'), undefined);
  });

  it('covers every AC15 failure class with its own check', () => {
    const names = runDoctor(workspaceRoot).map((check) => check.name);

    for (const required of [
      'Node.js', 'JDK', 'Maven Wrapper', 'Frontend', 'Module catalog', 'Preset closure', '.env',
      'Disk', 'Docker Compose', 'Docker daemon', 'Compose profiles', 'Ports', 'Volume isolation',
      'Runtime inventory', 'Database connectivity', 'Nacos registration',
    ]) {
      assert.ok(names.includes(required), `doctor 缺少检查项: ${required}`);
    }
  });

  it('gives every non-passing check a remediation hint', () => {
    for (const check of runDoctor(tempFixtureWithoutEnv())) {
      if (check.status === 'pass') {
        assert.equal(check.passed, true);
        continue;
      }
      assert.ok(check.hint.length > 0, `${check.name} 缺少定位建议`);
      assert.ok(check.detail.length > 0, `${check.name} 缺少事实描述`);
      assert.equal(check.passed, false, 'skip 不得伪装成 pass');
    }
  });

  it('exits non-zero on failure and keeps skip out of the pass/fail exit code', () => {
    assert.equal(doctorExitCode([{ status: 'pass' } as never]), 0);
    assert.equal(doctorExitCode([{ status: 'skip' } as never]), 0);
    assert.equal(doctorExitCode([{ status: 'pass' }, { status: 'fail' }] as never[]), 1);
    const real = runDoctor(workspaceRoot);
    assert.equal(doctorExitCode(real), real.some((check) => check.status === 'fail') ? 1 : 0);
  });

  it('never prints environment values even when they are configured', () => {
    const fixture = tempFixtureWithSentinelEnv();
    const sentinels = [
      'SENTINEL-ROOT-PW-9f3a', 'SENTINEL-APP-PW-9f3a', 'SENTINEL-REDIS-9f3a',
      'SENTINEL-NACOS-TOKEN-9f3a', 'SENTINEL-INTERNAL-TOKEN-9f3a', 'SENTINEL-JWK-KEY-9f3a',
      'SENTINEL-OAUTH-SECRET-9f3a', 'SENTINEL-XXL-PW-9f3a', 'SENTINEL-XXL-USER-9f3a',
      'SENTINEL-NACOS-IDENTITY-9f3a',
    ];
    const rendered = runDoctor(fixture)
      .map((check) => `${check.name} ${check.status} ${check.detail} ${check.hint}`)
      .join('\n');

    for (const sentinel of sentinels) {
      assert.equal(rendered.includes(sentinel), false, `doctor 输出了变量值: ${sentinel.slice(0, 8)}...`);
    }
    assert.equal(rendered.includes('SENTINEL'), false);
    assert.ok(rendered.includes('.env'), '缺少 .env 检查输出');
  });
});

function tempFixtureWithoutEnv(): string {
  const fixture = resolve(tempRoot, 'no-env');
  prepareFixture(fixture);
  rmSync(resolve(fixture, '.env'), { force: true });
  return fixture;
}

function tempFixtureWithSentinelEnv(): string {
  const fixture = resolve(tempRoot, 'sentinel-env');
  prepareFixture(fixture);
  const values: Record<string, string> = {
    MYSQL_ROOT_PASSWORD: 'SENTINEL-ROOT-PW-9f3a',
    OMNI_DB_PASSWORD: 'SENTINEL-APP-PW-9f3a',
    REDIS_PASSWORD: 'SENTINEL-REDIS-9f3a',
    NACOS_AUTH_TOKEN: 'SENTINEL-NACOS-TOKEN-9f3a',
    NACOS_AUTH_IDENTITY_KEY: 'SENTINEL-NACOS-IDENTITY-9f3a',
    NACOS_AUTH_IDENTITY_VALUE: 'SENTINEL-NACOS-IDENTITY-9f3a',
    OMNI_INTERNAL_API_TOKEN: 'SENTINEL-INTERNAL-TOKEN-9f3a',
    JWK_ENCRYPT_KEY: 'SENTINEL-JWK-KEY-9f3a',
    OAUTH2_STATE_SECRET: 'SENTINEL-OAUTH-SECRET-9f3a',
    XXL_JOB_ACCESS_TOKEN: 'SENTINEL-XXL-TOKEN-9f3a',
    XXL_JOB_ADMIN_USERNAME: 'SENTINEL-XXL-USER-9f3a',
    XXL_JOB_ADMIN_PASSWORD: 'SENTINEL-XXL-PW-9f3a',
  };
  writeFileSync(resolve(fixture, '.env'),
    Object.entries(values).map(([name, value]) => `${name}=${value}`).join('\n'), 'utf8');
  return fixture;
}

function prepareFixture(fixture: string): void {
  rmSync(fixture, { recursive: true, force: true });
  mkdirSync(fixture, { recursive: true });
  cpSync(resolve(workspaceRoot, 'scaffold'), resolve(fixture, 'scaffold'), { recursive: true });
  for (const file of ['compose.yaml', 'compose.infra.yaml', 'compose.apps.yaml', 'compose.observability.yaml']) {
    copyFileSync(resolve(workspaceRoot, file), resolve(fixture, file));
  }
  mkdirSync(resolve(fixture, 'omni-backend'), { recursive: true });
  writeFileSync(resolve(fixture, 'omni-backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw'), '', 'utf8');
  mkdirSync(resolve(fixture, 'omni-frontend'), { recursive: true });
  writeFileSync(resolve(fixture, 'omni-frontend', 'package.json'), '{}', 'utf8');
}
