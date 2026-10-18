import { randomBytes } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const defaultRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const secret = (bytes = 24) => randomBytes(bytes).toString('hex')

/** 仅初始化全新环境；旧数据卷必须保留原凭据并使用正式升级路径。 */
export function initializeEnvironment(root = defaultRoot) {
  const values = {
    OMNI_INTERNAL_API_TOKEN: secret(), GATEWAY_ASSERTION_SECRET: secret(32),
    MYSQL_ROOT_PASSWORD: secret(), OMNI_DB_PASSWORD: secret(), REDIS_PASSWORD: secret(),
    XXL_JOB_ACCESS_TOKEN: secret(), XXL_JOB_ADMIN_PASSWORD: secret(9),
    NACOS_AUTH_TOKEN: randomBytes(32).toString('base64'),
    NACOS_AUTH_IDENTITY_KEY: secret(16), NACOS_AUTH_IDENTITY_VALUE: secret(), NACOS_PASSWORD: secret(),
    OAUTH2_STATE_SECRET: secret(32), JWK_ENCRYPT_KEY: secret(16),
  }
  let content = readFileSync(resolve(root, '.env.example'), 'utf8')
  for (const [name, value] of Object.entries(values)) {
    const pattern = new RegExp(`^${name}=.*$`, 'm')
    if (!pattern.test(content)) throw new Error(`Environment template is missing ${name}`)
    content = content.replace(pattern, `${name}=${value}`)
  }
  // 原子新建保护已有凭据，输出不包含生成的值。
  writeFileSync(resolve(root, '.env'), content, { flag: 'wx', mode: 0o600 })
  console.log('Created private .env for a fresh local environment; existing files are never overwritten.')
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2)
  if (args.length && !(args.length === 2 && args[0] === '--root')) throw new Error('Usage: init-env.mjs [--root directory]')
  initializeEnvironment(args[1] ? resolve(args[1]) : defaultRoot)
}
