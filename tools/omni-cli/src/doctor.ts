import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadCatalog } from './catalog.js';

export interface DoctorCheck {
  name: string;
  passed: boolean;
  detail: string;
}

/** 执行不改写工作区的基础诊断。 */
export function runDoctor(workspaceRoot: string): DoctorCheck[] {
  const [major = 0, minor = 0] = process.versions.node.split('.').map(Number);
  const nodePassed = major > 22 || (major === 22 && minor >= 12);
  const checks: DoctorCheck[] = [
    {
      name: 'Node.js',
      passed: nodePassed,
      detail: `${process.versions.node} (required >= 22.12.0)`,
    },
    {
      name: 'Maven Wrapper',
      passed: existsSync(resolve(workspaceRoot, 'omni-backend', 'mvnw.cmd'))
        || existsSync(resolve(workspaceRoot, 'omni-backend', 'mvnw')),
      detail: 'omni-backend/mvnw(.cmd)',
    },
    {
      name: 'Frontend',
      passed: existsSync(resolve(workspaceRoot, 'omni-frontend', 'package.json')),
      detail: 'omni-frontend/package.json',
    },
  ];
  try {
    const catalog = loadCatalog(workspaceRoot);
    checks.push({ name: 'Module catalog', passed: true, detail: `${catalog.modules.length} modules` });
  } catch (error) {
    checks.push({
      name: 'Module catalog',
      passed: false,
      detail: error instanceof Error ? error.message : String(error),
    });
  }
  return checks;
}
