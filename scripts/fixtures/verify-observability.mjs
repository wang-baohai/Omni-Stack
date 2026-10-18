/** 在本机 Docker 网络内核对观测组件和业务采集，不修改业务数据。 */
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'

assert.ok(existsSync('/.dockerenv'), 'Verification must run inside local Docker')
// 预设裁剪后的应用集合由调用者显式声明，不把完整仓库的服务清单固化到工具中。
const expectedServices = process.env.E2E_OBSERVABILITY_SERVICES?.split(',').filter(Boolean).sort()
assert.ok(expectedServices?.length && expectedServices.every((service) => /^omni-[a-z][a-z0-9-]*$/.test(service)), 'Declare the selected application services')
assert.equal(new Set(expectedServices).size, expectedServices.length, 'Duplicate application service')
const probes = [
  ['collector', 'http://otel-collector:13133/'],
  ['prometheus', 'http://prometheus:9090/-/ready'],
  ['pushgateway', 'http://pushgateway:9091/-/ready'],
  ['alertmanager', 'http://alertmanager:9093/-/ready'],
  ['tempo', 'http://tempo:3200/ready'],
  ['loki', 'http://loki:3100/ready'],
  ['alloy', 'http://alloy:12345/-/ready'],
  ['grafana', 'http://grafana:3000/api/health'],
]
const startedAt = new Date().toISOString()
const deadline = Date.now() + 120000
let readiness
let targets
let metrics
do {
  readiness = await Promise.all(probes.map(async ([service, url]) => {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(5000) })
      return { service, http: response.status }
    } catch { return { service, http: null } }
  }))
  try {
    targets = await (await fetch('http://prometheus:9090/api/v1/targets', { signal: AbortSignal.timeout(5000) })).json()
    metrics = await (await fetch('http://prometheus:9090/api/v1/query?query=up%7Bjob%3D%22omni-platform%22%7D', { signal: AbortSignal.timeout(5000) })).json()
  } catch { targets = null; metrics = null }
  if (readiness.every((probe) => probe.http === 200)
    && metrics?.status === 'success' && metrics.data.result.length === expectedServices.length
    && JSON.stringify(metrics.data.result.map((item) => item.metric.instance.split(':')[0]).sort()) === JSON.stringify(expectedServices)
    && metrics.data.result.every((item) => item.value[1] === '1')) break
  assert.ok(Date.now() < deadline, 'Readiness/business metrics did not recover within120s')
  await new Promise((done) => setTimeout(done, 3000))
} while (true)

const actualTargets = targets.data.activeTargets.map((target) => ({
  job: target.labels.job, instance: target.labels.instance, health: target.health,
}))
const traceId = process.argv.find((argument) => argument.startsWith('--trace-id='))?.split('=')[1]
let trace
if (traceId) {
  assert.match(traceId, /^[a-f0-9]{32}$/)
  const response = await fetch(`http://tempo:3200/api/traces/${traceId}`, { signal: AbortSignal.timeout(15000) })
  assert.equal(response.status, 200, 'Previously recorded trace is unavailable')
  const data = await response.json()
  const batches = data.batches || data.resourceSpans || []
  const services = [...new Set(batches.flatMap((batch) => (batch.resource?.attributes || [])
    .filter((attribute) => attribute.key === 'service.name').map((attribute) => attribute.value.stringValue)))].sort()
  const spanCount = batches.reduce((sum, batch) => sum + (batch.scopeSpans || batch.instrumentationLibrarySpans || [])
    .reduce((count, scope) => count + (scope.spans || []).length, 0), 0)
  assert.ok(services.includes('omni-gateway') && services.includes('omni-base') && spanCount > 0)
  trace = { trace_id: traceId, http: response.status, services, span_count: spanCount, historical_read_only: true }
}
console.log(JSON.stringify({
  status: 'PASS', runtime: 'local Docker', started_at: startedAt, finished_at: new Date().toISOString(),
  readiness, expected_services: expectedServices, business_targets_up: expectedServices.length, targets: actualTargets, historical_trace: trace,
  limits: ['Only listed components and business targets are asserted; omitted host exporters may remainDOWN', 'No external notification delivery or human login is asserted'],
}, null, 2))
