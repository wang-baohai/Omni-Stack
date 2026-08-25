import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import YAML from 'yaml';
import {
  developmentEnvironment,
  developmentCommandEnvironment,
  planDevelopmentSelection,
  validateVolumeDeletion,
} from '../src/development.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

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

  it('requires explicit confirmation before deleting named volumes', () => {
    assert.doesNotThrow(() => validateVolumeDeletion(false, false));
    assert.throws(() => validateVolumeDeletion(true, false), /confirm-delete-volumes/);
    assert.doesNotThrow(() => validateVolumeDeletion(true, true));
  });
});
