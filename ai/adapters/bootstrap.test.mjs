import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { test } from 'node:test'
import { bootstrap, generatedSkill, intactGenerated } from './bootstrap.mjs'

test('entry provenance detects user edits', () => {
  const content = '---\nname: sample\ndescription: test\n---\n\nBody\n'
  const generated = generatedSkill(content)
  assert.equal(intactGenerated(generated), true)
  assert.equal(intactGenerated(generated + 'user edit'), false)
})

test('both tool adapters are repeatable and protect local edits', () => {
  const root = mkdtempSync(resolve(tmpdir(), 'omni-ai-adapter-'))
  try {
    for (const tool of ['codex', 'qoder']) {
      const plan = bootstrap({ root, tool, apply: true })
      assert.ok(plan.length >= 1)
      assert.equal(bootstrap({ root, tool }).length, 0)
      const destination = plan[0].destination
      writeFileSync(destination, readFileSync(destination, 'utf8') + '\nuser edit\n')
      assert.throws(() => bootstrap({ root, tool, apply: true }), /user-modified/)
    }
  } finally { rmSync(root, { recursive: true, force: true }) }
})
