import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02：专用采购员走真实单据链，未知写结果先查状态；不回放旧成功业务。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
const local=createLocalContext(), [mode,attempt]=process.argv.slice(2)
assert.ok(['prepare','quote-order','receive','alternates','overview'].includes(mode)); assert.match(attempt??'',new RegExp(`^business-${mode}-r[1-9][0-9]*$`))
for(const [name,expected] of Object.entries({PROCUREMENT:'e043e8bebc7df4cb5c8b55bb88e9624c9e204f7c2897dda30c942e6921d2d340',SRM:'169eb5f48c0ebf44367a09e8771f7c8b45cb8b1ff5eb1d16185a0795a6640143',WORKFLOW:'2d0479552f79fd62fe589e263dd6ffa87c74916f1f7c75b31c5f7d6a130094da'})) assert.equal(process.env['E2E_'+name+'_IMAGE_ID'],'sha256:'+expected)
if(mode==='receive')assert.equal(process.env.E2E_ASSET_IMAGE_ID,'sha256:57314b7774192e23630e6fde829a2ac0830304f16f9759e0d3699382190c9176')
const statePath=privateRoot+'/procurement-business-scenarios.json'
const state=existsSync(statePath)?JSON.parse(readFileSync(statePath,'utf8')):{namespace:'GOAL20261022PROCBUSINESS',commands:{},objects:{},completed:{},runs:[]}
assert.equal(state.namespace,'GOAL20261022PROCBUSINESS')
const config=JSON.parse(readFileSync(privateRoot+'/procurement-config-scenarios.json','utf8'))
assert.ok(config.completed['CONFIG-real-role-denials-and-other-rules-preserved'])
const dir='/validation/evidence/codex-2026-10-22/wp27-s02/'+attempt
assert.ok(!existsSync(dir));mkdirSync(dir,{recursive:true})
const raw=privateRoot+'/'+attempt+'-wire.ndjson';writeFileSync(raw,'',{flag:'wx',mode:0o600})
const sha=value=>createHash('sha256').update(value).digest('hex'),clone=value=>JSON.parse(JSON.stringify(value))
const run={original_step:'WP-27.S02',mode,attempt,started_at:new Date().toISOString(),identity_source:'test-fixture',workers:1,
 script_sha256:sha(readFileSync(new URL(import.meta.url))),runtime_images:Object.fromEntries(['PROCUREMENT','SRM','WORKFLOW'].map(name=>[name,process.env['E2E_'+name+'_IMAGE_ID']])),requests:[],cases:[],original_raw_log:raw,
 limits:['只本次实际已执行case，不冒称全部采购/文图/G11完成','正式受控登录，非真人验证码；不输出认证头/凭据','供应商9分类是本次新前置，供应商10和已取消RFQ3不变']}
