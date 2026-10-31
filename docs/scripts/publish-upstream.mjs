import { createHash } from 'node:crypto'
import { cpSync, existsSync, lstatSync, mkdirSync, readFileSync, realpathSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

/** 只交付已审读的固定版本短摘录、原始版权/来源和完整许可，不公开整个上游目录。 */
export const UPSTREAM_PACKAGES = {
  'spring-security-7.0.5': ['authorization-request-dispatch.java.txt', 'pkce-s256.java.txt', 'authorization-code-consumption.java.txt'],
  'mybatis-plus-3.5.16': ['query-rewrite.java.txt', 'tenant-condition.java.txt', 'tenant-column.java.txt', 'permission-delegation.java.txt'],
  'flowable-8.0.0': ['task-command.java.txt', 'task-advance.java.txt', 'parallel-completion.java.txt', 'multi-instance-cleanup.java.txt', 'deployment-command.java.txt'],
  'xxl-job-3.3.1': ['schedule-lock.java.txt', 'trigger-envelope.java.txt', 'handler-registration.java.txt', 'handler-invocation.java.txt', 'completion-callback.java.txt'],
}

const packageLicenses = {
  'spring-security-7.0.5': 'Apache-2.0',
  'mybatis-plus-3.5.16': 'Apache-2.0',
  'flowable-8.0.0': 'Apache-2.0',
  'xxl-job-3.3.1': 'GPL-3.0-only',
}
const packageFiles = (name, excerpts) => [...excerpts, 'LICENSE.txt', 'provenance.json', ...(name === 'xxl-job-3.3.1' ? ['NOTICE.txt'] : [])]
const permitted = new Set(Object.entries(UPSTREAM_PACKAGES).flatMap(([name, files]) =>
  packageFiles(name, files).map(file => `docs/reference/upstream/${name}/${file}`)))
const digest = bytes => createHash('sha256').update(bytes).digest('hex')

/** 链接只有精确命中静态白名单才转站内；未登记文件仍按仓库链接处理。 */
export function upstreamAssetRoute(repositoryPath) {
  return permitted.has(repositoryPath) ? `/${repositoryPath.slice(5)}` : null
}

/** 只复制当前发布页面引用的包；校验整包后写入站点，缺文件、漂移或软链接均失败。 */
export function copyReferencedUpstreamAssets(repositoryRoot, output, pages) {
  const content = pages.map(page => readFileSync(resolve(repositoryRoot, page.source), 'utf8')).join('\n')
  const planned = []
  for (const [name, excerpts] of Object.entries(UPSTREAM_PACKAGES)) {
    if (!content.includes(`reference/upstream/${name}/`)) continue
    const prefix = `docs/reference/upstream/${name}`
    const inputs = new Map()
    for (const file of packageFiles(name, excerpts)) {
      const path = `${prefix}/${file}`, absolute = resolve(repositoryRoot, path)
      if (!existsSync(absolute) || !lstatSync(absolute).isFile() || realpathSync(absolute) !== absolute) {
        throw new Error(`Invalid or missing public upstream asset: ${path}`)
      }
      const bytes = readFileSync(absolute)
      if (bytes.length === 0 || bytes.length > 65536) throw new Error(`Invalid upstream asset size: ${path}`)
      inputs.set(file, bytes)
    }
    const provenance = JSON.parse(inputs.get('provenance.json').toString('utf8'))
    if (provenance.license !== packageLicenses[name] || digest(inputs.get('LICENSE.txt')) !== provenance.license_sha256) {
      throw new Error(`Upstream license digest mismatch: ${name}`)
    }
    if (name === 'xxl-job-3.3.1' && digest(inputs.get('NOTICE.txt')) !== provenance.notice_sha256) {
      throw new Error(`Upstream notice digest mismatch: ${name}`)
    }
    const names = provenance.excerpts?.map(entry => entry.file)
    if (!Array.isArray(names) || names.length !== excerpts.length || new Set(names).size !== excerpts.length ||
      !excerpts.every(file => names.includes(file))) throw new Error(`Invalid upstream excerpt inventory: ${name}`)
    for (const entry of provenance.excerpts) {
      if (digest(inputs.get(entry.file)) !== entry.excerpt_sha256) throw new Error(`Upstream excerpt digest mismatch: ${name}/${entry.file}`)
    }
    for (const [file, bytes] of inputs) planned.push({ path: `${prefix}/${file}`, sha256: digest(bytes) })
  }
  for (const entry of planned) {
    const destination = resolve(output, 'public', entry.path.slice(5))
    mkdirSync(dirname(destination), { recursive: true })
    cpSync(resolve(repositoryRoot, entry.path), destination)
  }
  return planned
}
