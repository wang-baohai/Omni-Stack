#!/usr/bin/env node
import { Command } from 'commander';
import { isAbsolute, resolve } from 'node:path';
import { loadCatalog, validateCatalogResources } from './catalog.js';
import { runDoctor } from './doctor.js';
import { CliError } from './errors.js';
import { renderCrudGeneration, validateExistingCrud } from './crud-generator.js';
import { loadCrudSpec } from './crud-spec.js';
import { listPresetIds, resolvePreset } from './presets.js';
import {
  applyServiceGeneration,
  GENERATOR_VERSION,
  planServiceGeneration,
  validateGeneratedService,
} from './service-generator.js';
import { renderServiceIntegration } from './service-integration-renderer.js';
import { applyRenderedIntegration } from './service-integration-transaction.js';
import type { CreateServiceOptions } from './types.js';
import { findWorkspaceRoot } from './workspace.js';

const program = new Command();
program.name('omni').description('Omni-Stack 项目脚手架 CLI').version(GENERATOR_VERSION);
program.option('--root <directory>', '工作区根目录');

const catalog = program.command('catalog').description('模块清单命令');
catalog.command('validate').description('校验模块清单').action(() => {
  const root = workspaceRoot();
  const value = loadCatalog(root);
  validateCatalogResources(root, value);
  console.log(`catalog valid: version=${value.version}, modules=${value.modules.length}`);
});

const preset = program.command('preset').description('项目裁剪预设命令');
preset.command('list').description('列出预设').action(() => {
  listPresetIds(workspaceRoot()).forEach((id) => console.log(id));
});
preset.command('validate <preset-id>').description('校验预设及依赖闭包').action((presetId: string) => {
  const root = workspaceRoot();
  const resolved = resolvePreset(root, loadCatalog(root), presetId);
  console.log(`preset valid: ${presetId}, modules=${resolved.resolvedModules.join(',')}`);
});
preset.command('explain <preset-id>').description('解释预设').action((presetId: string) => {
  const root = workspaceRoot();
  const resolved = resolvePreset(root, loadCatalog(root), presetId);
  console.log(`${resolved.preset.displayName} (${resolved.preset.id}@${resolved.preset.version})`);
  console.log(resolved.preset.description);
  console.log(`explicit: ${resolved.explicitModules.join(', ')}`);
  console.log(`resolved: ${resolved.resolvedModules.join(', ')}`);
});
preset.command('diff <left> <right>').description('比较两个预设').action((left: string, right: string) => {
  const root = workspaceRoot();
  const moduleCatalog = loadCatalog(root);
  const leftModules = new Set(resolvePreset(root, moduleCatalog, left).resolvedModules);
  const rightModules = new Set(resolvePreset(root, moduleCatalog, right).resolvedModules);
  const onlyLeft = [...leftModules].filter((id) => !rightModules.has(id));
  const onlyRight = [...rightModules].filter((id) => !leftModules.has(id));
  console.log(`only ${left}: ${onlyLeft.join(', ') || '-'}`);
  console.log(`only ${right}: ${onlyRight.join(', ') || '-'}`);
});

program.command('doctor').description('检查本地开发环境与工作区').action(() => {
  const checks = runDoctor(workspaceRoot());
  checks.forEach((check) => console.log(`${check.passed ? 'PASS' : 'FAIL'} ${check.name}: ${check.detail}`));
  if (checks.some((check) => !check.passed)) process.exitCode = 1;
});

