import { spawnSync } from 'node:child_process'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

export const ROOT = resolve(import.meta.dirname, '../../..')
export const EXECUTION = 'WP009-EXEC-20260920T081611Z-a7f3'
export const PROJECT = 'omni-wp001-duzr'
export const F_UUID = '4a955545-b585-11f1-b9fc-42f7a703eb82'
export const ORIGIN = 'http://localhost:43000'
export const RUN = process.env.WP009_F_RUN || `WP009-PW-${new Date().toISOString().replace(/[-:.]/g, '')}-${randomBytes(3).toString('hex')}`
export const ARTIFACTS = resolve(ROOT, '.qoder/wp009-evidence', EXECUTION, RUN)
export const services = ['mysql', 'redis', 'nacos', 'auth', 'gateway', 'base', 'frontend'] as const
export type Service = typeof services[number]

export function ensure(ok: unknown, message: string): asserts ok {
  if (!ok) throw new Error(message)
}

export function command(program: string, args: string[], input?: string | Buffer): string {
  const result = spawnSync(program, args, { input, encoding: 'utf8', windowsHide: true, timeout: 45_000, maxBuffer: 4 * 1024 * 1024 })
  // 原始命令、响应体和 stderr 可能含凭据，一律不进入失败报告。
  ensure(result.status === 0, `${program} 调用失败（退出码 ${result.status ?? 'unavailable'}，原始输出已隐藏）`)
  return result.stdout.trim()
}

export function container(service: Service): string {
  ensure(services.includes(service), '拒绝非 WP009 F 服务')
  return `${PROJECT}-wp009f-${service}-1`
}

interface Runtime {
  id: string
  image: string
  labels: Record<string, string>
  networks: Record<string, unknown>
  mounts: { Type: string; Name?: string; Source: string; Destination: string }[]
  running: boolean
}

export function validateRuntime(service: Service, runtime: Runtime) {
  ensure(runtime.labels['com.docker.compose.project'] === PROJECT, 'F project 不匹配')
  ensure(runtime.labels['com.docker.compose.service'] === `wp009f-${service}`, 'F service 不匹配')
  ensure(runtime.labels['com.omni.execution'] === EXECUTION, 'F execution 不匹配')
  ensure(Object.keys(runtime.networks).join() === 'omni-wp009f', '拒绝混入 M 或旧 F 网络')
  ensure(runtime.running, `F ${service} 未运行`)
  if (service === 'mysql') {
    ensure(runtime.mounts.some(m => m.Destination === '/var/lib/mysql' && m.Name === `${PROJECT}_wp009f-mysql-data`), 'F MySQL 卷不匹配')
  }
  ensure(!runtime.mounts.some(m => /(?:^|[/\\])\.env\.wp001$|omni-wp001-duzr[-_]mysql-data|f-mysql-data/.test(m.Source) && !m.Source.includes('wp009f-mysql-data')), '拒绝共享主库或旧 F 挂载')
}

export function inspect(service: Service): Runtime {
  const template = '{"id":{{json .Id}},"image":{{json .Image}},"labels":{{json .Config.Labels}},"networks":{{json .NetworkSettings.Networks}},"mounts":{{json .Mounts}},"running":{{json .State.Running}}}'
  const runtime = JSON.parse(command('docker', ['inspect', container(service), '--format', template])) as Runtime
  validateRuntime(service, runtime)
  return runtime
}

