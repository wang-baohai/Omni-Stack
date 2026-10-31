import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve } from 'node:path'
import { test } from 'node:test'
import { copyReferencedUpstreamAssets, upstreamAssetRoute, UPSTREAM_PACKAGES } from './publish-upstream.mjs'

const digest = value => createHash('sha256').update(value).digest('hex')

/** 仅创建受控测试数据，不把这份夹具当作官方源码或许可的验收证据。 */
function fixture(name = 'spring-security-7.0.5') {
  const root = mkdtempSync(resolve(tmpdir(), 'omni-upstream-public-'))
  const prefix = `docs/reference/upstream/${name}`
  mkdirSync(resolve(root, prefix), { recursive: true })
  mkdirSync(resolve(root, 'docs/wiki'), { recursive: true })
  writeFileSync(resolve(root, 'docs/wiki/sample.md'), `[license](../reference/upstream/${name}/LICENSE.txt)\n`)
  writeFileSync(resolve(root, prefix, 'LICENSE.txt'), 'Controlled test license text\n')
  const excerpts = UPSTREAM_PACKAGES[name].map((file, index) => {
    const bytes = `// controlled excerpt fixture ${index}\n`
    writeFileSync(resolve(root, prefix, file), bytes)
    return { file, excerpt_sha256: digest(bytes) }
  })
  if (name === 'xxl-job-3.3.1') writeFileSync(resolve(root, prefix, 'NOTICE.txt'), 'Controlled upstream copyright notice\n')
  writeFileSync(resolve(root, prefix, 'provenance.json'), JSON.stringify({ license: name === 'xxl-job-3.3.1' ? 'GPL-3.0-only' : 'Apache-2.0',
    license_sha256: digest('Controlled test license text\n'),
    ...(name === 'xxl-job-3.3.1' ? { notice_sha256: digest('Controlled upstream copyright notice\n') } : {}), excerpts }))
  return { root, prefix, output: resolve(root, 'site'), pages: [{ source: 'docs/wiki/sample.md' }] }
}

test('only exact public assets from referenced packages are copied', () => {
  const f = fixture()
  try {
    writeFileSync(resolve(f.root, f.prefix, 'private.log'), 'must stay outside the site')
    mkdirSync(resolve(f.root, 'docs/reference/upstream/flowable-8.0.0'), { recursive: true })
    writeFileSync(resolve(f.root, 'docs/reference/upstream/flowable-8.0.0/private.json'), '{}')
    const files = copyReferencedUpstreamAssets(f.root, f.output, f.pages)
    assert.equal(files.length, 5)
    const published = resolve(f.output, 'public', f.prefix.slice(5))
    assert.deepEqual(readdirSync(published).sort(), ['LICENSE.txt', 'provenance.json', ...UPSTREAM_PACKAGES['spring-security-7.0.5']].sort())
    assert.equal(readFileSync(resolve(published, 'LICENSE.txt'), 'utf8'), 'Controlled test license text\n')
    assert.equal(existsSync(resolve(published, 'private.log')), false)
    assert.equal(existsSync(resolve(f.output, 'public/reference/upstream/flowable-8.0.0')), false)
    assert.equal(upstreamAssetRoute(`${f.prefix}/LICENSE.txt`), '/reference/upstream/spring-security-7.0.5/LICENSE.txt')
    for (const path of [`${f.prefix}/private.log`, `${f.prefix}/extra.json`, 'docs/reference/upstream/../private.json']) {
      assert.equal(upstreamAssetRoute(path), null)
    }
  } finally { rmSync(f.root, { recursive: true, force: true }) }
})

test('XXL-JOB exact-version license and notice are required without changing other package policies', () => {
  for (const kind of ['valid', 'wrong-license', 'missing-notice', 'changed-notice', 'missing-notice-digest', 'other-package-license']) {
    const f = fixture(kind === 'other-package-license' ? 'spring-security-7.0.5' : 'xxl-job-3.3.1')
    try {
      const provenancePath = resolve(f.root, f.prefix, 'provenance.json')
      const provenance = JSON.parse(readFileSync(provenancePath, 'utf8'))
      if (kind === 'wrong-license') provenance.license = 'Apache-2.0'
      if (kind === 'other-package-license') provenance.license = 'GPL-3.0-only'
      if (kind === 'missing-notice-digest') delete provenance.notice_sha256
      writeFileSync(provenancePath, JSON.stringify(provenance))
      if (kind === 'missing-notice') rmSync(resolve(f.root, f.prefix, 'NOTICE.txt'))
      if (kind === 'changed-notice') writeFileSync(resolve(f.root, f.prefix, 'NOTICE.txt'), 'changed notice')
      if (kind === 'valid') {
        const files = copyReferencedUpstreamAssets(f.root, f.output, f.pages)
        assert.equal(files.length, 8)
        assert.equal(readFileSync(resolve(f.output, 'public', f.prefix.slice(5), 'NOTICE.txt'), 'utf8'), 'Controlled upstream copyright notice\n')
        assert.equal(upstreamAssetRoute(`${f.prefix}/NOTICE.txt`), '/reference/upstream/xxl-job-3.3.1/NOTICE.txt')
        assert.equal(upstreamAssetRoute(`${f.prefix}/unregistered.java`), null)
      } else {
        assert.throws(() => copyReferencedUpstreamAssets(f.root, f.output, f.pages), /missing public upstream|digest mismatch/)
        assert.equal(existsSync(resolve(f.output, 'public')), false)
      }
    } finally { rmSync(f.root, { recursive: true, force: true }) }
  }
})

test('missing files, changed excerpts or licenses and symbolic links cannot publish', () => {
  for (const kind of ['missing', 'excerpt', 'license', 'symlink']) {
    const f = fixture()
    try {
      const excerpt = resolve(f.root, f.prefix, UPSTREAM_PACKAGES['spring-security-7.0.5'][0])
      if (kind === 'missing') rmSync(resolve(f.root, f.prefix, 'LICENSE.txt'))
      if (kind === 'excerpt') writeFileSync(excerpt, '// changed source\n')
      if (kind === 'license') writeFileSync(resolve(f.root, f.prefix, 'LICENSE.txt'), 'changed license')
      if (kind === 'symlink') {
        rmSync(excerpt)
        symlinkSync(resolve(f.root, f.prefix, 'LICENSE.txt'), excerpt)
      }
      assert.throws(() => copyReferencedUpstreamAssets(f.root, f.output, f.pages), /missing public upstream|digest mismatch/)
      assert.equal(existsSync(resolve(f.output, 'public')), false)
    } finally { rmSync(f.root, { recursive: true, force: true }) }
  }
})
