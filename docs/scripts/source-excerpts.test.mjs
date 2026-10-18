import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { test } from 'node:test'
import { parse } from 'yaml'

const root = resolve(import.meta.dirname, '../..')
const normalize = (value) => value.replaceAll('\r\n', '\n').trim()

test('source explanations quote current source exactly in all four languages', () => {
  const directory = resolve(root, 'docs/wiki')
  const documents = readdirSync(directory).filter(name => /^(?:source-debugging|workflow-development)(?:\.(?:en|jp|kr))?\.md$/.test(name))
  const catalog = parse(readFileSync(resolve(root, 'scaffold/catalog/modules.yaml'), 'utf8'))
  assert.ok(Array.isArray(catalog.modules), '源码示例测试需要当前模块目录')
  const stems = ['source-debugging', ...(catalog.modules.some(module => module.id === 'workflow') ? ['workflow-development'] : [])]
  const expected = stems.flatMap(stem => ['', '.en', '.jp', '.kr'].map(suffix => stem + suffix + '.md'))
  assert.deepEqual(documents.sort(), expected.sort(), '每个适用的源码教程必须具备全部四语言')
  for (const name of documents) {
    const markdown = readFileSync(resolve(directory, name), 'utf8').replaceAll('\r\n', '\n')
    const excerpts = [...markdown.matchAll(/<!-- omni:source path="([^"]+)" -->\n```(?:java|typescript)\n([\s\S]*?)\n```/g)]
    assert.ok(excerpts.length > 0, `${name}: missing verified source excerpts`)
    for (const [, path, excerpt] of excerpts) {
      assert.ok(!path.startsWith('/') && !path.includes('\\') && !path.includes(':') && !path.split('/').includes('..'), 'source path must stay inside the delivery')
      const source = normalize(readFileSync(resolve(root, path), 'utf8'))
      assert.ok(source.includes(normalize(excerpt)), `${name}: source excerpt drifted from ${path}`)
    }
  }
})
