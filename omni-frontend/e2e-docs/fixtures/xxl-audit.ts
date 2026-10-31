/** 隔离教学项目的 XXL-JOB 只读核对，会话只保留在容器进程内存。 */
import { existsSync, readFileSync } from 'node:fs'
import { expect } from '@playwright/test'

export interface XxlExecution {
  id: number; jobId: number; triggerCode: number; handleCode: number
  triggerTime: string; handleTime: string; executorParam: string
  origin: 'CRON' | 'MANUAL' | 'UNKNOWN'
}
interface RemoteLog extends Omit<XxlExecution, 'origin'> { triggerMsg: string }
interface RemoteJob { id: number; executorHandler: string; executorParam: string; triggerStatus: number; scheduleConf: string }
interface PageData<T> { data: T[]; offset: number; pagesize: number; total: number }

/** G8 适配：项目名取本次已核实的 omni-stack，仍要求环境变量严格相等，错误项目必须拒绝。 */
export const VERIFIED_E2E_PROJECT = 'omni-stack'

/** 从仓库外私有配置读取隔离凭据，返回不持久化 Cookie 的只读客户端。 */
export async function createXxlAudit(privateEnvFile: string) {
  if (!existsSync('/.dockerenv') || process.env.E2E_SCHEDULING_UI_PROJECT !== VERIFIED_E2E_PROJECT) throw new Error('仅允许本次隔离 Docker 项目')
  const values: Record<string, string> = {}
  for (const line of readFileSync(privateEnvFile, 'utf8').split(/\r?\n/)) {
    const entry = /^(XXL_JOB_ADMIN_USERNAME|XXL_JOB_ADMIN_PASSWORD)=(.*)$/.exec(line)
    if (entry) values[entry[1]!] = entry[2]!
  }
  if (!values.XXL_JOB_ADMIN_USERNAME || !values.XXL_JOB_ADMIN_PASSWORD) throw new Error('缺少隔离调度中心私有配置')
  const base = 'http://xxl-job-admin:8080/xxl-job-admin'
  const loginCookie = async () => {
    const login = await fetch(`${base}/auth/doLogin`, { method: 'POST', body: new URLSearchParams({ userName: values.XXL_JOB_ADMIN_USERNAME!, password: values.XXL_JOB_ADMIN_PASSWORD! }), signal: AbortSignal.timeout(15_000) })
      .catch(() => { throw new Error('隔离 XXL-JOB 登录网络失败；不输出请求配置') })
    const value = login.headers.getSetCookie().map(entry => entry.split(';')[0]!).find(entry => entry.startsWith('xxl_job_login_token='))
    if (!value) throw new Error('隔离 XXL-JOB 登录未返回会话凭据')
    return value
  }
  let cookie = await loginCookie()
  const post = async <T>(path: string, form: Record<string, string>) => {
    for (let attempt = 0; attempt < 2; attempt++) {
      const response = await fetch(base + path, { method: 'POST', headers: { Cookie: cookie }, body: new URLSearchParams(form), signal: AbortSignal.timeout(15_000) })
        .catch(() => { throw new Error(`XXL-JOB 只读核对网络失败 ${path}`) })
      const body = await response.json() as { code: number; msg?: string; data: T }
      if (attempt === 0 && body.code !== 200 && /not login/i.test(body.msg || '')) { cookie = await loginCookie(); continue }
      expect(response.status, path).toBe(200); expect(body.code, path).toBe(200)
      return body.data
    }
    throw new Error('XXL-JOB 只读核对会话未能恢复')
  }
  const groups = await post<PageData<{ id: number; appname: string }>>('/jobgroup/pageList', { offset: '0', pagesize: '100', appname: 'omni-base' })
  const group = groups.data.find(value => value.appname === 'omni-base')
  if (!group) throw new Error('隔离 Base 执行器组不存在')
  const pageAll = async <T extends { id: number }>(path: string, form: Record<string, string>) => {
    const result: T[] = [], seen = new Set<number>()
    for (let offset = 0; offset < 1000; offset += 100) {
      const page = await post<PageData<T>>(path, { ...form, jobGroup: String(group.id), offset: String(offset), pagesize: '100' })
      if (!Array.isArray(page.data) || !Number.isInteger(page.total)) throw new Error('XXL-JOB 分页契约不符，不能宣称远端不存在')
      for (const value of page.data) {
        if (seen.has(value.id)) throw new Error('XXL-JOB 重复分页，不能宣称远端不存在')
        seen.add(value.id); result.push(value)
      }
      if (result.length >= page.total) return result
      if (!page.data.length) throw new Error('XXL-JOB 分页提前结束，不能宣称远端不存在')
    }
    throw new Error('XXL-JOB 核对超过分页预算')
  }
  return {
    logs: async (jobId: number, localJobId: number): Promise<XxlExecution[]> => {
      const rows = await pageAll<RemoteLog>('/joblog/pageList', { jobId: String(jobId), logStatus: '-1', filterTime: '' })
      return rows.map(row => {
        expect(row.jobId).toBe(jobId)
        const message = JSON.parse(row.executorParam) as { jobId: number; tenantId: number; jobName: string }
        expect(message.jobId).toBe(localJobId); expect(message.tenantId).toBe(1); expect(message.jobName).toMatch(/^WIKI-20261009A-SCHUI-/)
        const origin = /Cron触发|Cron|CRON/.test(row.triggerMsg) ? 'CRON' : /手动触发|Manual|MANUAL/.test(row.triggerMsg) ? 'MANUAL' : 'UNKNOWN'
        return { id: row.id, jobId: row.jobId, triggerCode: row.triggerCode, handleCode: row.handleCode, triggerTime: row.triggerTime, handleTime: row.handleTime, executorParam: row.executorParam, origin }
      })
    },
    absent: async (jobId: number) => !(await pageAll<RemoteJob>('/jobinfo/pageList', { triggerStatus: '-1', executorHandler: 'userJobExecuteHandler', jobDesc: 'WIKI-20261009A-SCHUI-', author: '' })).some(row => row.id === jobId),
  }
}