if(mode==='receive')run.runtime_images.ASSET=process.env.E2E_ASSET_IMAGE_ID
state.runs.push({attempt,mode,at:run.started_at})
const persist=()=>{writeFileSync(statePath,JSON.stringify(state,null,2)+'\n',{mode:0o600});writeFileSync(dir+'/result.json',JSON.stringify({...run,objects:state.objects},null,2)+'\n')}
const tokens={};let currentCase
async function api(actor,path,method='GET',body,expected=200){
 assert.ok(/^\/api\/(srm|procurement|workflow|asset)\//.test(path));tokens[actor]??=await local.login(actor)
 const row={case:currentCase,actor,user_id:local.state.accounts[actor].id,method,path,body:body??null,expected,at:new Date().toISOString()};run.requests.push(row);persist()
 let response
 try{response=await fetch('http://omni-gateway:8080'+path,{method,headers:{Authorization:`Bearer ${tokens[actor]}`,...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)})}
 catch{row.outcome=method==='GET'?'TRANSPORT_ERROR':'WRITE_OUTCOME_UNCERTAIN';persist();throw new Error(method+' '+path+'结果未知，先核对固定对象，不盲重发')}
 const data=await response.json();appendFileSync(raw,JSON.stringify({...row,http_status:response.status,response:data})+'\n')
 row.http_status=response.status;row.business_code=data.code??response.status;row.payload_sha256=sha(JSON.stringify(data));persist()
 assert.equal(row.business_code,expected,actor+':'+method+' '+path);if(expected===200)assert.equal(response.status,200)
 return data.data
}
async function command(key,actor,path,method,body,expected=200){
 if(state.commands[key]?.complete)return clone(state.commands[key].result)
 assert.ok(!state.commands[key],'旧命令'+key+'结果未知，禁止重复写入')
 state.commands[key]={actor,path,method,body:body??null,expected,attempt,at:new Date().toISOString()};persist()
 const result=(await api(actor,path,method,body,expected))??null
 Object.assign(state.commands[key],{complete:true,result:clone(result),completed_at:new Date().toISOString()});persist();return result
}
async function check(id,action){
 currentCase=id
 if(state.completed[id]){assert.deepEqual(state.completed[id].runtime_images,run.runtime_images);run.cases.push({id,result:'REUSE_PREVIOUS_ACTUAL_PASS',evidence:state.completed[id]});persist();return}
 const start=run.requests.length
 try{const evidence=await action(),result={id,result:'PASS',attempt,at:new Date().toISOString(),runtime_images:run.runtime_images,request_indexes:[start,run.requests.length-1],evidence};state.completed[id]=clone(result);run.cases.push(result);persist();console.log(JSON.stringify({case:id,result:'PASS',requests:run.requests.length-start}))}
 catch(error){run.cases.push({id,result:'FAIL',error:error.message});persist();throw error}
}
const req=id=>'/api/procurement/requisition/'+id,rfq=id=>'/api/procurement/rfq/'+id
const dateAfterDays=days=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(new Date(Date.now()+days*86400000))
async function waitReq(id,status){let result;for(let count=0;count<75;count++){result=await api('procStaff',req(id));if(result.status===status)return result;await new Promise(resolve=>setTimeout(resolve,400))}assert.equal(result.status,status);return result}
async function approve(key,started,approved){
 const tasks=await api('procManager','/api/workflow/task/todo?page=1&size=100'),matches=tasks.records.filter(row=>row.processInstanceId===started.processInstanceId)
 assert.equal(matches.length,1);assert.equal(Number(matches[0].assigneeId),217)
 const taskId=matches[0].taskId,view=await api('procManager',req(started.id)+'/approval-view?taskId='+encodeURIComponent(taskId));assert.equal(view.requisition.id,started.id)
 await command(key,'procManager','/api/workflow/approval/'+encodeURIComponent(taskId)+'/complete','POST',{approved,comment:'本Goal采购全集真实'+(approved?'批准':'驳回')+'，不改库结果',variables:{}})
 return waitReq(started.id,approved?'APPROVED':'REJECTED')
}
async function srmCounters(){
 const response=await fetch('http://omni-srm:19905/actuator/prometheus',{signal:AbortSignal.timeout(30000)});assert.equal(response.status,200);const body=await response.text(),counts={}
 for(const uri of ['/api/internal/supplier/search','/api/internal/supplier/batch','/api/internal/quotation/batch']) counts[uri]=body.split('\n').filter(line=>line.startsWith('http_server_requests_seconds_count{')&&line.includes('uri="'+uri+'"')&&line.includes('status="200"')).reduce((sum,line)=>sum+Number(line.slice(line.lastIndexOf('}')+1).trim()),0)
 return{at:new Date().toISOString(),counts,payload_sha256:sha(body)}
}
const po=id=>'/api/procurement/purchase-order/'+id,gr=id=>'/api/procurement/goods-receipt/'+id
async function assetCards(){
 const list=await api('assetManager','/api/asset/asset/list?page=1&size=100&categoryCode='+encodeURIComponent(config.objects.leaf.categoryCode)+'&keyword='+encodeURIComponent(config.objects.asset.materialName))
 assert.ok(Array.isArray(list.records));return list.records.filter(row=>row.sourcePoId===state.objects.order.id)
}
async function waitCards(count){
 let rows
 for(let attempt=0;attempt<100;attempt++){rows=await assetCards();if(rows.length===count)return rows;assert.ok(rows.length<count,'不能用等待隐藏重复建卡');await new Promise(resolve=>setTimeout(resolve,400))}
 assert.equal(rows.length,count,'真实消费/建卡未在限定观察时间完成，不重放收货命令');return rows
}
try{
 if(mode==='prepare'){
 await check('SUPPLIER-actual-existing-portal-and-category-precondition',async()=>{
  const profile=await api('supplier','/api/srm/portal/profile');assert.equal(profile.supplierId,9)
  const supplier=await api('srmAdmin','/api/srm/supplier/9');assert.equal(supplier.status,'APPROVED');assert.equal(supplier.name,'Goal 20261022 supplier');assert.equal(supplier.version,3);assert.equal(supplier.categoryCode,null)
  state.supplier_before={id:9,name:supplier.name,categoryCode:supplier.categoryCode,status:supplier.status,ownerUserId:supplier.ownerUserId,ownerUnitId:supplier.ownerUnitId,version:supplier.version};persist()
  await command('set-only-goal-supplier9-category','srmAdmin','/api/srm/supplier/9','PUT',{version:supplier.version,categoryCode:config.objects.leaf.categoryCode})
  const after=await api('srmAdmin','/api/srm/supplier/9');assert.equal(after.categoryCode,config.objects.leaf.categoryCode);assert.equal(after.status,supplier.status);assert.equal(after.ownerUserId,supplier.ownerUserId)
  state.objects.supplier={id:9,portal_user:224,categoryCode:after.categoryCode,status:after.status,version:after.version};persist()
  run.internal_before=await srmCounters();state.internal_before=run.internal_before;state.internal_before_attempt=attempt;persist()
  const options=await api('procStaff','/api/procurement/rfq/supplier-options?categoryCode='+encodeURIComponent(after.categoryCode)+'&keyword='+encodeURIComponent(supplier.name)+'&limit=100')
  assert.equal(options.length,1);assert.equal(options[0].id,9)
  const preserved=await api('srmAdmin','/api/srm/supplier/10');assert.equal(preserved.version,20);assert.equal(preserved.ownerUserId,215);assert.equal(preserved.categoryCode,null)
  return{supplier_id:9,portal_user:224,categoryCode:after.categoryCode,formal_update_version:after.version,supplier10_preserved_version:20,real_rfq_ui_category_options:[9]}
 })
 await check('REQ-02-real-draft-crud-and-REQ-03-rejected-edit-new-round-approval',async()=>{
  const mainTitle='Goal 20261022 procurement main chain',draftTitle='Goal 20261022 procurement disposable draft'
  for(const title of[mainTitle,draftTitle])assert.equal((await api('procStaff','/api/procurement/requisition/list?page=1&size=100&keyword='+encodeURIComponent(title))).records.length,0,'新业务自然键已有未知对象')
  const lines=[{materialId:config.objects.asset.id,quantity:'3.000000',estimatedUnitPrice:'25.000000',remark:'三件资产化样本'},{materialId:config.objects.supply.id,quantity:'2.000000',estimatedUnitPrice:'12.500000',remark:'两件非资产样本'}]
  const body={title:mainTitle,reason:'本Goal采购全集真实审批、报价、分批收货和质检样本',lines};state.main_body=body;persist()
  await command('reject-zero-quantity','procStaff','/api/procurement/requisition','POST',{...body,lines:[{...lines[0],quantity:'0.000000'}]},400)
  const disposable=await command('create-disposable-request','procStaff','/api/procurement/requisition','POST',{...body,title:draftTitle})
  const changed=await command('edit-disposable-request','procStaff',req(disposable.id),'PUT',{...body,title:draftTitle+' edited',version:disposable.version})
  await command('reject-stale-request-edit','procStaff',req(disposable.id),'PUT',{...body,title:draftTitle,version:disposable.version},409)
  await command('peer-delete-request-denied','procPeer',req(disposable.id)+'?version='+changed.version,'DELETE',undefined,404)
  await command('delete-disposable-request','procStaff',req(disposable.id)+'?version='+changed.version,'DELETE')
  await api('procStaff',req(disposable.id),'GET',undefined,404)
  let main=await command('create-main-request','procStaff','/api/procurement/requisition','POST',body)
  assert.equal(main.totalAmount,'100.0000');assert.equal(main.requesterUserId,215);assert.equal(main.requesterUnitId,606);state.objects.requisition=main;persist()
  main=await command('submit-main-first-round','procStaff',req(main.id)+'/submit','POST',{version:main.version});assert.equal(main.status,'APPROVING');assert.equal(main.approvalAttempt,1)
  const firstInstance=main.processInstanceId
  main=await approve('reject-main-first-round',main,false);assert.equal(main.status,'REJECTED')
  main=await command('edit-rejected-main-back-to-draft','procStaff',req(main.id),'PUT',{...body,version:main.version});assert.equal(main.status,'DRAFT');assert.equal(main.workflowStartStatus,'NOT_STARTED');assert.equal(main.workflowBusinessKey,null);assert.equal(main.processInstanceId,null)
  main=await command('submit-main-second-round','procStaff',req(main.id)+'/submit','POST',{version:main.version});assert.equal(main.approvalAttempt,2);assert.equal(main.workflowBusinessKey,main.id+':2');assert.notEqual(main.processInstanceId,firstInstance)
  main=await approve('approve-main-second-round',main,true);assert.equal(main.status,'APPROVED');assert.equal(main.approvalAttempt,2)
  state.objects.requisition=main;state.first_rejected_process=firstInstance;persist()
  await api('procScopeManager',req(main.id));await api('procPeer',req(main.id),'GET',undefined,404)
  const invisible=await api('procPeer','/api/procurement/requisition/list?page=1&size=100&keyword='+encodeURIComponent(main.requisitionNo));assert.equal(invisible.records.length,0)
  return{deleted_draft:disposable.id,main_requisition:main.id,total_amount:main.totalAmount,first_rejected_process:firstInstance,second_approved_process:main.processInstanceId,approval_attempt:2,requester:215,peer_detail_code:404}
 })
 await check('RFQ-01-RFQ-02-real-draft-crud-send-and-portal-scope',async()=>{
  const main=state.objects.requisition,deadline=state.deadline??=dateAfterDays(7);persist()
  const body={requisitionId:main.id,title:'Goal 20261022 procurement main RFQ',quotationDeadline:deadline,supplierIds:[9]}
  await command('reject-rfq-from-cancelled-request','procStaff','/api/procurement/rfq','POST',{...body,requisitionId:6},409)
  await command('reject-past-rfq-deadline','procStaff','/api/procurement/rfq','POST',{...body,quotationDeadline:'2026-01-01 00:00:00'},400)
  assert.equal((await api('procStaff','/api/procurement/rfq/list?page=1&size=100&keyword='+encodeURIComponent('Goal 20261022 procurement'))).records.length,0)
  let disposable=await command('create-disposable-rfq','procStaff','/api/procurement/rfq','POST',{...body,title:'Goal 20261022 procurement disposable RFQ'})
  const firstVersion=disposable.version
  disposable=await command('edit-disposable-rfq','procStaff',rfq(disposable.id),'PUT',{title:'Goal 20261022 procurement disposable RFQ edited',quotationDeadline:deadline,supplierIds:[9],version:firstVersion})
  await command('stale-disposable-rfq-edit','procStaff',rfq(disposable.id),'PUT',{title:body.title,quotationDeadline:deadline,supplierIds:[9],version:firstVersion},409)
  await api('supplier','/api/srm/portal/quotation/invitations/'+disposable.id,'GET',undefined,404)
  await command('peer-delete-rfq-denied','procPeer',rfq(disposable.id)+'?version='+disposable.version,'DELETE',undefined,404)
  await command('delete-disposable-rfq','procStaff',rfq(disposable.id)+'?version='+disposable.version,'DELETE')
  await api('procStaff',rfq(disposable.id),'GET',undefined,404)
  let current=await command('create-main-rfq','procStaff','/api/procurement/rfq','POST',body)
  await api('procStaff',rfq(current.id)+'/comparison','GET',undefined,409)
  await command('stale-main-rfq-send','procStaff',rfq(current.id)+'/send','POST',{version:current.version+1},409)
  current=await command('send-main-rfq','procStaff',rfq(current.id)+'/send','POST',{version:current.version});assert.equal(current.status,'SENT');assert.equal(current.ownerUserId,215);assert.equal(current.lines.length,2)
  state.objects.rfq=current;persist()
  assert.deepEqual(await api('procStaff',rfq(current.id)+'/comparison'),[])
  await api('procScopeManager',rfq(current.id));await api('procPeer',rfq(current.id),'GET',undefined,404)
  const invitations=await api('supplier','/api/srm/portal/quotation/invitations');assert.ok(invitations.some(row=>row.rfqId===current.id))
  const detail=await api('supplier','/api/srm/portal/quotation/invitations/'+current.id);assert.equal(detail.rfqId,current.id);assert.equal(detail.status,'SENT');assert.equal(detail.invitationStatus,'INVITED');assert.equal(detail.lines.length,2)
  await api('srmPortal','/api/srm/portal/quotation/invitations/'+current.id,'GET',undefined,404)
  const old=await api('procStaff',rfq(3));assert.equal(old.status,'CANCELLED')
  run.internal_before??=state.internal_before;run.internal_before_attempt=state.internal_before_attempt
  run.internal_after=await srmCounters();run.internal_success_deltas=Object.fromEntries(Object.keys(run.internal_before.counts).map(path=>[path,run.internal_after.counts[path]-run.internal_before.counts[path]]));persist()
  for(const count of Object.values(run.internal_success_deltas))assert.ok(count>0,'当前正式调用方的实际内部成功计数必须增加')
  return{deleted_draft_rfq:disposable.id,main_rfq:current.id,source_requisition:main.id,status:current.status,owner:215,invited_supplier:9,actual_portal_user:224,other_portal_user:229,old_rfq3_status:'CANCELLED',current_internal_network_deltas:run.internal_success_deltas,internal_single_get_still_not_proven:true}
 })
 run.result='PASS_THREE_REAL_PREPARATION_FUNCTION_CASES_PENDING_QUOTE_ORDER_RECEIPT_AND_FULL_REVIEW'
 }else if(mode==='quote-order'){
  assert.ok(state.completed['RFQ-01-RFQ-02-real-draft-crud-send-and-portal-scope'])
  await check('RFQ-01-real-portal-quote-versions-idempotency-and-nonempty-comparison',async()=>{
   const id=state.objects.rfq.id,invitationPath='/api/srm/portal/quotation/invitations/'+id
   const current=await api('procStaff',rfq(id));assert.equal(current.status,'SENT')
   const invitation=await api('supplier',invitationPath);assert.equal(invitation.currentQuotation,null);assert.equal(invitation.lines.length,2)
   state.valid_until??=dateAfterDays(10);persist();run.quote_internal_before=await srmCounters();persist()
   const first={requestId:'goal_20261022_proc_main_quote_v1',rfqId:id,version:0,validUntil:state.valid_until,
    lines:invitation.lines.map((row,index)=>({rfqLineId:row.rfqLineId,unitPrice:index===0?'29.990000':'11.250000',deliveryDays:7,remark:'Goal 本地虚构报价样本'}))}
   state.first_quote_body=first;persist()
   await command('other-portal-main-quote-denied','srmPortal','/api/srm/portal/quotation','POST',{...first,requestId:'goal_20261022_proc_main_quote_other'},404)
   await command('internal-admin-main-quote-denied','srmAdmin','/api/srm/portal/quotation','POST',first,403)
   await command('incomplete-main-quote-denied','supplier','/api/srm/portal/quotation','POST',{...first,requestId:'goal_20261022_proc_main_quote_missing',lines:first.lines.slice(0,1)},400)
   const quotation=await command('main-quote-first','supplier','/api/srm/portal/quotation','POST',first)
   assert.equal(quotation.supplierId,9);assert.equal(quotation.version,1);assert.equal(quotation.totalAmount,'112.4700')
   const replay=await api('supplier','/api/srm/portal/quotation','POST',first);assert.equal(replay.id,quotation.id);assert.equal(replay.version,1)
   await command('same-intent-different-content-denied','supplier','/api/srm/portal/quotation','POST',{...first,lines:first.lines.map(row=>({...row,deliveryDays:8}))},409)
   const second={...first,requestId:'goal_20261022_proc_main_quote_v2',version:1,lines:first.lines.map((row,index)=>index===0?{...row,unitPrice:'30.010000'}:row)}
   const updated=await command('main-quote-second','supplier','/api/srm/portal/quotation','POST',second)
   assert.equal(updated.id,quotation.id);assert.equal(updated.version,2);assert.equal(updated.totalAmount,'112.5300')
   assert.ok(updated.lines.every(row=>typeof row.unitPrice==='string'))
   state.objects.quotation=updated;state.second_quote_body=second;persist()
   const oldIntent=await api('supplier','/api/srm/portal/quotation','POST',first);assert.equal(oldIntent.id,updated.id);assert.equal(oldIntent.version,2)
   const comparisons=await api('procStaff',rfq(id)+'/comparison');assert.equal(comparisons.length,1);assert.equal(comparisons[0].id,updated.id);assert.equal(comparisons[0].version,2);assert.equal(comparisons[0].totalAmount,'112.5300')
   run.quote_internal_after=await srmCounters();const path='/api/internal/quotation/batch',delta=run.quote_internal_after.counts[path]-run.quote_internal_before.counts[path];assert.ok(delta>0);persist()
   return{rfq_id:id,supplier_id:9,portal_user:224,quotation_id:updated.id,current_version:2,first_total:'112.4700',final_total:'112.5300',real_current_internal_nonempty_quote_requests_added:delta}
  })
  await check('RFQ-02-real-award-and-PO-01-PO-02-edit-send-confirm-role-boundaries',async()=>{
   const id=state.objects.rfq.id,quotation=state.objects.quotation,current=await api('procStaff',rfq(id));assert.equal(current.status,'SENT')
   const terms={title:'Goal 20261022 procurement main order',deliveryAddress:'Goal local test warehouse',contactName:'Goal fixture receiver',contactPhone:'00000000000'}
   const award={rfqVersion:current.version,quotationId:quotation.id,quotationVersion:quotation.version,...terms};state.award_body=award;persist()
   await command('stale-main-quotation-award-denied','procStaff',rfq(id)+'/award','POST',{...award,quotationVersion:1},409)
   const awarded=await command('award-main-rfq','procStaff',rfq(id)+'/award','POST',award)
   assert.equal(awarded.rfq.status,'AWARDED');assert.equal(awarded.purchaseOrder.status,'DRAFT');assert.equal(awarded.purchaseOrder.ownerUserId,215);assert.equal(awarded.purchaseOrder.ownerUnitId,606)
   assert.equal(awarded.purchaseOrder.totalAmount,'112.5300');assert.equal(awarded.purchaseOrder.quotationId,quotation.id);assert.equal(awarded.purchaseOrder.quotationVersion,2)
   state.objects.rfq=awarded.rfq;let order=awarded.purchaseOrder;state.objects.order=order;persist()
   await command('repeated-award-state-conflict','procStaff',rfq(id)+'/award','POST',award,409)
   const orders=await api('procStaff','/api/procurement/purchase-order/list?page=1&size=100&rfqId='+id);assert.equal(orders.records.length,1);assert.equal(orders.records[0].id,order.id)
   const path='/api/procurement/purchase-order/'+order.id,edit={...terms,title:terms.title+' edited',expectedDeliveryDate:null,version:order.version}
   await command('stale-order-edit-denied','procStaff',path,'PUT',{...edit,version:order.version+1},409)
   order=await command('edit-main-order','procStaff',path,'PUT',edit);assert.equal(order.totalAmount,'112.5300')
   await api('procScopeManager',path);await api('procPeer',path,'GET',undefined,404)
   const outside=await api('procPeer','/api/procurement/purchase-order/list?page=1&size=100&rfqId='+id);assert.equal(outside.records.length,0)
   await command('portal-order-confirm-denied','supplier',path+'/confirm','POST',{version:order.version},403)
   order=await command('send-main-order','procStaff',path+'/send','POST',{version:order.version});assert.equal(order.status,'SENT')
   await command('sent-order-edit-denied','procStaff',path,'PUT',{...edit,version:order.version},409)
   order=await command('confirm-main-order','procStaff',path+'/confirm','POST',{version:order.version});assert.equal(order.status,'CONFIRMED');assert.equal(order.totalAmount,'112.5300')
   assert.equal(order.lines.length,2);assert.equal(order.ownerUserId,215);state.objects.order=order;persist()
   const preserved=await api('procStaff',rfq(3));assert.equal(preserved.status,'CANCELLED')
   return{rfq_id:id,rfq_status:'AWARDED',awarded_current_quotation:quotation.id,quotation_version:2,order_id:order.id,order_status:order.status,total_amount:order.totalAmount,owner:215,owner_unit:606,source_request:state.objects.requisition.id,unique_order_for_rfq:1,award_before_future_deadline_was_allowed_by_real_service:true,not_supplier_portal_confirmation:true}
  })
  run.result='PASS_REAL_CURRENT_QUOTE_VERSIONS_COMPARISON_AWARD_AND_ORDER_PENDING_RECEIPTS_AND_OTHER_BRANCHES'
 }else if(mode==='receive'){
  assert.ok(state.completed['RFQ-02-real-award-and-PO-01-PO-02-edit-send-confirm-role-boundaries'])
  await check('GR-01-GR-02-real-first-partial-receipt-no-draft-reservation',async()=>{
   const order=await api('procStaff',po(state.objects.order.id));assert.equal(order.status,'CONFIRMED');assert.equal(order.lines.length,2)
   const assetLine=order.lines.find(row=>row.materialId===config.objects.asset.id),supplyLine=order.lines.find(row=>row.materialId===config.objects.supply.id)
   assert.ok(assetLine&&supplyLine);assert.equal(assetLine.quantity,'3.000000');assert.equal(supplyLine.quantity,'2.000000')
   state.receipt_order_lines={asset:assetLine.id,supply:supplyLine.id};state.receipt_time??=dateAfterDays(0);persist()
   assert.equal((await assetCards()).length,0)
   const body={poId:order.id,receiveTime:state.receipt_time,remark:'Goal 主链A本人部分收货',lines:[{poLineId:assetLine.id,receivedQuantity:'1.000000',qualityStatus:'PASS',remark:'一件资产已通过'},{poLineId:supplyLine.id,receivedQuantity:'1.000000',qualityStatus:'PENDING',remark:'非资产待后续质检'}]}
   await command('peer-create-receipt-denied','procPeer','/api/procurement/goods-receipt','POST',body,404)
   await command('duplicate-receipt-line-denied','procStaff','/api/procurement/goods-receipt','POST',{...body,lines:[body.lines[0],body.lines[0]]},400)
   let first=await command('create-receipt-a','procStaff','/api/procurement/goods-receipt','POST',body);assert.equal(first.status,'DRAFT');assert.equal(first.ownerUserId,215)
   const unreserved=await api('procStaff',po(order.id));assert.ok(unreserved.lines.every(row=>row.receivedQuantity==='0.000000'))
   await command('stale-receipt-confirm-denied','procStaff',gr(first.id)+'/confirm','POST',{version:first.version+1},409)
   first=await command('confirm-receipt-a','procStaff',gr(first.id)+'/confirm','POST',{version:first.version});assert.equal(first.status,'CONFIRMED')
   state.objects.receiptA=first;persist();const partial=await api('procStaff',po(order.id));assert.equal(partial.status,'PARTIAL_RECEIVED')
   assert.equal(partial.lines.find(row=>row.id===assetLine.id).receivedQuantity,'1.000000')
   assert.equal(partial.lines.find(row=>row.id===supplyLine.id).receivedQuantity,'1.000000')
   const cards=await waitCards(1);assert.equal(cards[0].sourceGrId,first.id);assert.equal(cards[0].purchaseAmount,'30.01');assert.equal(typeof cards[0].id,'string')
   state.objects.asset_cards=cards.map(row=>({id:row.id,assetNo:row.assetNo,sourceGrId:row.sourceGrId,sourceGrLineId:row.sourceGrLineId,sourceUnitSequence:row.sourceUnitSequence,purchaseAmount:row.purchaseAmount,status:row.status}));persist()
   return{order:order.id,receipt:first.id,receiver:215,order_status:'PARTIAL_RECEIVED',draft_reserved_quantity:false,actual_initial_asset_card:cards[0].id,nonasset_pending_card_count:0}
  })
  await check('GR-02-real-owner-isolation-cumulative-overreceipt-rejection-and-full-delivery',async()=>{
   const id=state.objects.order.id,lines=state.receipt_order_lines,base={poId:id,receiveTime:state.receipt_time}
   let second=await command('create-receipt-b-manager','procManager','/api/procurement/goods-receipt','POST',{...base,remark:'Goal 主链B经理收货',lines:[{poLineId:lines.asset,receivedQuantity:'1.000000',qualityStatus:'PENDING'},{poLineId:lines.supply,receivedQuantity:'1.000000',qualityStatus:'FAIL'}]})
   assert.equal(second.ownerUserId,217);state.objects.receiptB=second;persist()
   const overlap=await command('create-overlapping-draft-c','procStaff','/api/procurement/goods-receipt','POST',{...base,remark:'Goal 主链C重叠草稿，确认必须按全范围累计拒绝',lines:[{poLineId:lines.asset,receivedQuantity:'2.000000',qualityStatus:'PASS'}]})
   state.objects.overlapDraft=overlap;persist()
   second=await command('confirm-receipt-b-manager','procManager',gr(second.id)+'/confirm','POST',{version:second.version});assert.equal(second.status,'CONFIRMED');state.objects.receiptB=second;persist()
   await api('procStaff',gr(second.id),'GET',undefined,404);await api('procScopeManager',gr(second.id))
   const aggregate=await api('procStaff',po(id));assert.equal(aggregate.status,'PARTIAL_RECEIVED');assert.equal(aggregate.lines.find(row=>row.id===lines.asset).receivedQuantity,'2.000000');assert.equal(aggregate.lines.find(row=>row.id===lines.supply).receivedQuantity,'2.000000')
   await command('confirm-overlap-c-rejected','procStaff',gr(overlap.id)+'/confirm','POST',{version:overlap.version},409)
   const unchanged=await api('procStaff',gr(overlap.id));assert.equal(unchanged.status,'DRAFT');assert.equal(unchanged.version,overlap.version)
   let final=await command('create-final-receipt-d','procStaff','/api/procurement/goods-receipt','POST',{...base,remark:'Goal 主链D剩余一件资产待质检',lines:[{poLineId:lines.asset,receivedQuantity:'1.000000',qualityStatus:'PENDING'}]})
   final=await command('confirm-final-receipt-d','procStaff',gr(final.id)+'/confirm','POST',{version:final.version});assert.equal(final.status,'CONFIRMED');state.objects.receiptD=final;persist()
   const received=await api('procStaff',po(id));assert.equal(received.status,'RECEIVED');assert.equal(received.lines.find(row=>row.id===lines.asset).receivedQuantity,'3.000000');assert.equal(received.lines.find(row=>row.id===lines.supply).receivedQuantity,'2.000000')
   state.objects.order=received;persist()
   const selfList=await api('procStaff','/api/procurement/goods-receipt/list?page=1&size=100&poId='+id);assert.ok(!selfList.records.some(row=>row.id===second.id));assert.ok(selfList.records.some(row=>row.id===state.objects.receiptA.id))
   await api('procPeer',gr(state.objects.receiptA.id),'GET',undefined,404)
   assert.equal((await assetCards()).length,1)
   return{order:id,status:'RECEIVED',manager_receipt:second.id,manager_receipt_hidden_from_self:215,all_confirmed_quantity_visible_in_order:true,overlap_draft:overlap.id,overlap_confirmation:409,overlap_real_retained_status:'DRAFT',final_receipt:final.id,pending_and_failed_lines_did_not_create_cards:true}
  })
  await check('GR-02-GR-04-real-late-quality-and-three-linked-asset-cards',async()=>{
   let first=await api('procStaff',gr(state.objects.receiptA.id)),second=await api('procManager',gr(state.objects.receiptB.id)),final=await api('procStaff',gr(state.objects.receiptD.id))
   const aAsset=first.lines.find(row=>row.materialId===config.objects.asset.id),aPending=first.lines.find(row=>row.materialId===config.objects.supply.id),bPending=second.lines.find(row=>row.materialId===config.objects.asset.id),bFailed=second.lines.find(row=>row.materialId===config.objects.supply.id),dPending=final.lines[0]
   assert.equal(aAsset.qualityStatus,'PASS');assert.equal(aPending.qualityStatus,'PENDING');assert.equal(bPending.qualityStatus,'PENDING');assert.equal(bFailed.qualityStatus,'FAIL');assert.equal(dPending.qualityStatus,'PENDING')
   await command('terminal-pass-quality-change-rejected','procStaff',gr(first.id)+'/quality-result','POST',{version:first.version,lines:[{goodsReceiptLineId:aAsset.id,qualityStatus:'FAIL'}]},409)
   await command('quality-wrong-parent-line-rejected','procStaff',gr(first.id)+'/quality-result','POST',{version:first.version,lines:[{goodsReceiptLineId:bPending.id,qualityStatus:'PASS'}]},404)
   await command('quality-duplicate-line-rejected','procStaff',gr(first.id)+'/quality-result','POST',{version:first.version,lines:[{goodsReceiptLineId:aPending.id,qualityStatus:'PASS'},{goodsReceiptLineId:aPending.id,qualityStatus:'PASS'}]},400)
   await command('quality-stale-version-rejected','procStaff',gr(first.id)+'/quality-result','POST',{version:first.version+1,lines:[{goodsReceiptLineId:aPending.id,qualityStatus:'PASS'}]},409)
   first=await command('pass-nonasset-quality-a','procStaff',gr(first.id)+'/quality-result','POST',{version:first.version,lines:[{goodsReceiptLineId:aPending.id,qualityStatus:'PASS'}]});state.objects.receiptA=first;persist();assert.equal((await assetCards()).length,1)
   await command('terminal-fail-quality-change-rejected','procManager',gr(second.id)+'/quality-result','POST',{version:second.version,lines:[{goodsReceiptLineId:bFailed.id,qualityStatus:'PASS'}]},409)
   second=await command('pass-asset-quality-b','procManager',gr(second.id)+'/quality-result','POST',{version:second.version,lines:[{goodsReceiptLineId:bPending.id,qualityStatus:'PASS'}]});state.objects.receiptB=second;persist();await waitCards(2)
   final=await command('pass-asset-quality-d','procStaff',gr(final.id)+'/quality-result','POST',{version:final.version,lines:[{goodsReceiptLineId:dPending.id,qualityStatus:'PASS'}]});state.objects.receiptD=final;persist()
   await command('repeat-terminal-quality-b-rejected','procManager',gr(second.id)+'/quality-result','POST',{version:second.version,lines:[{goodsReceiptLineId:bPending.id,qualityStatus:'PASS'}]},409)
   const cards=await waitCards(3)
   assert.equal(new Set(cards.map(row=>row.id)).size,3);assert.equal(new Set(cards.map(row=>`${row.sourceGrId}:${row.sourceGrLineId}:${row.sourceUnitSequence}`)).size,3)
   const expectedReceipts=new Set([first.id,second.id,final.id]);assert.deepEqual(new Set(cards.map(row=>row.sourceGrId)),expectedReceipts)
   for(const card of cards){assert.equal(typeof card.id,'string');assert.equal(typeof card.purchaseAmount,'string');assert.equal(card.purchaseAmount,'30.01');assert.equal(card.status,'IN_STOCK');assert.equal(card.sourcePoId,state.objects.order.id)}
   state.objects.asset_cards=cards.map(row=>({id:row.id,assetNo:row.assetNo,sourcePoId:row.sourcePoId,sourceGrId:row.sourceGrId,sourceGrLineId:row.sourceGrLineId,sourceUnitSequence:row.sourceUnitSequence,purchaseAmount:row.purchaseAmount,status:row.status}));persist()
   return{order:state.objects.order.id,actual_asset_cards:state.objects.asset_cards,nonasset_pass_created_no_card:true,terminal_fail_preserved:true,pending_assets_created_only_after_real_quality_update:true,not_a_deliberate_duplicate_mq_injection:true,retained_overlap_draft:state.objects.overlapDraft.id}
  })
  run.result='PASS_THREE_REAL_RECEIPT_CUMULATIVE_SCOPE_QUALITY_AND_ASSET_FUNCTION_CASES_NOT_FULL_PROCUREMENT'
 }else if(mode==='alternates'){
  assert.ok(state.completed['GR-02-GR-04-real-late-quality-and-three-linked-asset-cards'])
  for(const purpose of ['cancel','delete'])await check('PO-02-real-'+purpose+'-separate-order',async()=>{
   const title='Goal 20261022 procurement '+purpose+' RFQ',prefix='alternate-'+purpose
   assert.equal((await api('procStaff','/api/procurement/rfq/list?page=1&size=100&keyword='+encodeURIComponent(title))).records.length,0,'固定自然键已有对象，先只读核对，不再创建')
   const main=await api('procStaff',req(state.objects.requisition.id));assert.equal(main.status,'APPROVED')
   let current=await command(prefix+'-create-rfq','procStaff','/api/procurement/rfq','POST',{requisitionId:main.id,title,quotationDeadline:dateAfterDays(7),supplierIds:[9]})
   current=await command(prefix+'-send-rfq','procStaff',rfq(current.id)+'/send','POST',{version:current.version});assert.equal(current.status,'SENT')
   const invitation=await api('supplier','/api/srm/portal/quotation/invitations/'+current.id);assert.equal(invitation.currentQuotation,null);assert.equal(invitation.lines.length,2)
   const quotation=await command(prefix+'-quote','supplier','/api/srm/portal/quotation','POST',{requestId:'goal_20261022_proc_'+purpose+'_quote_v1',rfqId:current.id,version:0,validUntil:dateAfterDays(10),
    lines:invitation.lines.map((row,index)=>({rfqLineId:row.rfqLineId,unitPrice:index===0?'9.990000':'4.500000',deliveryDays:7,remark:'Goal 独立订单分支样本'}))})
   assert.equal(quotation.totalAmount,'38.9700');assert.equal(quotation.version,1)
   const awarded=await command(prefix+'-award','procStaff',rfq(current.id)+'/award','POST',{rfqVersion:current.version,quotationId:quotation.id,quotationVersion:quotation.version,title:'Goal 20261022 procurement '+purpose+' order',deliveryAddress:'Goal local test warehouse',contactName:'Goal fixture receiver',contactPhone:'00000000000'})
   let order=awarded.purchaseOrder;assert.equal(order.status,'DRAFT');assert.equal(order.ownerUserId,215);assert.equal(order.totalAmount,'38.9700')
   state.objects[purpose+'Rfq']=awarded.rfq;state.objects[purpose+'Quotation']={id:quotation.id,version:quotation.version,totalAmount:quotation.totalAmount};persist()
   if(purpose==='cancel'){
    order=await command(prefix+'-send-order','procStaff',po(order.id)+'/send','POST',{version:order.version});assert.equal(order.status,'SENT')
    order=await command(prefix+'-confirm-order','procStaff',po(order.id)+'/confirm','POST',{version:order.version});assert.equal(order.status,'CONFIRMED')
    await command(prefix+'-peer-cancel-denied','procPeer',po(order.id)+'/cancel','POST',{version:order.version},404)
    await command(prefix+'-stale-cancel-denied','procStaff',po(order.id)+'/cancel','POST',{version:order.version+1},409)
    order=await command(prefix+'-cancel-order','procStaff',po(order.id)+'/cancel','POST',{version:order.version});assert.equal(order.status,'CANCELLED')
    await command(prefix+'-repeat-cancel-denied','procStaff',po(order.id)+'/cancel','POST',{version:order.version},409)
    state.objects.cancelledOrder=order;persist()
    const receipts=await api('procStaff','/api/procurement/goods-receipt/list?page=1&size=100&poId='+order.id);assert.equal(receipts.records.length,0)
   }else{
    await command(prefix+'-peer-delete-denied','procPeer',po(order.id)+'?version='+order.version,'DELETE',undefined,404)
    await command(prefix+'-stale-delete-denied','procStaff',po(order.id)+'?version='+(order.version+1),'DELETE',undefined,409)
    await command(prefix+'-delete-order','procStaff',po(order.id)+'?version='+order.version,'DELETE')
    await api('procStaff',po(order.id),'GET',undefined,404)
    const orders=await api('procStaff','/api/procurement/purchase-order/list?page=1&size=100&rfqId='+current.id);assert.equal(orders.records.length,0)
    state.objects.deletedOrder={id:order.id,orderNo:order.orderNo,rfqId:current.id,statusBeforeDelete:'DRAFT',deletedByFormalApi:true};persist()
   }
   const preservedRfq=await api('procStaff',rfq(current.id));assert.equal(preservedRfq.status,'AWARDED')
   const preservedMain=await api('procStaff',po(state.objects.order.id));assert.equal(preservedMain.status,'RECEIVED');assert.equal(preservedMain.version,6);assert.equal(preservedMain.totalAmount,'112.5300')
   return{purpose,rfq_id:current.id,quotation_id:quotation.id,order_id:order.id,total_amount:'38.9700',final_order_status:purpose==='cancel'?'CANCELLED':'DELETED_BY_FORMAL_API',rfq_remains_awarded:true,main_order_preserved:{id:preservedMain.id,status:preservedMain.status,version:preservedMain.version}}
  })
  run.result='PASS_TWO_SEPARATE_REAL_CANCEL_AND_DELETE_BRANCHES_MAIN_RECEIVED_ORDER_PRESERVED'
 }else{
  assert.ok(state.completed['PO-02-real-cancel-separate-order']);assert.ok(state.completed['PO-02-real-delete-separate-order'])
  await check('OV-01-real-summary-spend-currency-and-three-data-scopes',async()=>{
   const snapshot={}
   for(const actor of ['procStaff','procScopeManager','procPeer']){
    const summary=await api(actor,'/api/procurement/overview/summary'),hasData=actor!=='procPeer'
    assert.equal(summary.pendingApprovalRequisitionCount,0);assert.equal(summary.waitingQuotationRfqCount,0);assert.equal(summary.draftGoodsReceiptCount,hasData?1:0)
    const counts=Object.fromEntries(summary.purchaseOrderStatusCounts.map(row=>[row.status,row.count]))
    assert.deepEqual(Object.keys(counts).sort(),['DRAFT','SENT','CONFIRMED','PARTIAL_RECEIVED','RECEIVED','CLOSED','CANCELLED'].sort())
    for(const status of Object.keys(counts))assert.equal(counts[status],hasData&&['RECEIVED','CANCELLED'].includes(status)?1:0,actor+' '+status)
    assert.deepEqual(summary.committedAmountsByCurrency,hasData?[{currencyCode:'CNY',amount:'112.5300'}]:[])
    const spend={}
    for(const [dimension,key]of Object.entries({CATEGORY:config.objects.leaf.categoryCode,SUPPLIER:'9',DEPARTMENT:'606'})){
     const rows=await api(actor,'/api/procurement/overview/spend-analysis?dimension='+dimension+'&limit=100');assert.equal(rows.length,hasData?1:0)
     if(hasData){assert.equal(rows[0].dimension,dimension);assert.equal(rows[0].dimensionKey,key);assert.equal(rows[0].currencyCode,'CNY');assert.equal(rows[0].amount,'112.5300');assert.equal(typeof rows[0].amount,'string');if(dimension==='DEPARTMENT')assert.equal(rows[0].dimensionName,'606')}
     spend[dimension]=rows
    }
    snapshot[actor]={user_id:local.state.accounts[actor].id,summary,spend}
   }
   await api('employee','/api/procurement/overview/summary','GET',undefined,403)
   await api('supplier','/api/procurement/overview/summary','GET',undefined,403)
   await api('procStaff','/api/procurement/overview/spend-analysis','GET',undefined,400)
   await api('procStaff','/api/procurement/overview/spend-analysis?dimension=CATEGORY&limit=0','GET',undefined,400)
   await api('procStaff','/api/procurement/overview/spend-analysis?dimension=SUPPLIER&limit=101','GET',undefined,400)
   const preserved=await api('procStaff',rfq(3));assert.equal(preserved.status,'CANCELLED')
   state.objects.overview_snapshot=snapshot;persist()
   return{snapshots:snapshot,excluded_cancelled_amount:'38.9700',excluded_deleted_amount:'38.9700',committed_not_cash_paid:true,department_current_name_is_id_text:true,old_rfq3_preserved:'CANCELLED'}
  })
  run.result='PASS_REAL_OVERVIEW_THREE_SCOPES_THREE_DIMENSIONS_AMOUNT_STRINGS_AND_ROLE_DENIALS'
 }
 run.finished_at=new Date().toISOString();run.raw_log_sha256=sha(readFileSync(raw));persist()
 console.log(JSON.stringify({result:run.result,cases:run.cases.length,requests:run.requests.length}))
}catch(error){run.result='FAIL_BUSINESS_PRESERVE_OBJECTS_AND_UNKNOWN_COMMAND_GUARDS';run.error=error.message;persist();throw error}
finally{writeFileSync(dir+'/source-used.mjs',readFileSync(new URL(import.meta.url)))}
