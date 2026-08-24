import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Ajv, type ErrorObject, type ValidateFunction } from 'ajv';
import { CliError } from './errors.js';

const ajv = new Ajv({ allErrors: true, strict: true });
const validators = new Map<string, ValidateFunction>();

/** 加载并编译工作区 JSON Schema。 */
export function loadSchema(workspaceRoot: string, fileName: string): ValidateFunction {
  const schemaPath = resolve(workspaceRoot, 'scaffold', 'schemas', fileName);
  const cached = validators.get(schemaPath);
  if (cached !== undefined) return cached;
  try {
    const schema = JSON.parse(readFileSync(schemaPath, 'utf8')) as object;
    const validate = ajv.compile(schema);
    validators.set(schemaPath, validate);
    return validate;
  } catch (error) {
    throw new CliError(`无法加载 Schema ${schemaPath}: ${messageOf(error)}`);
  }
}

/** 把 Ajv 错误转换为稳定、可读文本。 */
export function formatSchemaErrors(errors: ErrorObject[] | null | undefined): string {
  return (errors ?? [])
    .map((error) => `${error.instancePath || '/'} ${error.message ?? '校验失败'}`)
    .join('; ');
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
