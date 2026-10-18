import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

/** Docker Desktop 的 Windows 共享路径需转换为 daemon 可用的绝对路径。 */
export function daemonWorkspacePath(source) {
  const windows = /^([A-Za-z]):[\\/](.*)$/.exec(source)
  if (windows) return `/run/desktop/mnt/host/${windows[1].toLowerCase()}/${windows[2].replaceAll('\\', '/')}`
  if (!source.startsWith('/')) throw new Error('Docker workspace mount source must be absolute')
  return source
}

/** 只从本容器的实际挂载中识别源目录，不猜用户电脑路径。 */
export function workspaceMount(mounts, destination = '/workspace') {
  const mount = mounts.find((item) => item.Type === 'bind' && item.Destination === destination)
  if (!mount?.Source) throw new Error('The CLI runner requires a bind-mounted repository at /workspace')
  return daemonWorkspacePath(mount.Source)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [container] = JSON.parse(execFileSync('docker', ['inspect', process.env.HOSTNAME], { encoding: 'utf8' }))
    process.env.OMNI_DOCKER_BIND_ROOT = workspaceMount(container.Mounts)
    const [command, ...args] = process.argv.slice(2)
    if (!command) throw new Error('The CLI runner requires a command')
    execFileSync(command, args, { stdio: 'inherit', env: process.env })
  } catch (error) {
    if (error.status === undefined) console.error(error.message)
    process.exit(error.status || 1)
  }
}
