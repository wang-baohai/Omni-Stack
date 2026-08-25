import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import {
  parseConfiguredEnvironmentNames,
  parseJavaMajor,
  requiredComposeEnvironmentNames,
} from '../src/doctor.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

describe('doctor', () => {
  it('extracts required Compose names without reading values', () => {
    const names = requiredComposeEnvironmentNames(workspaceRoot);

    assert.ok(names.includes('MYSQL_ROOT_PASSWORD'));
    assert.ok(names.includes('OMNI_INTERNAL_API_TOKEN'));
    assert.equal(names.some((name) => name.includes('=')), false);
  });

  it('reports only configured env names and rejects placeholders', () => {
    const names = parseConfiguredEnvironmentNames([
      'MYSQL_ROOT_PASSWORD=local-only',
      'OMNI_DB_PASSWORD=',
      'REDIS_PASSWORD=<replace-me>',
      'NACOS_AUTH_TOKEN=value',
      'XXL_JOB_ACCESS_TOKEN=replace-with-a-random-token',
    ].join('\n'));

    assert.deepEqual(names, new Set(['MYSQL_ROOT_PASSWORD', 'NACOS_AUTH_TOKEN']));
  });

  it('parses modern and legacy Java version formats', () => {
    assert.equal(parseJavaMajor('java version "25.0.2" 2026-01-20 LTS'), 25);
    assert.equal(parseJavaMajor('java version "1.8.0_402"'), 8);
    assert.equal(parseJavaMajor('not java'), undefined);
  });
});
