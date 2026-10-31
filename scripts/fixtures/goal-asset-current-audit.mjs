import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs'
import {createLocalContext,privateRoot} from './goal-local-identities.mjs'

// 原WP-28.S02的阶段后只读核对；不补写业务状态或自动重放命令。
assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(),'/validation/source')
const attempt=process.argv[2]
assert.match(attempt??'',/^asset-current-audit-r[1-9][0-9]*$/)
const out='/validation/evidence/codex-2026-10-22/wp28-s02/'+attempt
assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true})
const sha=value=>crypto.createHash('sha256').update(value).digest('hex')
const source=fs.readFileSync(new URL(import.meta.url));fs.writeFileSync(out+'/source-used.mjs',source)
const state=JSON.parse(fs.readFileSync(privateRoot+'/asset-scenarios.json')),local=createLocalContext(),tokens={}
assert.equal(state.namespace,'GOAL20261028ASSET')
const result={original_step:'WP-28.S02',started_at:new Date().toISOString(),fixture_sha256:sha(source),requests:[],checks:[],business_write_performed:false,config_write_performed:false,identity_source:'test-fixture',global_release_claimed:false,HUMAN_REVIEW:0}
const save=()=>fs.writeFileSync(out+'/result.json',JSON.stringify(result,null,2)+'\n')
const raw=privateRoot+'/'+attempt+'-wire.ndjson';fs.writeFileSync(raw,'',{flag:'wx',mode:0o600})
async function get(actor,path){
 assert(/^\/api\/(asset|workflow|auth)\//.test(path))
 tokens[actor]??=actor==='seedAdmin'?local.bootstrap():await local.login(actor)
 const response=await fetch('http://omni-gateway:8080'+path,{headers:{Authorization:'Bearer '+tokens[actor]},signal:AbortSignal.timeout(30000)})
 const json=await response.json(),record={actor,method:'GET',path,http_status:response.status,business_code:json.code??response.status,response_sha256:sha(JSON.stringify(json))}
 result.requests.push(record);fs.appendFileSync(raw,JSON.stringify({...record,response:json})+'\n');save()
 assert.equal(response.status,200);assert.equal(json.code,200)
 return json.data
}
function check(name,evidence){result.checks.push({name,result:'PASS',evidence});save()}
try{
 for(const mode of ['transfer-cancel-decide','disposal-cancel-decide','recovery-restore','transfer-recovery-finish','disposal-recovery-finish'])assert.equal(state.completed[mode],true,mode)
 const operator=await get('seedAdmin','/api/auth/user/232')
 assert.equal(operator.username,'goal_20261028_assetoperator');assert.equal(operator.primaryUnitId,606);assert.equal(operator.status,1);assert.equal(operator.tenantId,1)
 assert.deepEqual((await get('seedAdmin','/api/auth/user/232/roles')).map(Number),[40])
 await local.login('assetOperator');assert.deepEqual(local.state.accounts.assetOperator.actual_roles,['ASSET_ADMIN'])
 check('owned-232-config-and-fresh-roles-restored',{user_id:232,primary_unit:606,role_ids:[40]})
 const life=await get('assetManager','/api/asset/asset/'+state.objects.manualLife.id)
 assert.equal(life.status,'IN_USE');assert.equal(life.version,22);assert.equal(life.currentUserId,222);assert.equal(life.currentUnitId,606)
 assert.equal(life.locationCode,'ROOM-B606');assert.equal(life.activeOperationType,null);assert.equal(life.activeOperationId,null)
 check('life-restored-after-both-public-cancellations-and-both-original-retries',life)
 for(const [key,status] of [['manualDiscard','DISPOSED'],['manualScrap','SCRAPPED']]){
  const card=await get('assetManager','/api/asset/asset/'+state.objects[key].id)
  assert.equal(card.status,status);assert.equal(card.version,2);assert.equal(card.activeOperationType,null);assert.equal(card.activeOperationId,null)
  assert.equal(card.currentUserId,null);assert.equal(card.currentUnitId,null)
  check('distinct-irreversible-terminal-'+key,card)
 }
 const ops=[['transfer','transferMain','COMPLETED'],['transfer','transferReject','REJECTED'],['transfer','transferCancel','CANCELLED'],['transfer','transferRecovery','REJECTED'],['disposal','disposalReject','REJECTED'],['disposal','disposalDiscard','COMPLETED'],['disposal','disposalScrap','COMPLETED'],['disposal','disposalCancel','CANCELLED'],['disposal','disposalRecovery','REJECTED']]
 for(const [kind,key,status] of ops){
  const op=await get('assetManager','/api/asset/'+kind+'/'+state.objects[key].id)
  assert.equal(op.status,status);assert.equal(op.processInstanceId,state.objects[key].current.processInstanceId)
  check('original-operation-terminal-'+key,{operation:op,api_snapshot_only:true})
 }
 for(const before of state.prerequisites.procurement_cards){
  const current=await get('assetManager','/api/asset/asset/'+before.id)
  for(const field of ['id','assetNo','status','version','ownerUserId','ownerUnitId','sourcePoNo','sourceGrNo','purchaseAmount'])assert.equal(current[field],before[field],field)
 }
 check('six-procurement-source-cards-unchanged',{count:6})
 const roles=await get('seedAdmin','/api/auth/role/all')
 assert.equal(sha(JSON.stringify(roles.filter(row=>['ASSET_MANAGER','ASSET_ADMIN'].includes(row.roleCode)))),state.prerequisites.shared_roles_sha256)
 for(const model of state.prerequisites.models){
  const current=await get('assetManager','/api/workflow/model/'+model.id)
  assert.equal(current.currentPublishedVersionId,model.published_version_id)
  const version=await get('assetManager','/api/workflow/model/version/'+model.published_version_id)
  assert.equal(sha(version.bpmnXml),model.bpmn_sha256)
 }
 check('shared-role-definitions-and-both-existing-models-unchanged',{role_codes:['ASSET_ADMIN','ASSET_MANAGER'],model_ids:[3,4]})
 const todo=await get('assetApprover','/api/workflow/task/todo?page=1&size=100')
 const finishedIds=ops.map(([,key])=>state.objects[key].current.processInstanceId)
 assert(!todo.records.some(row=>finishedIds.includes(row.processInstanceId)))
 const delegated=state.commands['transfer-main-formal-delegate']
 if(delegated&&!delegated.complete){
  assert.equal(delegated.attempt,'asset-transfer-create-r3')
  assert.equal(delegated.path,'/api/workflow/approval/38998dc3-d2a5-11f1-938e-daec57ddc4b5/delegate?targetUserId=230')
  const prior=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/asset-transfer-create-r3/result.json'))
  const original=prior.requests.find(row=>row.method==='POST'&&row.path===delegated.path)
  assert.equal(original?.business_code,403);assert.equal(original.http_status,403)
  const approved=state.commands['transfer-main-approve']
  assert(approved?.complete);assert.equal(approved.actor,'assetManager')
  assert.equal(approved.path,'/api/workflow/approval/38998dc3-d2a5-11f1-938e-daec57ddc4b5/complete')
  assert.equal(state.objects.transferMain.current.status,'COMPLETED')
  Object.assign(delegated,{complete:true,result:null,actual_business_code:403,reconciled_at:new Date().toISOString(),reconciliation:'仅核销原403拒绝：未委托；同一任务后续由原223正式完成，AT-1-3当前已完成。保留原失败日志，不声称独立230审批，不重放委托'})
  const file=privateRoot+'/asset-scenarios.json';fs.writeFileSync(file,JSON.stringify(state,null,2)+'\n',{mode:0o600});fs.chmodSync(file,0o600)
  result.journal_reconciliations=[{key:'transfer-main-formal-delegate',actual_business_code:403,original_assigned_approver:223,business_write_replayed:false}]
 }
 const incomplete=Object.entries(state.commands).filter(([,value])=>!value.complete).map(([key,value])=>({key,attempt:value.attempt,path:value.path}))
 result.incomplete_journal_commands=incomplete
 assert.equal(incomplete.length,0,'有未核销写入意图，须按原对象核对，不自动重放')
 check('no-finished-workflow-task-or-unreconciled-write-intent',{finished_instances:finishedIds.length,incomplete_commands:0})
 result.result='PASS_READONLY_CURRENT_STATE_NOT_FULL_ASSET_ACCEPTANCE';result.finished_at=new Date().toISOString();result.raw_sha256=sha(fs.readFileSync(raw));save()
 console.log(JSON.stringify({result:result.result,checks:result.checks.length,requests:result.requests.length,life_status:life.status,life_version:life.version}))
}catch(error){result.result='FAIL_CURRENT_STATE_PRESERVED';result.error=error.message;save();throw error}
