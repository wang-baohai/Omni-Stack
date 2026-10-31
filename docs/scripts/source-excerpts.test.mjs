import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { relative, resolve, sep } from 'node:path'
import { test } from 'node:test'
import { parse } from 'yaml'

const root = resolve(import.meta.dirname, '../..')
const normalize = (value) => value.replaceAll('\r\n', '\n').trim()

/** 递归列出 docs/wiki 下全部 Markdown；原理页可位于子目录。 */
function allWikiPages(directory, out = []) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) allWikiPages(path, out)
    else if (entry.name.endsWith('.md')) out.push(path)
  }
  return out
}

/** 校验一份 Markdown 的源码片段；上游片段必须绑定固定版本，不伪装成本地文件。 */
function assertExcerpts(markdown, { file, sourceRoot = root } = {}) {
  const excerpts = [...markdown.matchAll(/<!-- omni:source path="([^"]+)"(?: upstream="([^"]+)")? -->\n```(?:java|typescript|yaml|hcl)\n([\s\S]*?)\n```/g)]
  assert.ok(excerpts.length > 0, `${file}: missing verified source excerpts`)
  for (const [, path, upstream, excerpt] of excerpts) {
    assert.ok(!path.startsWith('/') && !path.includes('\\') && !path.includes(':') && !path.split('/').includes('..'), 'source path must stay inside the delivery')
    if (upstream) {
      assert.match(upstream, /^[A-Za-z0-9_./-]+@[0-9a-f]{7,40}$/, `${file}: upstream excerpt needs repository@commit`)
      continue
    }
    const source = normalize(readFileSync(resolve(sourceRoot, path), 'utf8'))
    assert.ok(source.includes(normalize(excerpt)), `${file}: source excerpt drifted from ${path}`)
  }
}

/** 返回缺失的适用语言文件；stems 只包含当前交付实际保留的模块教程。 */
function missingLanguages(files, stems) {
  const expected = stems.flatMap(stem => ['', '.en', '.jp', '.kr'].map(suffix => stem + suffix + '.md'))
  return expected.filter((name) => !files.includes(name))
}

test('source explanations quote current source exactly in all four languages', () => {
  const directory = resolve(root, 'docs/wiki')
  const documents = readdirSync(directory).filter(name => /^(?:source-debugging|workflow-development)(?:\.(?:en|jp|kr))?\.md$/.test(name))
  const catalog = parse(readFileSync(resolve(root, 'scaffold/catalog/modules.yaml'), 'utf8'))
  assert.ok(Array.isArray(catalog.modules), '源码示例测试需要当前模块目录')
  const stems = ['source-debugging', ...(catalog.modules.some(module => module.id === 'workflow') ? ['workflow-development'] : [])]
  assert.deepEqual(missingLanguages(documents, stems), [], '每个适用的源码教程必须具备全部四语言')
  for (const name of documents) {
    const markdown = readFileSync(resolve(directory, name), 'utf8').replaceAll('\r\n', '\n')
    assertExcerpts(markdown, { file: name })
  }
})

test('every principle page with omni:source markers is verified recursively', () => {
  const pages = allWikiPages(resolve(root, 'docs/wiki'))
    .filter((file) => readFileSync(file, 'utf8').includes('<!-- omni:source'))
  assert.ok(pages.length >= 2, '至少应发现两篇带源码标记的原理页')
  for (const file of pages) {
    assertExcerpts(readFileSync(file, 'utf8').replaceAll('\r\n', '\n'), { file: relative(root, file).split(sep).join('/') })
  }
})

test('YAML and Alloy excerpts match source and reject changed values or target files', () => {
  const workspace = mkdtempSync(resolve(tmpdir(), 'omni-config-excerpts-'))
  try {
    const yaml = 'receivers:\n  - name: local-null\n'
    const alloy = 'rule {\n  action = "keep"\n}\n'
    writeFileSync(resolve(workspace, 'alertmanager.yml'), yaml)
    writeFileSync(resolve(workspace, 'config.alloy'), alloy)
    const yamlExcerpt = '<!-- omni:source path="alertmanager.yml" -->\n```yaml\n' + yaml + '```\n'
    const alloyExcerpt = '<!-- omni:source path="config.alloy" -->\n```hcl\n' + alloy + '```\n'
    assert.doesNotThrow(() => assertExcerpts(yamlExcerpt, { file: 'yaml.md', sourceRoot: workspace }))
    assert.doesNotThrow(() => assertExcerpts(alloyExcerpt, { file: 'alloy.md', sourceRoot: workspace }))
    assert.throws(() => assertExcerpts(yamlExcerpt.replace('local-null', 'delivered'),
      { file: 'changed-yaml.md', sourceRoot: workspace }), /drifted/)
    assert.throws(() => assertExcerpts(alloyExcerpt.replace('"keep"', '"drop"'),
      { file: 'changed-alloy.md', sourceRoot: workspace }), /drifted/)
    assert.throws(() => assertExcerpts(yamlExcerpt.replace('alertmanager.yml', 'missing.yml'),
      { file: 'missing-config.md', sourceRoot: workspace }), /ENOENT/)
  } finally {
    rmSync(workspace, { recursive: true, force: true })
  }
})

test('tampered excerpt, missing source, missing language and unversioned upstream fail', () => {
  const workspace = mkdtempSync(resolve(tmpdir(), 'omni-source-excerpts-'))
  try {
    const sourcePath = 'sample/Source.java'
    mkdirSync(resolve(workspace, 'sample'), { recursive: true })
    writeFileSync(resolve(workspace, sourcePath), 'class Source {\n  int value = 1;\n}\n')
    const ok = '<!-- omni:source path="sample/Source.java" -->\n```java\nclass Source {\n  int value = 1;\n}\n```\n'
    assert.doesNotThrow(() => assertExcerpts(ok, { file: 'ok.md', sourceRoot: workspace }))

    const tampered = ok.replace('value = 1', 'value = 2')
    assert.throws(() => assertExcerpts(tampered, { file: 'tampered.md', sourceRoot: workspace }), /drifted/)

    const missing = ok.replace('sample/Source.java', 'sample/Missing.java')
    assert.throws(() => assertExcerpts(missing, { file: 'missing.md', sourceRoot: workspace }), /ENOENT/)

    const upstream = '<!-- omni:source path="sample/Source.java" upstream="vendor/lib@deadbeef" -->\n```java\nclass Upstream {}\n```\n'
    assert.doesNotThrow(() => assertExcerpts(upstream, { file: 'upstream.md', sourceRoot: workspace }))
    const unversioned = upstream.replace(' upstream="vendor/lib@deadbeef"', ' upstream="vendor/lib"')
    assert.throws(() => assertExcerpts(unversioned, { file: 'unversioned.md', sourceRoot: workspace }), /repository@commit/)

    const threeLanguages = ['source-debugging.md', 'source-debugging.en.md', 'source-debugging.jp.md']
    assert.deepEqual(missingLanguages(threeLanguages, ['source-debugging']), ['source-debugging.kr.md'])
  } finally {
    rmSync(workspace, { recursive: true, force: true })
  }
})
