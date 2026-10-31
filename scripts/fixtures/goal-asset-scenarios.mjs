import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs'
import {createLocalContext,privateRoot} from './goal-local-identities.mjs'
import {assetOperationModes,runAssetOperation} from './goal-asset-operations.mjs'

// 原WP28S02的本地受控场景；只操作登记身份及自然键对象，不直接写状态或占用。
assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(),'/validation/source')
const [mode,attempt]=process.argv.slice(2)
assert(['prepare','catalog','catalog-finish','allocate','accept','maintenance','maintenance-complete','return-in-use','reallocate','return',...assetOperationModes].includes(mode))
assert.match(attempt??'',new RegExp('^asset-'+mode+'-r[1-9][0-9]*$'))
assert.equal(process.env.E2E_ASSET_IMAGE_ID,'sha256:57314b7774192e23630e6fde829a2ac0830304f16f9759e0d3699382190c9176')
assert.equal(process.env.E2E_WORKFLOW_IMAGE_ID,'sha256:2d0479552f79fd62fe589e263dd6ffa87c74916f1f7c75b31c5f7d6a130094da')
const local=createLocalContext(),stateFile=privateRoot+'/asset-scenarios.json'
const state=fs.existsSync(stateFile)?JSON.parse(fs.readFileSync(stateFile)):{namespace:'GOAL20261028ASSET',commands:{},objects:{},completed:{},runs:[]}
assert.equal(state.namespace,'GOAL20261028ASSET')
const out='/validation/evidence/codex-2026-10-22/wp28-s02/'+attempt
assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true})
fs.writeFileSync(out+'/source-used.mjs',fs.readFileSync(new URL(import.meta.url)))
const raw=privateRoot+'/'+attempt+'-wire.ndjson'
fs.writeFileSync(raw,'',{flag:'wx',mode:0o600})
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex'),clone=value=>JSON.parse(JSON.stringify(value))
const run={original_step:'WP-28.S02',mode,attempt,started_at:new Date().toISOString(),workers:1,identity_source:'test-fixture',requests:[],checks:[],asset_image:process.env.E2E_ASSET_IMAGE_ID,workflow_image:process.env.E2E_WORKFLOW_IMAGE_ID,business_database_directly_updated:false,shared_role_changed:false,procurement_story_assets_changed:false,raw_private:raw,global_release_claimed:false}
fs.writeFileSync(out+'/operations-source-used.mjs',fs.readFileSync(new URL('./goal-asset-operations.mjs',import.meta.url)))
run.fixture_inputs={runner_sha256:sha(fs.readFileSync(new URL(import.meta.url))),operations_sha256:sha(fs.readFileSync(new URL('./goal-asset-operations.mjs',import.meta.url)))}
fs.writeFileSync(out+'/cancellation-source-used.mjs',fs.readFileSync(new URL('./goal-asset-cancellation.mjs',import.meta.url)))
run.fixture_inputs.cancellation_sha256=sha(fs.readFileSync(new URL('./goal-asset-cancellation.mjs',import.meta.url)))
fs.writeFileSync(out+'/recovery-source-used.mjs',fs.readFileSync(new URL('./goal-asset-recovery.mjs',import.meta.url)))
run.fixture_inputs.recovery_sha256=sha(fs.readFileSync(new URL('./goal-asset-recovery.mjs',import.meta.url)))
state.runs.push({mode,attempt,started_at:run.started_at})
const persist=()=>{
 fs.writeFileSync(stateFile,JSON.stringify(state,null,2)+'\n',{mode:0o600});fs.chmodSync(stateFile,0o600)
 fs.writeFileSync(out+'/result.json',JSON.stringify({...run,objects:state.objects,prerequisites:state.prerequisites},null,2)+'\n')
}
const tokens={}
const safeBody=body=>body?Object.fromEntries(Object.entries(body).map(([key,value])=>[key,/password|token|secret/i.test(key)?'<redacted>':value])):null
async function api(actor,path,method='GET',body,expected=200){
 assert(/^\/api\/(asset|workflow|auth)\//.test(path))
 if(actor==='seedAdmin') assert(path==='/api/auth/user'||path.startsWith('/api/auth/user/')||path==='/api/auth/role/all'||path==='/api/auth/org/tree','Seed administrator must not perform asset/workflow business')
 tokens[actor]??=actor==='seedAdmin'?local.bootstrap():await local.login(actor)
 const row={actor,user_id:actor==='seedAdmin'?1:local.state.accounts[actor].id,method,path,body:safeBody(body),at:new Date().toISOString(),expected_business_code:expected}
 run.requests.push(row);persist()
 let response
 try{response=await fetch('http://omni-gateway:8080'+path,{method,headers:{Authorization:'Bearer '+tokens[actor],...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)})}
 catch{row.outcome=method==='GET'?'TRANSPORT_ERROR':'WRITE_OUTCOME_UNCERTAIN';persist();throw Error('Read original natural key before replay: '+method+' '+path)}
 const json=await response.json()
 fs.appendFileSync(raw,JSON.stringify({...row,http_status:response.status,response:json})+'\n')
 row.http_status=response.status;row.business_code=json.code??response.status;row.payload_sha256=sha(JSON.stringify(json));persist()
 assert.equal(row.business_code,expected,method+' '+path+' unexpected code')
 if(expected===200)assert.equal(response.status,200)
 return json.data
}
async function command(key,actor,path,method,body,expected=200){
 if(state.commands[key]?.complete)return clone(state.commands[key].result)
 assert(!state.commands[key],'Unknown previous write, reconcile exact object before replay: '+key)
 state.commands[key]={actor,path,method,body_sha256:sha(JSON.stringify(body??null)),at:new Date().toISOString(),attempt};persist()
 const result=(await api(actor,path,method,body,expected))??null
 Object.assign(state.commands[key],{complete:true,result:clone(result),completed_at:new Date().toISOString()});persist()
 return result
}
function pass(id,evidence){run.checks.push({id,result:'PASS',evidence});persist();console.log(JSON.stringify({case:id,result:'PASS'}))}
async function allUsers(){
 const users=[]
 for(let page=1;page<=100;page++){
  const value=await api('seedAdmin','/api/auth/user/list?page='+page+'&size=100')
  assert(Array.isArray(value.records));users.push(...value.records)
  if(users.length>=value.total||!value.records.length)return users
 }
 throw Error('Unbounded user pagination')
}
async function prepare(){
 assert.equal(local.state.accounts.assetManager.id,223);assert.equal(local.state.accounts.assetUser.id,222)
 const roleRows=await api('seedAdmin','/api/auth/role/all')
 const role=code=>{const found=roleRows.find(row=>row.roleCode===code);assert(found?.id&&found.tenantId===1&&found.status===1);return found}
 assert.equal(role('ASSET_MANAGER').dataScope,'DEPT_AND_BELOW')
 assert.equal(role('ASSET_ADMIN').dataScope,'TENANT')
 const roleDigest=sha(JSON.stringify(roleRows.filter(row=>['ASSET_MANAGER','ASSET_ADMIN'].includes(row.roleCode))))
 const modelProofs=[]
 for(const [id,category] of [[3,'ASSET_TRANSFER'],[4,'ASSET_DISPOSAL']]){
  const model=await api('assetManager','/api/workflow/model/'+id)
  assert.equal(model.category,category);assert.equal(model.status,1);assert(model.currentPublishedVersionId)
  const version=await api('assetManager','/api/workflow/model/version/'+model.currentPublishedVersionId)
  assert.equal(version.status,'PUBLISHED')
  for(const required of ['ASSET_MANAGER','START_USER_PRIMARY_UNIT','scopedRoleAssignmentListener'])assert(version.bpmnXml.includes(required),'Published model prerequisite mismatch: '+required)
  modelProofs.push({id,modelKey:model.modelKey,category,published_version_id:model.currentPublishedVersionId,bpmn_sha256:sha(version.bpmnXml),user_tasks:(version.bpmnXml.match(/<(?:\w+:)?userTask\b/g)??[]).length,assignment_fragments:(version.bpmnXml.match(/<(?:\w+:)?assignment\b[^>]*>[\s\S]*?<\/(?:\w+:)?assignment>/g)??[]),model_changed:false})
 }
 state.prerequisites={models:modelProofs,existing_asset_manager:223,existing_asset_user:222,work_unit:local.state.organizations.work.id,peer_unit:local.state.organizations.b.id,shared_roles_sha256:roleDigest}
 persist()
 const seedSpecs=[['assetApprover','ASSET_MANAGER',local.state.organizations.work.id],['assetPeer','ASSET_MANAGER',local.state.organizations.b.id],['assetOperator','ASSET_ADMIN',local.state.organizations.work.id]]
 let users=await allUsers()
 for(const [alias,roleCode,unitId] of seedSpecs){
  const username='goal_20261028_'+alias.toLowerCase()
  let account=local.state.accounts[alias],user=users.find(row=>row.username===username)
  if(user&&!account?.create_attempted)throw Error('Refuse unknown pre-existing identity: '+username)
  if(!account){account=local.state.accounts[alias]={username,password:'G!'+crypto.randomBytes(24).toString('base64url'),create_attempted:false,identity_source:'test-fixture'};local.save()}
  if(!user){
   account.create_attempted=true;local.save()
   await command('seed-user-'+alias,'seedAdmin','/api/auth/user','POST',{tenantId:1,username,password:account.password,nickname:username})
   users=await allUsers();user=users.find(row=>row.username===username);assert(user?.id)
  }
  assert.equal(user.tenantId,1);assert.equal(user.status,1);assert.equal(user.username,username)
  account.id=user.id;local.save()
  assert(user.primaryUnitId==null||user.primaryUnitId===unitId,'Refuse unexplained primary-unit change')
  const beforeRoles=await api('seedAdmin','/api/auth/user/'+user.id+'/roles')
  const before={primaryUnitId:user.primaryUnitId,role_ids:beforeRoles}
  if(user.primaryUnitId!==unitId)await command('seed-unit-'+alias,'seedAdmin','/api/auth/user/'+user.id,'PUT',{primaryUnitId:unitId})
  if(JSON.stringify(beforeRoles.map(Number).sort())!==JSON.stringify([Number(role(roleCode).id)]))await command('seed-role-'+alias,'seedAdmin','/api/auth/user/'+user.id+'/roles','POST',[role(roleCode).id])
  const afterRoles=await api('seedAdmin','/api/auth/user/'+user.id+'/roles');assert.deepEqual(afterRoles.map(Number),[Number(role(roleCode).id)])
  account.organization_id=unitId;account.role_ids=afterRoles;account.configuration_changes??=[]
  account.configuration_changes.push({at:new Date().toISOString(),method:'formal-api-owned-test-identity',before,after:{primaryUnitId:unitId,role_ids:afterRoles},rollback:{method:'PUT',path:'/api/auth/user/'+user.id+'/status?status=0',note:'Disable only this Goal identity; preserve business/approval history'}})
  local.save();tokens[alias]=await local.login(alias);assert.deepEqual(account.actual_roles,[roleCode])
  state.objects[alias]={id:user.id,username,organization_id:unitId,role_id:role(roleCode).id,role_code:roleCode,identity_source:'test-fixture',rollback:account.configuration_changes.at(-1).rollback};persist()
 }
 const afterRoles=await api('seedAdmin','/api/auth/role/all')
 assert.equal(sha(JSON.stringify(afterRoles.filter(row=>['ASSET_MANAGER','ASSET_ADMIN'].includes(row.roleCode)))),roleDigest)
 const ledger=await api('assetManager','/api/asset/asset/list?page=1&size=100')
 state.prerequisites.procurement_cards=ledger.records.map(row=>({id:row.id,assetNo:row.assetNo,status:row.status,version:row.version,ownerUserId:row.ownerUserId,ownerUnitId:row.ownerUnitId,sourcePoNo:row.sourcePoNo,sourceGrNo:row.sourceGrNo,purchaseAmount:row.purchaseAmount}))
 assert.equal(state.prerequisites.procurement_cards.length,6)
 assert(state.prerequisites.procurement_cards.every(row=>row.status==='IN_STOCK'&&row.version===0))
 pass('AST-PREPARE-existing-models-three-minimal-owned-identities-unchanged-shared-roles-and-six-source-cards',{models:modelProofs,seeded:Object.keys(state.objects),source_cards:6,asset_business_writes:0})
 state.completed.prepare=true;persist()
}
const assetPath=id=>'/api/asset/asset/'+id
async function sameSourceCards(){
 for(const before of state.prerequisites.procurement_cards){
  const now=await api('assetManager',assetPath(before.id))
  for(const key of ['id','assetNo','status','version','ownerUserId','ownerUnitId','sourcePoNo','sourceGrNo','purchaseAmount'])assert.equal(now[key],before[key],'Existing procurement card changed: '+key)
 }
}
async function catalog(){
 assert(state.completed.prepare&&!state.completed.catalog,'Only missing original catalog phase may run')
 const manager=local.state.accounts.assetManager,unit=manager.organization_id
 const prefix='Goal 20261028 asset '
 const body=name=>({name,categoryCode:'OFFICE',specification:'Controlled asset tutorial sample',brand:'Omni',model:'Demo-2026',purchaseDate:'2026-10-28',purchaseAmount:'123.45',currencyCode:'CNY',locationCode:'ROOM-A606',warrantyExpiryDate:'2027-10-28',expectedLifeYears:3,remark:state.namespace,ownerUserId:manager.id,ownerUnitId:unit})
 for(const [key,label,amount] of [['manualLife','lifecycle','123.45'],['manualDiscard','discard','10.00'],['manualScrap','scrap','20.00'],['deleteProbe','delete-probe','1.00']]){
  const value=body(prefix+label);value.purchaseAmount=amount
  if(state.commands['create-'+key]?.complete){
   const created=state.commands['create-'+key].result
   assert.equal(created.name,value.name);assert.equal(created.ownerUserId,manager.id)
   state.objects[key]??={id:created.id,assetNo:created.assetNo,name:created.name,create_result:created,kind:'owned-manual-asset'}
  }else{
   const existing=await api('assetManager','/api/asset/asset/list?page=1&size=100&keyword='+encodeURIComponent(value.name))
   assert(!existing.records.some(row=>row.name===value.name),'Unknown matching manual card must not be adopted')
   const created=await command('create-'+key,'assetManager','/api/asset/asset','POST',value)
   assert.equal(created.status,'IN_STOCK');assert.equal(created.version,0);assert.equal(created.currentUserId,null);assert.equal(created.sourceGrId,null)
   assert.equal(created.purchaseAmount,amount);assert.equal(typeof created.purchaseAmount,'string')
   state.objects[key]={id:created.id,assetNo:created.assetNo,name:created.name,create_result:created,kind:'owned-manual-asset',rollback:'Retain lifecycle history; only never-mutated manual card can be formally deleted, otherwise use normal business disposal.'}
  }
  persist()
 }
 const life=state.objects.manualLife
 const initial=await api('assetManager',assetPath(life.id))
 const update={...body(life.name),version:state.objects.manualLife.create_result.version,purchaseAmount:'125.50',brand:'Omni tutorial revised'}
 const updated=await command('update-manualLife','assetManager',assetPath(life.id),'PUT',update)
 assert.equal(updated.purchaseAmount,'125.50');assert.equal(updated.brand,update.brand);assert.equal(updated.status,'IN_STOCK')
 assert.equal(updated.ownerUserId,manager.id);assert.equal(updated.ownerUnitId,unit)
 await command('stale-update-manualLife','assetManager',assetPath(life.id),'PUT',update,409)
 await command('history-protected-delete-manualLife','assetManager',assetPath(life.id)+'?version='+updated.version,'DELETE',undefined,409)
 const probe=state.objects.deleteProbe
 const deleteVersion=state.objects.deleteProbe.create_result.version
 await command('delete-never-mutated-probe','assetManager',assetPath(probe.id)+'?version='+deleteVersion,'DELETE')
 await api('assetManager',assetPath(probe.id),'GET',undefined,404)
 probe.deleted_by_formal_api=true;persist()
 const history=await api('assetManager',assetPath(life.id)+'/history?page=1&size=100')
 assert(history.total>=2);assert(history.records.some(row=>row.remark?.includes('更新资产资料')))
 const source=state.prerequisites.procurement_cards[0]
 await command('source-protected-delete','assetManager',assetPath(source.id)+'?version='+source.version,'DELETE',undefined,409)
 const bad=body(prefix+'invalid-numeric-money');bad.purchaseAmount=123.45
 // Asset现有契约明确JSON读取错误经通用Advice返回500；拒绝结果与无落库需分别核对。
 await command('numeric-money-rejected','assetManager','/api/asset/asset','POST',bad,500)
 await command('self-role-create-rejected','assetUser','/api/asset/asset','POST',body(prefix+'forbidden-user-create'),403)
 const peer=await api('assetPeer','/api/asset/asset/list?page=1&size=100&keyword='+encodeURIComponent(prefix))
 assert.equal(peer.total,0)
 await api('assetPeer',assetPath(life.id),'GET',undefined,404)
 const deniedAllocation={version:updated.version,targetUserId:local.state.accounts.assetPeer.id,targetUnitId:local.state.accounts.assetPeer.organization_id,remark:state.namespace+' cross-scope negative'}
 await command('cross-department-allocation-rejected','assetManager',assetPath(life.id)+'/allocate','POST',deniedAllocation,403)
 const own=await api('assetManager','/api/asset/asset/list?page=1&size=2&keyword='+encodeURIComponent(prefix))
 assert.equal(own.total,3);assert.equal(own.records.length,2)
 const second=await api('assetManager','/api/asset/asset/list?page=2&size=2&keyword='+encodeURIComponent(prefix))
 assert.equal(second.records.length,1);assert(!own.records.some(row=>row.id===second.records[0].id))
 const after=await api('assetManager',assetPath(life.id))
 assert.equal(after.version,updated.version);assert.equal(after.status,'IN_STOCK');assert.equal(after.currentUserId,null)
 await sameSourceCards()
 pass('AST-04-actual-manual-create-update-stale-and-delete-history-source-guards',{manualLife:{id:life.id,version:after.version,purchaseAmount:after.purchaseAmount},deletedProbe:probe.id,history_records:history.total})
 pass('AST-02-AST-03-AST-12-AST-16-current-pagination-self-role-sibling-scope-and-target-write-boundaries',{owned_total:3,pages:[2,1],peer_total:0,negative_business_codes:[400,403,404,409],unchanged_source_cards:6})
 state.objects.manualLife.current=after;state.completed.catalog=true;persist()
}
async function catalogFinish(){
 assert(state.completed.prepare&&!state.completed.catalog)
 const contract=fs.readFileSync('/source/docs/api-contract.md','utf8')
 assert.equal(sha(contract),'63bb7c8c879ae25e46dc17642a41b61e9445efe48583def3e7afa92a7d2bd837')
 assert(contract.includes('Asset 当前通用处理器会落入 500'))
 const priorFile='/validation/evidence/codex-2026-10-22/wp28-s02/asset-catalog-r1/result.json'
 const prior=JSON.parse(fs.readFileSync(priorFile))
 assert.equal(prior.result,'FAIL_PRESERVE_CURRENT_OBJECTS_AND_UNCERTAIN_COMMANDS')
 const previous=prior.requests.at(-1)
 assert.equal(previous.path,'/api/asset/asset');assert.equal(previous.business_code,500);assert.equal(previous.expected_business_code,400)
 const priorRaw=privateRoot+'/asset-catalog-r1-wire.ndjson'
 const response=JSON.parse(fs.readFileSync(priorRaw,'utf8').trim().split('\n').at(-1))
 assert.equal(response.response.code,500);assert.equal(response.http_status,500)
 assert.equal(response.body.name,'Goal 20261028 asset invalid-numeric-money');assert.equal(typeof response.body.purchaseAmount,'number')
 const absent=await api('assetManager','/api/asset/asset/list?page=1&size=100&keyword='+encodeURIComponent(response.body.name))
 assert.equal(absent.total,0,'Invalid input must not produce a card')
 const pending=state.commands['numeric-money-rejected']
 assert(pending&&!pending.complete)
 assert.equal(pending.body_sha256,sha(JSON.stringify(response.body)))
 Object.assign(pending,{complete:true,result:null,completed_at:new Date().toISOString(),reconciled:true,original_wrong_expected_business_code:400,actual_business_code:500,
  evidence:{response_log:priorRaw,response_log_sha256:sha(fs.readFileSync(priorRaw)),contract:'docs/api-contract.md',contract_sha256:sha(contract),zero_natural_key_rows:0},
  reason:'Initial test expectation contradicted existing documented Asset JSON mapping. Actual response and zero persisted objects verified; no replay and no product contract/assertion weakened.'})
 run.prior_failed_attempt={path:priorFile,sha256:sha(fs.readFileSync(priorFile)),original_raw:priorRaw,raw_sha256:sha(fs.readFileSync(priorRaw)),actual_requests:prior.requests.length,original_failure_preserved:true,successful_commands_not_reexecuted:true};persist()
 const life=state.objects.manualLife,updated=state.commands['update-manualLife'].result
 assert.equal(updated.version,1);assert.equal(updated.purchaseAmount,'125.50')
 assert(state.commands['delete-never-mutated-probe'].complete&&state.objects.deleteProbe.deleted_by_formal_api)
 const forbidden={name:'Goal 20261028 asset forbidden-user-create',categoryCode:'OFFICE',currencyCode:'CNY',purchaseAmount:'1.00',ownerUserId:222,ownerUnitId:local.state.accounts.assetUser.organization_id}
 await command('self-role-create-rejected','assetUser','/api/asset/asset','POST',forbidden,403)
 const prefix='Goal 20261028 asset '
 const peer=await api('assetPeer','/api/asset/asset/list?page=1&size=100&keyword='+encodeURIComponent(prefix));assert.equal(peer.total,0)
 await api('assetPeer',assetPath(life.id),'GET',undefined,404)
 await command('cross-department-allocation-rejected','assetManager',assetPath(life.id)+'/allocate','POST',{version:updated.version,targetUserId:local.state.accounts.assetPeer.id,targetUnitId:local.state.accounts.assetPeer.organization_id,remark:state.namespace+' cross-scope negative'},403)
 const page1=await api('assetManager','/api/asset/asset/list?page=1&size=2&keyword='+encodeURIComponent(prefix))
 const page2=await api('assetManager','/api/asset/asset/list?page=2&size=2&keyword='+encodeURIComponent(prefix))
 assert.equal(page1.total,3);assert.equal(page1.records.length,2);assert.equal(page2.records.length,1);assert(!page1.records.some(row=>row.id===page2.records[0].id))
 const after=await api('assetManager',assetPath(life.id));assert.equal(after.version,1);assert.equal(after.status,'IN_STOCK');assert.equal(after.currentUserId,null)
 const history=await api('assetManager',assetPath(life.id)+'/history?page=1&size=100');assert.equal(history.total,2)
 await sameSourceCards()
 pass('AST-04-actual-manual-create-update-stale-and-delete-history-source-guards',{manualLife:{id:life.id,version:after.version,purchaseAmount:after.purchaseAmount},deletedProbe:state.objects.deleteProbe.id,history_records:history.total,reused_prior_real_requests:17,invalid_numeric:{actual_http:500,actual_business_code:500,zero_created:0,original_wrong_expectation_preserved:true}})
 pass('AST-02-AST-03-AST-12-AST-16-current-pagination-self-role-sibling-scope-and-target-write-boundaries',{owned_total:3,pages:[2,1],peer_total:0,negative_business_codes:[403,404,409,500],unchanged_source_cards:6})
 state.objects.manualLife.current=after;state.completed.catalog=true;persist()
}
// 原WP28S02生命周期场景正文；构建冻结结束后合入正式fixture，不新增计划步骤。
async function readLife(expectedStatus,expectedVersion,assigned){
 const life=state.objects.manualLife,current=await api('assetManager',assetPath(life.id))
 assert.equal(current.id,life.id);assert.equal(current.status,expectedStatus);assert.equal(current.version,expectedVersion)
 assert.equal(current.purchaseAmount,'125.50');assert.equal(current.ownerUserId,223);assert.equal(current.ownerUnitId,606)
 assert.equal(current.currentUserId,assigned?222:null);assert.equal(current.currentUnitId,assigned?606:null)
 assert.equal(current.activeOperationId,null);assert.equal(current.activeOperationType,null)
 assert.equal(current.sourceGrId,null);state.objects.manualLife.current=current;persist();return current
}
async function myLife(actor,expected){
 const list=await api(actor,'/api/asset/asset/my?page=1&size=100&keyword='+encodeURIComponent(state.objects.manualLife.assetNo))
 assert.equal(list.total,expected)
 if(expected){assert.equal(list.records[0].id,state.objects.manualLife.id);assert.equal(list.records[0].currentUserId,local.state.accounts[actor].id)}
 return list
}
async function eligibleLife(expected){
 for(const kind of ['transfer','disposal']){
  const options=await api('assetManager','/api/asset/options/'+kind+'-assets?keyword='+encodeURIComponent(state.objects.manualLife.assetNo)+'&limit=30')
  assert.equal(options.some(row=>row.id===state.objects.manualLife.id),expected,kind+' candidate must follow actual state and occupancy')
 }
}
async function lifecycleAllocate(){
 assert(state.completed.catalog&&!state.completed.allocate)
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-allocate']?.complete)await readLife('IN_STOCK',1,false)
 const allocated=await command('life-allocate','assetManager',path+'/allocate','POST',{version:1,targetUserId:222,targetUnitId:606,remark:state.namespace+' controlled allocation'})
 assert.equal(allocated.status,'ALLOCATED');assert.equal(allocated.version,2)
 await command('life-other-admin-cannot-accept','assetOperator',path+'/accept','POST',{version:2},404)
 await command('life-owner-cannot-return-for-recipient','assetManager',path+'/return','POST',{version:2},404)
 await api('assetUser',path,'GET',undefined,403)
 await myLife('assetUser',1);await myLife('assetManager',0);await eligibleLife(true)
 const current=await readLife('ALLOCATED',2,true);await sameSourceCards()
 pass('AST-05-AST-06-AST-16-real-allocation-and-fixed-self-boundary',{asset_id:current.id,status:current.status,version:current.version,current_user:222,current_unit:606,other_admin_accept:404,owner_return:404,self_management_detail:403,self_rows:1,manager_my_rows:0,eligible_while_allocated:true,unchanged_source_cards:6})
 state.completed.allocate=true;persist()
}
async function lifecycleAccept(){
 assert(state.completed.allocate&&!state.completed.accept)
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-accept']?.complete)await readLife('ALLOCATED',2,true)
 const accepted=await command('life-accept','assetUser',path+'/accept','POST',{version:2,remark:state.namespace+' recipient accepted'})
 assert.equal(accepted.status,'IN_USE');assert.equal(accepted.version,3)
 await command('life-stale-accept','assetUser',path+'/accept','POST',{version:2},409)
 await command('life-self-no-maintenance-authority','assetUser',path+'/maintenance/start','POST',{version:3},403)
 await myLife('assetUser',1);await myLife('assetManager',0);await eligibleLife(true)
 const current=await readLife('IN_USE',3,true);await sameSourceCards()
 pass('AST-06-real-recipient-accept-version-and-role-checks',{asset_id:current.id,status:current.status,version:current.version,recipient:222,stale_accept:409,self_maintenance:403,eligible_while_in_use:true,unchanged_source_cards:6})
 state.completed.accept=true;persist()
}
async function lifecycleMaintenance(){
 assert(state.completed.accept&&!state.completed.maintenance)
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-maintenance-start']?.complete)await readLife('IN_USE',3,true)
 const repaired=await command('life-maintenance-start','assetManager',path+'/maintenance/start','POST',{version:3,remark:state.namespace+' repair started'})
 assert.equal(repaired.status,'MAINTENANCE');assert.equal(repaired.version,4)
 await command('life-maintenance-cannot-allocate','assetManager',path+'/allocate','POST',{version:4,targetUserId:222,targetUnitId:606},409)
 await command('life-maintenance-cannot-return','assetUser',path+'/return','POST',{version:4},409)
 await command('life-maintenance-cannot-transfer','assetManager','/api/asset/transfer','POST',{assetId:state.objects.manualLife.id,toUserId:222,toUnitId:606,toLocation:'ROOM-A606-B',reason:state.namespace+' maintenance negative'},409)
 await command('life-maintenance-cannot-dispose','assetManager','/api/asset/disposal','POST',{assetId:state.objects.manualLife.id,disposalType:'SCRAP',reason:state.namespace+' maintenance negative',residualValue:'0.00',disposalMethod:'Controlled test'},409)
 for(const kind of ['transfer','disposal']){
  const list=await api('assetManager','/api/asset/'+kind+'/list?page=1&size=100&keyword='+encodeURIComponent(state.objects.manualLife.assetNo))
  assert.equal(list.total,0,'Rejected maintenance request must not leave an application')
 }
 await eligibleLife(false);await myLife('assetUser',1)
 const current=await readLife('MAINTENANCE',4,true);await sameSourceCards()
 pass('AST-08-real-maintenance-preserves-recipient-and-rejects-incompatible-commands',{asset_id:current.id,status:current.status,version:current.version,recipient:222,rejected_commands:['allocate','return','transfer','disposal'],business_codes:[409,409,409,409],created_applications:0,eligible_candidates:0,unchanged_source_cards:6})
 state.completed.maintenance=true;persist()
}
async function lifecycleMaintenanceComplete(){
 assert(state.completed.maintenance&&!state.completed['maintenance-complete'])
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-maintenance-complete']?.complete)await readLife('MAINTENANCE',4,true)
 const completed=await command('life-maintenance-complete','assetManager',path+'/maintenance/complete','POST',{version:4,remark:state.namespace+' repair completed'})
 assert.equal(completed.status,'IN_USE');assert.equal(completed.version,5)
 const current=await readLife('IN_USE',5,true);await myLife('assetUser',1);await eligibleLife(true);await sameSourceCards()
 pass('AST-08-real-maintenance-complete-restores-use-not-stock',{asset_id:current.id,status:current.status,version:current.version,recipient_preserved:222,unchanged_source_cards:6})
 state.completed['maintenance-complete']=true;persist()
}
async function lifecycleReturnInUse(){
 assert(state.completed['maintenance-complete']&&!state.completed['return-in-use'])
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-return-from-use']?.complete)await readLife('IN_USE',5,true)
 const first=await command('life-return-from-use','assetUser',path+'/return','POST',{version:5,remark:state.namespace+' actual return after use'})
 assert.equal(first.status,'IN_STOCK');assert.equal(first.version,6);assert.equal(first.currentUserId,null);assert.equal(first.currentUnitId,null)
 const current=await readLife('IN_STOCK',6,false);assert.equal(current.allocatedTime,null);await myLife('assetUser',0);await sameSourceCards()
 pass('AST-07-real-return-from-use-clears-current-use',{asset_id:current.id,status:current.status,version:6,self_rows:0,unchanged_source_cards:6})
 state.completed['return-in-use']=true;persist()
}
async function lifecycleReallocate(){
 assert(state.completed['return-in-use']&&!state.completed.reallocate)
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-reallocate-for-direct-return']?.complete)await readLife('IN_STOCK',6,false)
 const allocated=await command('life-reallocate-for-direct-return','assetManager',path+'/allocate','POST',{version:6,targetUserId:222,targetUnitId:606,remark:state.namespace+' return before acceptance'})
 assert.equal(allocated.status,'ALLOCATED');assert.equal(allocated.version,7)
 const current=await readLife('ALLOCATED',7,true);await myLife('assetUser',1);await sameSourceCards()
 pass('AST-07-real-pending-acceptance-precondition-for-direct-return',{asset_id:current.id,status:current.status,version:7,recipient:222,unchanged_source_cards:6})
 state.completed.reallocate=true;persist()
}
async function lifecycleReturn(){
 assert(state.completed.reallocate&&!state.completed.return)
 const path=assetPath(state.objects.manualLife.id)
 if(!state.commands['life-return-before-acceptance']?.complete)await readLife('ALLOCATED',7,true)
 const second=await command('life-return-before-acceptance','assetUser',path+'/return','POST',{version:7,remark:state.namespace+' actual return before acceptance'})
 assert.equal(second.status,'IN_STOCK');assert.equal(second.version,8)
 await command('life-double-return-rejected','assetUser',path+'/return','POST',{version:8},404)
 const current=await readLife('IN_STOCK',8,false);assert.equal(current.allocatedTime,null)
 await myLife('assetUser',0);await eligibleLife(true)
 const history=await api('assetManager',path+'/history?page=1&size=100');assert.equal(history.total,9)
 for(const text of ['分配资产','确认领用','送修','维修完成','退还资产'])assert(history.records.some(row=>row.remark?.includes(text)))
 await sameSourceCards()
 pass('AST-07-AST-03-both-real-returns-clear-use-and-preserve-history',{asset_id:current.id,status:current.status,version:current.version,returned_from:['IN_USE','ALLOCATED'],current_user:null,current_unit:null,allocated_time:null,self_rows:0,duplicate_return:404,history_records:history.total,unchanged_source_cards:6})
 state.completed.return=true;persist()
}

try{
 if(mode==='prepare')await prepare()
 if(mode==='catalog')await catalog()
 if(mode==='catalog-finish')await catalogFinish()
 if(mode==='allocate')await lifecycleAllocate()
 if(mode==='accept')await lifecycleAccept()
 if(mode==='maintenance')await lifecycleMaintenance()
 if(mode==='maintenance-complete')await lifecycleMaintenanceComplete()
 if(mode==='return-in-use')await lifecycleReturnInUse()
 if(mode==='reallocate')await lifecycleReallocate()
 if(mode==='return')await lifecycleReturn()
 if(assetOperationModes.includes(mode))await runAssetOperation(mode,{state,local,api,command,pass,persist,sameSourceCards})
 run.result=assetOperationModes.includes(mode)?'PASS_REAL_OWNED_OPERATION_PHASE_NOT_FULL_ASSET_ACCEPTANCE':['allocate','accept','maintenance','maintenance-complete','return-in-use','reallocate','return'].includes(mode)?'PASS_REAL_OWNED_LIFECYCLE_PHASE_NOT_FULL_ASSET_ACCEPTANCE':mode==='prepare'?'PASS_OWNED_TEST_CONFIGURATION_PREPARED_NOT_ASSET_ACTION_ACCEPTANCE':'PASS_OWNED_MANUAL_CATALOG_AND_SCOPE_NOT_FULL_ASSET_ACCEPTANCE'
 run.raw_sha256=sha(fs.readFileSync(raw));run.finished_at=new Date().toISOString();persist()
}catch(error){run.result='FAIL_PRESERVE_CURRENT_OBJECTS_AND_UNCERTAIN_COMMANDS';run.error=error.message;persist();throw error}
