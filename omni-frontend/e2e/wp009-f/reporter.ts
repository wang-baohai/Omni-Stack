import type { Reporter, TestCase, TestResult, FullResult, TestStep, FullConfig, Suite } from '@playwright/test/reporter'
import { ARTIFACTS, evidence, RUN } from './target'

/** 不输出认证报文、请求头、操作日志或自动 DOM 快照。 */
export default class RedactedReporter implements Reporter {
  private cases: object[] = []
  private expectedCases = 0
  onBegin(_config: FullConfig, suite: Suite) {
    this.expectedCases = suite.allTests().length
    process.stdout.write(`证据目录：${ARTIFACTS}\n`)
  }
  onTestBegin(test: TestCase) { process.stdout.write(`开始：${test.title}\n`) }
  onStepBegin(_test: TestCase, _result: TestResult, step: TestStep) {
    if (step.category === 'test.step') process.stdout.write(`步骤：${step.title}\n`)
  }
  onTestEnd(test: TestCase, result: TestResult) {
    const safeError = result.errors.map(error => {
      const first = (error.message || '未提供错误信息').split('\n')[0]
      return /token|cookie|captchaCode|password|Bearer|eyJ/i.test(first) ? '认证相关失败，原始内容已隐藏' : first
    })
    this.cases.push({ title: test.title, location: test.location, status: result.status, durationMs: result.duration, errors: safeError, errorLocations: result.errors.map(error => error.stack?.match(/[^\s]+\.spec\.ts:\d+:\d+/g) || []), annotations: test.annotations })
    process.stdout.write(`${result.status}: ${test.title}\n${safeError.join('\n')}\n`)
    evidence('test-results.json', { run: RUN, expectedCases: this.expectedCases, cases: this.cases })
  }
  onEnd(result: FullResult) {
    evidence('test-results.json', { run: RUN, expectedCases: this.expectedCases, status: result.status, cases: this.cases, finishedAt: new Date().toISOString(), fullWp009Acceptance: false })
  }
}
