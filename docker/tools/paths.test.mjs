import assert from 'node:assert/strict'
import test from 'node:test'
import { daemonWorkspacePath, workspaceMount } from './entrypoint.mjs'

test('Windows and Linux daemon paths retain the actual mounted workspace', () => {
  assert.equal(daemonWorkspacePath('C:\\Example Project\\repo'), '/run/desktop/mnt/host/c/Example Project/repo')
  assert.equal(daemonWorkspacePath('/home/reader/repo'), '/home/reader/repo')
  assert.equal(workspaceMount([{ Type: 'bind', Destination: '/workspace', Source: '/home/reader/repo' }]), '/home/reader/repo')
  assert.throws(() => daemonWorkspacePath('relative/repo'), /absolute/)
  assert.throws(() => workspaceMount([{ Type: 'volume', Destination: '/workspace', Source: '/var/lib/docker' }]), /bind-mounted/)
})