function mysql(input: string): string {
  inspect('mysql')
  return command('docker', ['exec', '-i', container('mysql'), 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -uroot omni_auth'], input)
}

export function checkDatabase(expected = F_UUID) {
  ensure(mysql('SELECT @@server_uuid, DATABASE();\n') === `${expected}\tomni_auth`, 'F UUID/schema 不匹配，禁止任何后续写入')
}

export function sql(query: string): string {
  // 同一连接内先判断 UUID/schema，错误目标执行必失败的语句，绝不先写后查。
  inspect('mysql')
  const encoded = Buffer.from(query).toString('hex')
  return mysql(`SET @q=IF(@@server_uuid='${F_UUID}' AND DATABASE()='omni_auth',CONVERT(0x${encoded} USING utf8mb4),'SELECT * FROM information_schema.wp009_wrong_target_refused'); PREPARE wp009 FROM @q; EXECUTE wp009; DEALLOCATE PREPARE wp009;\n`)
}

export function redis(...args: string[]): string {
  inspect('redis')
  ensure(['SET', 'GET', 'TYPE', 'TTL', 'EXISTS', 'EXPIRE', 'HGET', 'HGETALL'].includes(args[0]), 'Redis 命令不在测试白名单')
  ensure(/^(captcha:|login:fail:1:WP009-|xss:(enabled|rules):1$|online:|token:blacklist:[a-zA-Z0-9-]+$)/.test(args[1]), 'Redis key 不属于测试边界')
  const resp = `*${args.length}\r\n${args.map(v => `$${Buffer.byteLength(v)}\r\n${v}\r\n`).join('')}`
  // 参数通过标准输入传递，redis-cli --pipe 不回显值；读取命令另用参数化 exec。
  if (['SET', 'EXPIRE'].includes(args[0])) {
    return command('docker', ['exec', '-i', container('redis'), 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --pipe'], resp)
  }
  return command('docker', ['exec', container('redis'), 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw "$@"', 'redis-cli', ...args])
}

export function controlledCaptcha() {
  checkDatabase()
  const captchaKey = randomUUID()
  const captchaCode = randomBytes(2).toString('hex')
  const javaHome = process.env.JAVA_HOME
  ensure(javaHome, '需要 JDK25 的 JAVA_HOME 来运行测试专用图片生成器')
  const image = command(resolve(javaHome, 'bin/java.exe'), ['-Djava.awt.headless=true', resolve(ROOT, 'scripts/fixtures/captcha/CaptchaFixture.java')], `${captchaCode}\n`)
  ensure(Buffer.from(image, 'base64').subarray(0, 8).toString('hex') === '89504e470d0a1a0a', '夹具图片必须为有效 PNG')
  redis('SET', `captcha:${captchaKey}`, captchaCode, 'EX', '300', 'NX')
  ensure(Number(redis('TTL', `captcha:${captchaKey}`)) > 0, '验证码 TTL 未生效')
  return { captchaKey, captchaCode, captchaImage: `data:image/png;base64,${image}` }
}

export function evidence(name: string, value: unknown) {
  ensure(/^[a-z0-9-]+\.json$/.test(name), '非法证据文件名')
  mkdirSync(ARTIFACTS, { recursive: true })
  writeFileSync(resolve(ARTIFACTS, name), JSON.stringify(value, null, 2) + '\n')
}

export function sha(path: string) {
  return createHash('sha256').update(readFileSync(path)).digest('hex')
}

export function preflight() {
  ensure(/^WP009-PW-[A-Za-z0-9-]+$/.test(RUN), '非法 run ID')
  ensure(!process.env.E2E_BASE_URL || process.env.E2E_BASE_URL === ORIGIN, 'WP009 F 不接受其他前端地址')
  const runtime = Object.fromEntries(services.map(s => [s, inspect(s)]))
  checkDatabase()
  ensure(sql('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\'omni_auth\' AND table_name=\'sys_auth_login_policy\'') === '1', 'F 缺少 WP009 migration')
  // 不回显配置：仅核对非秘密依赖目标，防止网络隔离却通过宿主机访问 M。
  const authTargets = command('docker', ['exec', container('auth'), 'sh', '-c', 'printf "%s\\n" "$SPRING_DATASOURCE_URL" "$SPRING_DATA_REDIS_HOST" "$SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR"'])
  ensure(authTargets.includes('jdbc:mysql://wp009f-mysql:3306/omni_auth?') && authTargets.includes('\nwp009f-redis\nwp009f-nacos:8848'), 'Auth 依赖目标不属于 F')
  evidence('preflight.json', { run: RUN, at: new Date().toISOString(), uuid: F_UUID, schema: 'omni_auth', status: 'PASS', runtime })
  return runtime
}
