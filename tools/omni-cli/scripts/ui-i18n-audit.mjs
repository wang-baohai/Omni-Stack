import { existsSync, globSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const frontendRoot = resolve(repositoryRoot, 'omni-frontend')
const baselinePath = resolve(frontendRoot, 'i18n-hardcoded-baseline.json')
const checkOnly = process.argv.includes('--check')
const writeBaseline = process.argv.includes('--write-baseline')
const hanPattern = /\p{Script=Han}/u

function stripComments(content) {
  return content
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

function countTemplateFindings(source) {
  const template = source.match(/<template(?:\s[^>]*)?>([\s\S]*?)<\/template>/)?.[1] || ''
  return stripComments(template)
    .split(/\r?\n/)
    .filter((line) => hanPattern.test(line))
    .length
}

function countStringFindings(source) {
  let count = 0
  const sourceFile = ts.createSourceFile('ui-i18n-audit.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  function visit(node) {
    if ((ts.isStringLiteralLike(node) || ts.isTemplateLiteralToken(node)) && hanPattern.test(node.text)) {
      count += 1
    }
    ts.forEachChild(node, visit)
  }
  visit(sourceFile)
  return count
}

function collect() {
  const files = globSync('src/**/*.{ts,vue}', { cwd: frontendRoot })
    .map((file) => file.replaceAll('\\', '/'))
    .filter((file) => !file.startsWith('src/locales/') && !file.startsWith('src/i18n/'))
  const findings = {}
  for (const file of files.sort()) {
    const source = readFileSync(resolve(frontendRoot, file), 'utf8')
    const isVue = file.endsWith('.vue')
    const template = isVue ? countTemplateFindings(source) : 0
    const scriptSource = isVue
      ? source.match(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/)?.[1] || ''
      : source
    const script = countStringFindings(scriptSource)
    if (template + script > 0) findings[file] = { template, script }
  }
  return findings
}

function total(findings) {
  return Object.values(findings).reduce((sum, item) => sum + item.template + item.script, 0)
}

const current = collect()

if (writeBaseline) {
  const payload = {
    version: 1,
    purpose: '记录尚未迁移到 vue-i18n 的可见中文基线；最终目标为零。',
    generatedBy: relative(repositoryRoot, fileURLToPath(import.meta.url)).replaceAll('\\', '/'),
    total: total(current),
    files: current,
  }
  writeFileSync(baselinePath, `${JSON.stringify(payload, null, 2)}\n`)
  process.stdout.write(`已写入 UI i18n 基线：${payload.total} 项，${Object.keys(current).length} 个文件\n`)
  process.exit(0)
}

if (!existsSync(baselinePath)) {
  process.stderr.write('缺少 omni-frontend/i18n-hardcoded-baseline.json；先运行 --write-baseline\n')
  process.exit(1)
}

const baseline = JSON.parse(readFileSync(baselinePath, 'utf8'))
const failures = []
for (const [file, finding] of Object.entries(current)) {
  const previous = baseline.files[file]
  const currentCount = finding.template + finding.script
  const previousCount = previous ? previous.template + previous.script : 0
  if (currentCount > previousCount) failures.push(`${file}: ${previousCount} -> ${currentCount}`)
}

const currentTotal = total(current)
if (currentTotal > baseline.total) failures.push(`总数增加：${baseline.total} -> ${currentTotal}`)

if (failures.length > 0) {
  failures.forEach((message) => process.stderr.write(`- ${message}\n`))
  process.stderr.write('UI i18n 硬编码检查失败；新增界面文案必须进入语言包。\n')
  process.exit(1)
}

const mode = checkOnly ? '检查' : '盘点'
process.stdout.write(`UI i18n ${mode}通过：${currentTotal}/${baseline.total} 项，${Object.keys(current).length} 个文件；目标为 0。\n`)
