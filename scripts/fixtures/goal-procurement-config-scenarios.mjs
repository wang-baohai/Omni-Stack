import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02的配置功能；只写固定命名空间中新建对象，不改共享角色、原规则或流程。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
assert.equal(process.env.E2E_PROCUREMENT_IMAGE_ID, 'sha256:e043e8bebc7df4cb5c8b55bb88e9624c9e204f7c2897dda30c942e6921d2d340')
const attempt = process.argv[2]; assert.match(attempt ?? '', /^config-r[1-9][0-9]*$/)
const local = createLocalContext(), prefix = 'GOAL20261022_PROC'
const statePath = privateRoot + '/procurement-config-scenarios.json'
const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : { namespace: prefix, objects: {}, commands: {}, completed: {}, runs: [] }
assert.equal(state.namespace, prefix)
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const raw = privateRoot + '/procurement-' + attempt + '-wire.ndjson'
writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const clone = value => JSON.parse(JSON.stringify(value))
const run = { original_step: 'WP-27.S02', attempt, started_at: new Date().toISOString(), workers: 1, identity_source: 'test-fixture',
  procurement_image: process.env.E2E_PROCUREMENT_IMAGE_ID, script_sha256: sha(readFileSync(new URL(import.meta.url))),
  original_raw_log: raw, cases: [], requests: [], shared_roles_changed: false, existing_rules_changed: false,
  limits: ['仅MAT01/MAT02/ROUTE01/ROUTE02当前配置功能，不作为所有采购业务或文图验收', '普通EMPLOYEE共享授权差异另记；本人使用既有采购员/经理，不改变原共享角色'] }