const generate = program.command('generate').description('代码生成命令');
generate.command('crud')
  .description('从安全声明生成标准单表全栈 CRUD（默认 dry-run）')
  .requiredOption('--spec <file>', 'CRUD YAML 声明文件')
  .option('--dry-run', '只读渲染并显示变更计划')
  .option('--apply', '执行跨文件原子写入')
  .option('--check', '仅校验声明，不渲染或写入')
  .action((commandOptions: { spec: string; dryRun?: boolean; apply?: boolean; check?: boolean }) => {
    const root = workspaceRoot();
    if (commandOptions.check === true) {
      const spec = loadCrudSpec(root, commandOptions.spec);
      console.log(`crud spec valid: ${spec.moduleId}:${spec.aggregateName}, fields=${spec.fields.length}`);
      return;
    }
    if (commandOptions.apply === true && commandOptions.dryRun === true) {
      throw new CliError('--apply 与 --dry-run 不能同时使用');
    }
    const rendered = renderCrudGeneration(root, commandOptions.spec);
    console.log(`${commandOptions.apply === true ? 'APPLY' : 'DRY-RUN'} CRUD ${rendered.plan.aggregateKey}`);
    if (rendered.plan.unchanged) {
      const lock = validateExistingCrud(root, commandOptions.spec);
      console.log(`unchanged: files=${lock.files.length}, template=${lock.templateVersion}`);
      return;
    }
    rendered.plan.operations.forEach((operation) => {
      console.log(`  ${operation.kind.toUpperCase()} ${operation.target} - ${operation.description}`);
    });
    rendered.plan.warnings.forEach((warning) => console.log(`  WARN ${warning}`));
    if (commandOptions.apply !== true) {
      console.log(`ready: ${rendered.changes.length} file changes; 未写入工作区，确认后追加 --apply。`);
      return;
    }
    const result = applyRenderedIntegration(root, rendered);
    console.log(`crud applied: files=${result.files}`);
    result.cleanupWarnings.forEach((warning) => console.log(`  WARN ${warning}`));
  });

program.command('create-service <service-id>')
  .description('生成新微服务接入包（默认仅预览）')
  .option('--package <java-package>', 'Java 包名')
  .option('--display-name <name>', '模块显示名称')
  .option('--api-prefix <path>', 'API 路径前缀')
  .option('--service-port <port>', '服务端口', parsePort)
  .option('--management-port <port>', '管理端口', parsePort)
  .option('--xxl-port <port>', 'XXL-JOB 执行器端口', parsePort)
  .option('--database <name>', '数据库名')
  .option('--table-prefix <prefix>', '业务表前缀')
  .option('--oper-log', '启用操作日志')
  .option('--job', '启用 XXL-JOB')
  .option('--mq', '启用可靠消息（自动启用 XXL-JOB）')
  .option('--data-scope', '启用数据权限（必须同时声明表）')
  .option('--data-scope-table <table...>', 'DataScope 业务表清单')
  .option('--output <directory>', '生成包输出目录')
  .option('--apply', '执行原子写入')
  .action((serviceId: string, commandOptions: Record<string, unknown>) => {
    const root = workspaceRoot();
    const options = toCreateServiceOptions(commandOptions);
    const plan = planServiceGeneration(root, serviceId, options);
    console.log(`${options.apply ? 'APPLY' : 'DRY-RUN'} create ${plan.spec.artifactId}`);
    console.log(`target: ${plan.targetDirectory}`);
    console.log(`ports: service=${plan.spec.servicePort}, management=${plan.spec.managementPort}${plan.spec.enableJob ? `, xxl=${plan.spec.xxlPort}` : ''}`);
    plan.files.forEach((file) => console.log(`  CREATE ${file.path}`));
    console.log(`  CREATE omni-service.lock.json`);
    if (!options.apply) {
      console.log('未写入文件；确认后追加 --apply。');
      return;
    }
    const result = applyServiceGeneration(plan);
    console.log(result === 'created' ? '服务生成包已原子写入。' : '输入和生成文件均未变化。');
  });

const service = program.command('service').description('服务生成包命令');
service.command('validate <service-id>')
  .description('校验已生成服务包的锁文件和内容哈希')
  .requiredOption('--output <directory>', '生成包目录')
  .action((serviceId: string, commandOptions: { output: string }) => {
    const target = resolveOutput(workspaceRoot(), commandOptions.output);
    const lock = validateGeneratedService(target);
    if (lock.spec.serviceId !== serviceId) throw new CliError(`锁文件 service-id 为 ${lock.spec.serviceId}，与 ${serviceId} 不一致`);
    console.log(`service valid: ${serviceId}, files=${lock.files.length}, template=${lock.templateVersion}`);
  });
