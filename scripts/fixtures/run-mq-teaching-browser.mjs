/**
 * 容器内的教学浏览器入口；仅从仓库外私有 JSON 读取短期测试令牌。
 * 不签发身份、不打印令牌、不启动应用，也不自动重放已登记的阶段。
 */
import { existsSync, readFileSync } from 'node:fs'
import { isAbsolute, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

if (!existsSync('/.dockerenv')) throw new Error('仅允许本机 Docker 容器内执行')
const allowed = ['pending', 'retry', 'actions', 'terminal', 'fault', 'operlog']
if (!allowed.includes(process.env.E2E_MQ_PHASE)) throw new Error('缺少有效的 E2E_MQ_PHASE')
if (process.env.E2E_RUNTIME_MODE !== 'external' || process.env.E2E_BASE_URL !== 'http://omni-frontend:3000') {
  throw new Error('必须连接独立 Docker 网络中的正式前端')
}
const tokenFile = process.env.E2E_MQ_TOKEN_FILE
if (!tokenFile || !isAbsolute(tokenFile) || resolve(tokenFile).startsWith('/workspace/')) {
  throw new Error('E2E_MQ_TOKEN_FILE 必须指向仓库外私有 JSON')
}
const tokens = JSON.parse(readFileSync(tokenFile, 'utf8').replace(/^\uFEFF/, ''))
if (!tokens.adminToken || !tokens.outsiderToken) throw new Error('需要 adminToken 和 outsiderToken，值不得写入日志')
const registry = process.env.E2E_REGISTRY_PATH
if (!registry || !isAbsolute(registry) || resolve(registry).startsWith('/workspace/') || existsSync(registry)) {
  throw new Error('阶段登记必须是仓库外新路径；先核对旧结果，禁止覆盖')
}
const extraArgs = process.argv.slice(2)
if (extraArgs.some(arg => arg !== '--list')) throw new Error('仅允许可选的 --list；禁止通过入口改变用例或跳过断言')
const result = spawnSync(process.execPath, [
  'node_modules/@playwright/test/cli.js', 'test', '--config=playwright.docs.config.ts',
  'mq-teaching.flows.spec.ts', ...extraArgs,
], {
  cwd: '/workspace/omni-frontend', stdio: 'inherit',
  env: { ...process.env, E2E_ADMIN_TOKEN: tokens.adminToken, E2E_MQ_OUTSIDER_TOKEN: tokens.outsiderToken },
})
if (result.error) throw new Error('无法启动容器内 Playwright；检查依赖卷')
process.exit(result.status ?? 1)