state.runs.push({ attempt, at: run.started_at })
const persist = () => {
  writeFileSync(statePath, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(dir + '/result.json', JSON.stringify({ ...run, objects: state.objects }, null, 2) + '\n')
}
const tokens = {}
let currentCase
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(path.startsWith('/api/procurement/'), '只允许正式采购API')
  tokens[actor] ??= await local.login(actor)
  const row = { case: currentCase, actor, user_id: local.state.accounts[actor].id, method, path, body: body ?? null, expected, at: new Date().toISOString() }
  run.requests.push(row); persist()
  let response
  try { response = await fetch('http://omni-gateway:8080' + path, { method, headers: { Authorization: `Bearer ${tokens[actor]}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) }) }
  catch { row.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist(); throw new Error(method + ' ' + path + '结果未知，必须先读回，不自动重发') }
  const json = await response.json()
  appendFileSync(raw, JSON.stringify({ ...row, http_status: response.status, response: json }) + '\n')
  row.http_status = response.status; row.business_code = json.code ?? response.status; row.payload_sha256 = sha(JSON.stringify(json)); persist()
  assert.equal(row.business_code, expected, actor + ':' + method + ' ' + path)
  if (expected === 200) assert.equal(response.status, 200)
  return json.data
}
async function command(key, actor, path, method, body, expected = 200) {
  if (state.commands[key]?.complete) return clone(state.commands[key].result)
  assert.ok(!state.commands[key], '未知结果的旧命令' + key + '必须先核对，不重放')
  state.commands[key] = { actor, path, method, body: body ?? null, expected, attempt, at: new Date().toISOString() }; persist()
  const result = (await api(actor, path, method, body, expected)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); persist()
  return result
}
async function check(id, action) {
  currentCase = id
  if (state.completed[id]) { assert.equal(state.completed[id].procurement_image, run.procurement_image); run.cases.push({ id, result: 'REUSE_PREVIOUS_ACTUAL_PASS', evidence: state.completed[id] }); persist(); return }
  const start = run.requests.length
  try {
    const evidence = await action(), result = { id, result: 'PASS', evidence, attempt, at: new Date().toISOString(), procurement_image: run.procurement_image, request_indexes: [start, run.requests.length - 1] }
    state.completed[id] = clone(result); run.cases.push(result); persist(); console.log(JSON.stringify({ case: id, result: 'PASS', requests: run.requests.length - start }))
  } catch (error) { run.cases.push({ id, result: 'FAIL', error: error.message }); persist(); throw error }
}
const categories = async () => {
  const flatten = rows => rows.flatMap(row => [row, ...flatten(row.children ?? [])])
  return flatten(await api('procStaff','/api/procurement/material/category/list'))
}
const categoryBody = (row, changes = {}) => ({ version: row.version, parentId: row.parentId, categoryName: row.categoryName, sort: row.sort, status: row.status, ...changes })
const materialBody = (row, changes = {}) => ({ version: row.version, categoryId: row.categoryId, materialName: row.materialName, specification: row.specification, unit: row.unit, assetManaged: row.assetManaged, status: row.status, ...changes })
const ruleBody = (row, changes = {}) => ({ version: row.version, routeName: row.routeName, categoryCode: row.categoryCode, minAmount: row.minAmount, maxAmount: row.maxAmount, modelVersionId: row.modelVersionId, priority: row.priority, status: row.status, ...changes })
const rules = async () => (await api('procScopeManager','/api/procurement/approval-route/list?page=1&size=100')).records
const match = amount => api('procScopeManager','/api/procurement/approval-route/match-preview','POST',{ categoryCode: state.objects.leaf.categoryCode, totalAmount: amount })
const ruleStable = rows => rows.map(row => Object.fromEntries(['id','routeCode','routeName','categoryCode','minAmount','maxAmount','modelVersionId','priority','status','version'].map(key => [key,row[key]]))).sort((a,b) => a.id-b.id)
// 金额比较仍用十进制定点，不以二进制浮点选择预期匹配规则。
const amount4 = value => { const match=String(value).match(/^(\d+)(?:\.(\d{1,4}))?$/);assert.ok(match);return BigInt(match[1])*10000n+BigInt((match[2]??'').padEnd(4,'0')) }
try {
  await check('MAT-01-real-hierarchy-crud-and-version-guards', async () => {
    const before = await categories()
    for (const code of [prefix+'_ROOT',prefix+'_LEAF',prefix+'_TEMP']) assert.ok(!before.some(row => row.categoryCode === code), '自然键已有未登记对象，不接管')
    state.objects.root = await command('create-root','procStaff','/api/procurement/material/category','POST',{ parentId:0,categoryCode:prefix+'_ROOT',categoryName:'Goal 采购配置测试',sort:990,status:1 }); persist()
    state.objects.leaf = await command('create-leaf','procStaff','/api/procurement/material/category','POST',{ parentId:state.objects.root.id,categoryCode:prefix+'_LEAF',categoryName:'Goal 采购测试品类',sort:990,status:1 }); persist()
    const root=state.objects.root, leaf=state.objects.leaf
    await command('duplicate-category','procStaff','/api/procurement/material/category','POST',{ parentId:0,categoryCode:root.categoryCode,categoryName:'重复测试',sort:990,status:1 },409)
    await command('parent-delete-rejected','procStaff',`/api/procurement/material/category/${root.id}?version=${root.version}`,'DELETE',undefined,409)
    await command('parent-disable-rejected','procStaff',`/api/procurement/material/category/${root.id}`,'PUT',categoryBody(root,{status:0}),409)
    await command('self-parent-rejected','procStaff',`/api/procurement/material/category/${leaf.id}`,'PUT',categoryBody(leaf,{parentId:leaf.id}),400)
    await command('stale-category-edit','procStaff',`/api/procurement/material/category/${leaf.id}`,'PUT',categoryBody(leaf,{version:leaf.version+1}),409)
    state.objects.leaf=await command('rename-leaf','procStaff',`/api/procurement/material/category/${leaf.id}`,'PUT',categoryBody(leaf,{categoryName:'Goal 采购测试品类（已更新）',sort:991})); persist()
    assert.equal(state.objects.leaf.categoryCode,leaf.categoryCode)
    const temp=await command('create-temp-category','procStaff','/api/procurement/material/category','POST',{parentId:root.id,categoryCode:prefix+'_TEMP',categoryName:'Goal 待回收测试品类',sort:992,status:1})
    await command('delete-temp-category','procStaff',`/api/procurement/material/category/${temp.id}?version=${temp.version}`,'DELETE')
    const after=await categories(); assert.ok(!after.some(row=>row.id===temp.id)); assert.equal(after.find(row=>row.id===leaf.id).categoryName,state.objects.leaf.categoryName)
    return { root_id:root.id,leaf_id:leaf.id,temporary_deleted:temp.id,code_immutable:true }
  })
  await check('MAT-02-real-catalog-crud-enable-disable-and-shared-read', async () => {
    const leaf=state.objects.leaf, code=prefix+'_DISPOSABLE'
    assert.equal((await api('procStaff','/api/procurement/material/list?page=1&size=100&keyword='+code)).records.length,0)
    const body={categoryId:leaf.id,materialCode:code,materialName:'Goal 可回收物料样本',unit:'PCS',assetManaged:false,status:'ACTIVE',specification:'仅本Goal配置验证'}
    let temp=await command('create-disposable-material','procStaff','/api/procurement/material','POST',body)
    await command('duplicate-material','procStaff','/api/procurement/material','POST',body,409)
    await command('invalid-asset-unit','procStaff','/api/procurement/material','POST',{...body,materialCode:prefix+'_INVALID_UNIT',assetManaged:true,unit:'KG'},400)
    await command('nonleaf-material-rejected','procStaff','/api/procurement/material','POST',{...body,materialCode:prefix+'_INVALID_PARENT',categoryId:state.objects.root.id},400)
    const first=clone(temp)
    temp=await command('disable-material','procStaff',`/api/procurement/material/${temp.id}`,'PUT',materialBody(temp,{status:'INACTIVE'})); assert.equal(temp.status,'INACTIVE')
    await command('stale-material-edit','procStaff',`/api/procurement/material/${temp.id}`,'PUT',materialBody(first),409)
    temp=await command('enable-material','procStaff',`/api/procurement/material/${temp.id}`,'PUT',materialBody(temp,{status:'ACTIVE',materialName:'Goal 可回收物料（已更新）'})); assert.equal(temp.materialCode,code)
    assert.equal((await api('procPeer',`/api/procurement/material/${temp.id}`)).id,temp.id)
    await command('stale-material-delete','procStaff',`/api/procurement/material/${temp.id}?version=${temp.version+1}`,'DELETE',undefined,409)
    await command('delete-disposable-material','procStaff',`/api/procurement/material/${temp.id}?version=${temp.version}`,'DELETE')
    await api('procStaff',`/api/procurement/material/${temp.id}`,'GET',undefined,404)
    for(const [label,assetManaged] of [['asset',true],['supply',false]]) {
      const materialCode=prefix+'_'+label.toUpperCase()
      assert.equal((await api('procStaff','/api/procurement/material/list?page=1&size=100&keyword='+materialCode)).records.length,0)
      state.objects[label]=await command('create-'+label+'-sample','procStaff','/api/procurement/material','POST',{...body,materialCode,materialName:assetManaged?'Goal 资产化物料样本':'Goal 非资产物料样本',assetManaged}); persist()
    }
    await command('leaf-delete-with-material-rejected','procStaff',`/api/procurement/material/category/${leaf.id}?version=${leaf.version}`,'DELETE',undefined,409)
    await command('leaf-disable-with-active-material-rejected','procStaff',`/api/procurement/material/category/${leaf.id}`,'PUT',categoryBody(leaf,{status:0}),409)
    const filtered=await api('procPeer',`/api/procurement/material/list?page=1&size=100&categoryId=${leaf.id}&assetManaged=true&status=ACTIVE`)
    assert.equal(filtered.records.length,1); assert.equal(filtered.records[0].id,state.objects.asset.id)
    return { temporary_deleted:temp.id,asset_material:state.objects.asset.id,nonasset_material:state.objects.supply.id,tenant_shared_peer_read:true }
  })
  await check('ROUTE-02-real-rule-crud-overlap-and-stale-guards', async () => {
    const before=await rules(); state.initial_other_rules=ruleStable(before); persist()
    assert.ok(!before.some(row=>row.categoryCode===state.objects.leaf.categoryCode))
    const choices=await api('procScopeManager','/api/procurement/approval-route/workflow-options')
    assert.ok(choices.some(row=>row.modelVersionId===2&&row.category==='purchase'))
    const body={routeName:'Goal 采购专用品类规则 A',categoryCode:state.objects.leaf.categoryCode,minAmount:'0.0000',maxAmount:'100.0000',modelVersionId:2,status:'ACTIVE'}
    let first=await command('create-rule-a','procScopeManager','/api/procurement/approval-route','POST',body)
    assert.match(first.routeCode,/^APR-[A-Z0-9]+$/)
    await command('overlapping-rule-rejected','procScopeManager','/api/procurement/approval-route','POST',{...body,routeName:'Goal 重叠规则应被拒绝',minAmount:'50.0000',maxAmount:'150.0000'},409)
    await command('invalid-rule-range-rejected','procScopeManager','/api/procurement/approval-route','POST',{...body,routeName:'Goal 无效区间',minAmount:'1.0000',maxAmount:'0.0000'},400)
    const initial=clone(first)
    first=await command('disable-rule-a','procScopeManager',`/api/procurement/approval-route/${first.id}`,'PUT',ruleBody(first,{status:'INACTIVE'})); assert.equal(first.status,'INACTIVE')
    await command('stale-rule-edit','procScopeManager',`/api/procurement/approval-route/${first.id}`,'PUT',ruleBody(initial),409)
    first=await command('enable-rule-a','procScopeManager',`/api/procurement/approval-route/${first.id}`,'PUT',ruleBody(first,{status:'ACTIVE',routeName:'Goal 采购专用品类规则 A（已更新）'}))
    assert.equal(first.routeCode,initial.routeCode); state.objects.ruleA=first
    state.objects.ruleB=await command('create-adjacent-rule-b','procScopeManager','/api/procurement/approval-route','POST',{...body,routeName:'Goal 采购专用品类规则 B',minAmount:'100.0000',maxAmount:null}); persist()
    return { rule_a:first.id,rule_b:state.objects.ruleB.id,model_version:2,server_generated_immutable_code:first.routeCode }
  })
  await check('ROUTE-01-real-half-open-match-coverage-impact-and-delete', async () => {
    const a=state.objects.ruleA,b=state.objects.ruleB
    for(const [amount,id] of [['0.0000',a.id],['99.9999',a.id],['100.0000',b.id],['101.0000',b.id]]) { const preview=await match(amount); assert.equal(preview.outcome,'MATCHED'); assert.equal(preview.routeId,id); assert.equal(preview.defaultRule,false) }
    const coverage=await api('procScopeManager','/api/procurement/approval-route/coverage')
    const category=coverage.categories.find(row=>row.categoryCode===state.objects.leaf.categoryCode); assert.ok(category); assert.equal(category.complete,true)
    const impact=await api('procScopeManager','/api/procurement/approval-route/impact?routeId='+a.id); assert.equal(impact.routeId,a.id); assert.ok(impact.coverage)
    await command('delete-adjacent-rule-b','procScopeManager',`/api/procurement/approval-route/${b.id}?version=${b.version}`,'DELETE')
    const atBoundary=await match('100.0000')
    const defaultAt100=state.initial_other_rules.filter(row=>row.categoryCode==='*'&&row.status==='ACTIVE'&&amount4(row.minAmount)<=1000000n&&(row.maxAmount===null||amount4(row.maxAmount)>1000000n))
    if(defaultAt100.length===0) assert.equal(atBoundary.outcome,'NO_MATCH')
    else {assert.equal(defaultAt100.length,1);assert.equal(atBoundary.outcome,'MATCHED');assert.equal(atBoundary.defaultRule,true);assert.equal(atBoundary.routeId,defaultAt100[0].id)}
    state.objects.ruleA=await command('extend-only-owned-rule-a','procScopeManager',`/api/procurement/approval-route/${a.id}`,'PUT',ruleBody(a,{maxAmount:null})); persist()
    const restored=await match('100.0000'); assert.equal(restored.routeId,a.id); assert.equal(restored.outcome,'MATCHED')
    assert.ok(!(await rules()).some(row=>row.id===b.id))
    return { exact_half_open_boundary:'100.0000',impact_route:a.id,temporary_rule_deleted:b.id,final_owned_rule:state.objects.ruleA.id,final_range:'[0,+infinity)',fallback_after_delete:atBoundary.outcome }
  })
  await check('CONFIG-real-role-denials-and-other-rules-preserved', async () => {
    await api('procStaff','/api/procurement/approval-route/list?page=1&size=100','GET',undefined,403)
    await command('staff-rule-create-denied','procStaff','/api/procurement/approval-route','POST',{routeName:'Goal 未授权创建应被拒绝',categoryCode:state.objects.leaf.categoryCode,minAmount:'0.0000',maxAmount:null,modelVersionId:2,status:'INACTIVE'},403)
    await api('srmPortal','/api/procurement/material/category/list','GET',undefined,403)
    const other=(await rules()).filter(row=>row.id!==state.objects.ruleA.id)
    assert.deepEqual(ruleStable(other),state.initial_other_rules,'其他规则稳定字段必须逐项未变')
    const categoriesNow=await categories(); assert.equal(categoriesNow.find(row=>row.id===state.objects.root.id).status,1)
    return { procurement_staff_rule_list:403,procurement_staff_rule_create:403,portal_material:403,other_rules_preserved:other.length,stable_other_rules_sha256:sha(JSON.stringify(ruleStable(other))) }
  })
  run.result='PASS_FIVE_REAL_CONFIG_FUNCTIONAL_CASES_NOT_FULL_PROCUREMENT'; run.finished_at=new Date().toISOString(); run.raw_log_sha256=sha(readFileSync(raw)); persist()
  console.log(JSON.stringify({result:run.result,cases:run.cases.length,requests:run.requests.length}))
} catch(error) { run.result='FAIL_CONFIG_PRESERVE_OBJECTS_AND_COMMAND_GUARDS'; run.error=error.message; persist(); throw error }
finally {writeFileSync(dir+'/source-used.mjs',readFileSync(new URL(import.meta.url)))}
