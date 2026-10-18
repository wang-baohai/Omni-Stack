import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const canonicalRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
export const digest = (content) => createHash('sha256').update(content).digest('hex')

/** 派生入口嵌入自己的摘要，用户修改后拒绝覆盖，不依赖个人绝对路径。 */
export function generatedSkill(content) {
  const normalized = content.replace(/\r\n?/g, '\n')
  const match = /^---\n[\s\S]*?\n---\n/.exec(normalized)
  if (!match) throw new Error('skill frontmatter missing')
  const head = match[0]
  const body = normalized.slice(head.length)
  return `${head}\n<!-- generated-by: omni-ai; content-sha256: ${digest(head + body)}; source: ai/skills -->\n${body}`
}

export function intactGenerated(content) {
  const marker = /\n<!-- generated-by: omni-ai; content-sha256: ([0-9a-f]{64}); source: ai\/skills -->\n/
  const match = marker.exec(content)
  return Boolean(match && digest(content.replace(marker, '')) === match[1])
}

export function bootstrap({ tool, apply = false, root = resolve(canonicalRoot, '..'), skillsRoot = resolve(canonicalRoot, 'skills') }) {
  if (!['codex', 'qoder'].includes(tool)) throw new Error('tool must be codex or qoder')
  const destinationRoot = resolve(root, tool === 'codex' ? '.agents/skills' : '.qoder/skills')
  const planned = []
  for (const name of readdirSync(skillsRoot)) {
    if (!/^[a-z0-9-]+$/.test(name)) throw new Error(`invalid skill name: ${name}`)
    const source = readFileSync(resolve(skillsRoot, name, 'SKILL.md'), 'utf8').replace(/\r\n?/g, '\n')
    const destination = resolve(destinationRoot, name, 'SKILL.md')
    if (!destination.startsWith(`${destinationRoot}${sep}`)) throw new Error('unsafe destination')
    const generated = generatedSkill(source)
    if (existsSync(destination)) {
      const existing = readFileSync(destination, 'utf8').replace(/\r\n?/g, '\n')
      if (existing === generated) continue
      // 允许迁移与规范源逐字相同的已有入口，其余未托管内容拒绝覆盖。
      if (existing !== source && !intactGenerated(existing)) throw new Error(`user-modified skill: ${destination}`)
    }
    planned.push({ destination, generated })
  }
  // 全部检查完成才写；任一用户内容冲突都不会造成半套更新。
  if (apply) for (const item of planned) {
    mkdirSync(dirname(item.destination), { recursive: true })
    writeFileSync(item.destination, item.generated)
  }
  console.log(`${tool}: ${planned.length} ${apply ? 'installed/updated' : 'pending'} entries`)
  return planned
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2)
  const tool = args.find((value) => value.startsWith('--tool='))?.slice(7)
  if (args.some((value) => !['--apply', '--check'].includes(value) && !/^--tool=(codex|qoder)$/.test(value))) throw new Error('unknown arguments')
  const planned = bootstrap({ tool, apply: args.includes('--apply') })
  if (args.includes('--check') && planned.length) process.exitCode = 1
}
