#!/usr/bin/env node
import { Command } from 'commander';
import { loadCatalog } from './catalog.js';
import { runDoctor } from './doctor.js';
import { CliError } from './errors.js';
import { listPresetIds, resolvePreset } from './presets.js';
import { findWorkspaceRoot } from './workspace.js';

const program = new Command();
program.name('omni').description('Omni-Stack 项目脚手架 CLI').version('0.1.0');
program.option('--root <directory>', '工作区根目录');

const catalog = program.command('catalog').description('模块清单命令');
catalog.command('validate').description('校验模块清单').action(() => {
  const root = workspaceRoot();
  const value = loadCatalog(root);
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

function workspaceRoot(): string {
  const options = program.opts<{ root?: string }>();
  return findWorkspaceRoot(options.root ?? process.cwd());
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
