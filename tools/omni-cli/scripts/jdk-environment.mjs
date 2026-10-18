import { spawnSync } from 'node:child_process'
import { delimiter, resolve } from 'node:path'

/** 以实际 Java 版本校验 JDK，兼容容器 /opt/java/openjdk 和宿主机自定义路径。 */
export function jdkEnvironment() {
  const javaHome = process.env.JAVA_HOME
  if (!javaHome) throw new Error('JAVA_HOME 必须指向 JDK 25；容器使用 /opt/java/openjdk')
  const java = resolve(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  const result = spawnSync(java, ['-version'], { encoding: 'utf8' })
  if (result.error) throw new Error(`JAVA_HOME 中的 Java 不可执行：${result.error.message}`)
  const output = `${result.stdout || ''}\n${result.stderr || ''}`
  if (result.status !== 0 || !/version "25(?:\.|"|[-+])/.test(output)) {
    throw new Error(`黄金验证需要 JDK 25：${output.trim()}`)
  }
  return { JAVA_HOME: javaHome, PATH: `${resolve(javaHome, 'bin')}${delimiter}${process.env.PATH || ''}` }
}
