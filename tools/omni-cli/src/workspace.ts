import { existsSync } from 'node:fs';
import { dirname, parse, resolve } from 'node:path';
import { CliError } from './errors.js';

const CATALOG_PATH = 'scaffold/catalog/modules.yaml';

/**
 * 从指定目录向上定位 Omni-Stack 工作区。
 */
export function findWorkspaceRoot(startDirectory = process.cwd()): string {
  let current = resolve(startDirectory);
  const root = parse(current).root;
  while (true) {
    if (existsSync(resolve(current, CATALOG_PATH))) {
      return current;
    }
    if (current === root) {
      throw new CliError(`无法定位工作区，未找到 ${CATALOG_PATH}`);
    }
    current = dirname(current);
  }
}