service.command('integrate <service-id>')
  .description('只读渲染服务包接入当前 monorepo 的全部变更')
  .requiredOption('--source <directory>', '已生成并通过校验的服务包目录')
  .option('--apply', '执行跨文件原子写入；默认只读 dry-run')
  .action((serviceId: string, commandOptions: { source: string; apply?: boolean }) => {
    const rendered = renderServiceIntegration(workspaceRoot(), commandOptions.source, serviceId);
    const plan = rendered.plan;
    console.log(`INTEGRATION PLAN ${serviceId}`);
    console.log(`source: ${plan.sourceDirectory}`);
    plan.operations.forEach((operation) => console.log(`  ${operation.kind.toUpperCase()} ${operation.target} - ${operation.description}`));
    plan.warnings.forEach((warning) => console.log(`  WARN ${warning}`));
    if (!plan.ready) {
      plan.conflicts.forEach((conflict) => console.log(`  CONFLICT ${conflict}`));
      process.exitCode = 2;
      return;
    }
    console.log(`rendered: ${rendered.changes.length} file changes`);
    rendered.changes.forEach((change) => {
      const beforeLines = change.before?.split(/\r?\n/).length ?? 0;
      const afterLines = change.after.split('\n').length;
      console.log(`  ${change.mode.toUpperCase()} ${change.target} (${afterLines - beforeLines >= 0 ? '+' : ''}${afterLines - beforeLines} lines)`);
    });
    if (commandOptions.apply !== true) {
      console.log(`ready: ${plan.operations.length} operations; renderer is read-only and wrote no files.`);
      console.log('未写入工作区；确认后追加 --apply。');
      return;
    }
    const result = applyRenderedIntegration(workspaceRoot(), rendered);
    console.log(`integration applied: files=${result.files}`);
    result.cleanupWarnings.forEach((warning) => console.log(`  WARN ${warning}`));
  });

function workspaceRoot(): string {
  const options = program.opts<{ root?: string }>();
  return findWorkspaceRoot(options.root ?? process.cwd());
}

function parsePort(value: string): number {
  if (!/^\d+$/.test(value)) throw new CliError(`端口必须是整数：${value}`);
  return Number(value);
}

function toCreateServiceOptions(options: Record<string, unknown>): CreateServiceOptions {
  return {
    ...(typeof options.package === 'string' ? { javaPackage: options.package } : {}),
    ...(typeof options.displayName === 'string' ? { displayName: options.displayName } : {}),
    ...(typeof options.apiPrefix === 'string' ? { apiPrefix: options.apiPrefix } : {}),
    ...(typeof options.servicePort === 'number' ? { servicePort: options.servicePort } : {}),
    ...(typeof options.managementPort === 'number' ? { managementPort: options.managementPort } : {}),
    ...(typeof options.xxlPort === 'number' ? { xxlPort: options.xxlPort } : {}),
    ...(typeof options.database === 'string' ? { databaseName: options.database } : {}),
    ...(typeof options.tablePrefix === 'string' ? { tablePrefix: options.tablePrefix } : {}),
    ...(options.operLog === true ? { operLog: true } : {}),
    ...(options.job === true ? { job: true } : {}),
    ...(options.mq === true ? { mq: true } : {}),
    ...(options.dataScope === true ? { dataScope: true } : {}),
    ...(Array.isArray(options.dataScopeTable) ? { dataScopeTable: options.dataScopeTable as string[] } : {}),
    ...(typeof options.output === 'string' ? { output: options.output } : {}),
    ...(options.apply === true ? { apply: true } : {}),
  };
}

function resolveOutput(root: string, output: string): string {
  return isAbsolute(output) ? resolve(output) : resolve(root, output);
}

program.parseAsync(process.argv).catch((error: unknown) => {
  if (error instanceof CliError) {
    console.error(`ERROR: ${error.message}`);
    process.exitCode = error.exitCode;
    return;
  }
  console.error(error);
  process.exitCode = 1;
});
