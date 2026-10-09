import { readFileSync } from 'node:fs';
import { parseDocument } from 'yaml';
import { CliError } from './errors.js';

/** 禁止重复键并加载 YAML。 */
export function readYamlFile(filePath: string): unknown {
  const document = parseDocument(readFileSync(filePath, 'utf8'), {
    uniqueKeys: true,
  });
  if (document.errors.length > 0) {
    throw new CliError(`${filePath} YAML 无效: ${document.errors.map((error) => error.message).join('; ')}`);
  }
  return document.toJS({ maxAliasCount: 0 });
}
