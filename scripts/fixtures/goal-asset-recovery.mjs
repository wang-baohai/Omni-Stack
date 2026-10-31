import assert from 'node:assert/strict'
import fs from 'node:fs'

// 原S02的同申请重试验证；故障仅来自本次232账号的临时主部门，始终正式回退。
export const assetRecoveryModes=['transfer-recovery-create','transfer-recovery-retry','transfer-recovery-finish','disposal-recovery-create','disposal-recovery-retry','disposal-recovery-finish']

export async function runAssetRecovery(mode,ctx){
 const {state,local,api,command,pass,persist,sameSourceCards}=ctx
 assert(state.completed['recovery-restore'],'先撤销取消验证借用角色')
 assert.equal(local.state.accounts.assetOperator.id,232)
 assert.equal(local.state.accounts.assetOperator.username,'goal_20261028_assetoperator')
 assert.equal(local.state.organizations.work.id,606);assert.equal(local.state.organizations.b.id,605)
 const kind=mode.startsWith('transfer-')?'transfer':'disposal',key=kind==='transfer'?'transferRecovery':'disposalRecovery'
 const beforeVersion=kind==='transfer'?18:20,base='/api/asset/'+kind,life=state.objects.manualLife
 const assetPath='/api/asset/asset/'+life.id,userPath='/api/auth/user/232'
 const reason=state.namespace+' '+kind+' retry-owned232'
 const body=kind==='transfer'?{assetId:life.id,toUserId:223,toUnitId:606,toLocation:'ROOM-RECOVERY606',reason}:{assetId:life.id,disposalType:'SCRAP',residualValue:'0.00',disposalMethod:'Recovery test; reject after successful retry to preserve original binding',reason}
 if(kind==='disposal')assert(state.completed['transfer-recovery-finish'])
 const originalUser=await api('seedAdmin',userPath)
 assert.equal(originalUser.username,'goal_20261028_assetoperator');assert.equal(originalUser.tenantId,1)
 assert.equal(originalUser.status,1);assert.equal(originalUser.primaryUnitId,606)
 assert.deepEqual((await api('seedAdmin',userPath+'/roles')).map(Number),[40])
 if(mode.endsWith('-create')){
  const before=await api('assetManager',assetPath)
  assert.equal(before.status,'IN_USE');assert.equal(before.version,beforeVersion)
  assert.equal(before.currentUserId,222);assert.equal(before.currentUnitId,606);assert.equal(before.locationCode,'ROOM-B606')
  assert.equal(before.activeOperationType,null);assert.equal(before.activeOperationId,null)
  assert.equal((await api('assetManager',base+'/list?page=1&size=100&keyword='+encodeURIComponent(reason))).total,0)
  const setup={user_id:232,tenant_id:1,before_primary_unit:606,temporary_primary_unit:605,method:'formal-api-owned-test-identity',rollback:{method:'PUT',path:userPath,body:{primaryUnitId:606}},at:new Date().toISOString()}
  state.objects[key]={source_asset_before:before,configuration:setup,reason,request_body:body};persist()
  local.state.accounts.assetOperator.configuration_changes.push(setup);local.save()
  try{
   await command(kind+'-recovery-owned-unit-605','seedAdmin',userPath,'PUT',{primaryUnitId:605})
   assert.equal((await api('seedAdmin',userPath)).primaryUnitId,605)
   // 此轮首次业务登录在部门605配置之后，真实服务自行解析当前主部门及候选人。
   await command(kind+'-recovery-create-unknown','assetOperator',base,'POST',body,503)
  }finally{
   // 即使创建结果出乎预期，也先恢复专用账号，保留未知写入供只读核对。
   const actual=await api('seedAdmin',userPath)
   assert([605,606].includes(actual.primaryUnitId),'拒绝覆盖非本夹具修改')
   if(actual.primaryUnitId===605)await command(kind+'-recovery-owned-unit-restore606','seedAdmin',userPath,'PUT',{primaryUnitId:606})
   assert.equal((await api('seedAdmin',userPath)).primaryUnitId,606)
   setup.restored_at=new Date().toISOString();local.save();persist()
  }
  const rows=await api('assetManager',base+'/list?page=1&size=100&keyword='+encodeURIComponent(reason))
  assert.equal(rows.total,1,'只核对原自然键，不重新创建')
  const op=await api('assetManager',base+'/'+rows.records[0].id)
  assert.equal(op.status,'PENDING_APPROVAL');assert.equal(op.workflowStartStatus,'PENDING')
  assert.equal(op.processInstanceId,null);assert.equal(op.previousAssetStatus,'IN_USE')
  const occupied=await api('assetManager',assetPath)
  assert.equal(occupied.status,kind==='transfer'?'TRANSFER':'DISPOSAL_PENDING')
  assert.equal(occupied.version,beforeVersion+1);assert.equal(occupied.activeOperationType,kind.toUpperCase())
  assert.equal(String(occupied.activeOperationId),String(op.id))
  state.objects[key]={...state.objects[key],id:op.id,no:op.transferNo??op.disposalNo,current:op,unknown_result:op,initiator_id:232,initiator_alias:'assetOperator',approver_id:230,approver_alias:'assetApprover'}
  state.objects.manualLife.current=occupied;persist()
  await command(kind+'-recovery-unknown-cannot-cancel','assetManager',base+'/'+op.id+'/cancel','POST',{version:op.version},409)
  await command(kind+'-recovery-unknown-cannot-complete','assetManager',base+'/'+op.id+'/complete','POST',{version:op.version},409)
  pass('AST-15-real-'+kind+'-unknown-preserves-original-application-and-occupancy',{operation:op,asset:occupied,owned_configuration_restored:setup,local_cancel:409,physical_complete:409,business_state_direct_write:false})
 }else if(mode.endsWith('-retry')){
  assert(state.completed[kind+'-recovery-create'])
  const original=state.objects[key],path=base+'/'+original.id
  const before=await api('assetManager',path)
  assert.equal(before.workflowStartStatus,'PENDING');assert.equal(before.processInstanceId,null)
  await command(kind+'-recovery-self-retry-denied','assetUser',path+'/retry-start','POST',{version:before.version},403)
  const oldKey=kind+'-recovery-stale-retry-denied'
  if(state.commands[oldKey]&&!state.commands[oldKey].complete){
   // 仅核销原调拨r1已真实收到400的负例；不继承失败断言，不重复任何业务写。
   assert.equal(kind,'transfer');assert.equal(original.id,'6');assert.equal(before.version,0)
   assert.equal(before.processInstanceId,null);assert.equal(state.commands[oldKey].attempt,'asset-transfer-recovery-retry-r1')
   const failed=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/asset-transfer-recovery-retry-r1/result.json'))
   assert.equal(failed.result,'FAIL_PRESERVE_CURRENT_OBJECTS_AND_UNCERTAIN_COMMANDS')
   const actual=failed.requests.find(row=>row.method==='POST'&&row.path===path+'/retry-start'&&row.body?.version===-1)
   assert.equal(actual?.business_code,400)
   Object.assign(state.commands[oldKey],{complete:true,result:null,reconciled_at:new Date().toISOString(),actual_business_code:400,reconciliation:'原version=-1被DTO @Min(0)拒绝；原失败日志保留；当前GET仍原ID/version0/PENDING/无实例，未发生重试启动'})
   persist();pass('AST-15-original-negative-version-rejection-preserved-not-a-409-claim',{operation_id:original.id,actual_code:400,unchanged_version:0})
  }
  await command(kind+'-recovery-mismatched-version-retry-denied','assetOperator',path+'/retry-start','POST',{version:before.version+1},409)
  const started=await command(kind+'-recovery-original-retry','assetOperator',path+'/retry-start','POST',{version:before.version})
  assert.equal(started.id,original.id);assert.equal(started.assetId,life.id);assert.equal(started.reason,reason)
  assert.equal(started.status,'PENDING_APPROVAL');assert.equal(started.workflowStartStatus,'STARTED');assert(started.processInstanceId)
  const rows=(await api('assetApprover','/api/workflow/task/todo?page=1&size=100')).records.filter(row=>row.processInstanceId===started.processInstanceId)
  assert.equal(rows.length,1);assert.equal(rows[0].assigneeId,230)
  const occupied=await api('assetManager',assetPath)
  assert.equal(occupied.version,beforeVersion+1);assert.equal(String(occupied.activeOperationId),String(original.id))
  const sameNaturalKey=await api('assetManager',base+'/list?page=1&size=100&keyword='+encodeURIComponent(reason))
  assert.equal(sameNaturalKey.total,1)
  original.current=started;original.task=rows[0];original.retry_result=started;state.objects.manualLife.current=occupied;persist()
  pass('AST-15-real-'+kind+'-original-application-retry-started-with-independent-230-task',{before,after:started,task:rows[0],same_application_count:1,asset_version_unchanged:occupied.version,configuration_primary_unit_restored:606})
 }else{
  assert(state.completed[kind+'-recovery-retry'])
  const original=state.objects[key],path=base+'/'+original.id
  if(!state.commands[kind+'-recovery-real-approval-reject']?.complete){
   const before=await api('assetManager',path)
   assert.equal(before.status,'PENDING_APPROVAL');assert.equal(before.processInstanceId,original.retry_result.processInstanceId)
   const rows=(await api('assetApprover','/api/workflow/task/todo?page=1&size=100')).records.filter(row=>row.processInstanceId===before.processInstanceId)
   assert.equal(rows.length,1);assert.equal(rows[0].taskId,original.task.taskId);assert.equal(rows[0].assigneeId,230)
   const wrongKey=kind+'-recovery-independent-reject'
   if(state.commands[wrongKey]&&!state.commands[wrongKey].complete){
    assert.equal(kind,'transfer');assert.equal(state.commands[wrongKey].attempt,'asset-transfer-recovery-finish-r1')
    const failed=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/asset-transfer-recovery-finish-r1/result.json'))
    const actual=failed.requests.find(row=>row.method==='POST'&&row.path==='/api/workflow/task/'+original.task.taskId+'/approve')
    assert.equal(actual?.business_code,404)
    Object.assign(state.commands[wrongKey],{complete:true,result:null,reconciled_at:new Date().toISOString(),actual_business_code:404,reconciliation:'错误接口路径404；真实待办仍原230任务，未审批；原失败不改为成功'})
    persist()
   }
   await command(kind+'-recovery-real-approval-reject','assetApprover','/api/workflow/approval/'+encodeURIComponent(original.task.taskId)+'/complete','POST',{approved:false,comment:state.namespace+' real independent rejection after successful same-application retry; keep original asset binding',variables:{}})
  }
  let rejected
  for(let count=0;count<45;count++){
   const row=await api('assetManager',path)
   if(row.status==='REJECTED'){rejected=row;break}
   assert.equal(row.status,'PENDING_APPROVAL');await new Promise(resolve=>setTimeout(resolve,1000))
  }
  assert(rejected,'保留成功审批命令，等待真实事件，不重复审批')
  const restored=await api('assetManager',assetPath)
  assert.equal(restored.status,'IN_USE');assert.equal(restored.version,beforeVersion+2)
  assert.equal(restored.activeOperationType,null);assert.equal(restored.activeOperationId,null)
  for(const field of ['id','assetNo','name','purchaseAmount','currencyCode','ownerUserId','ownerUnitId','currentUserId','currentUnitId','allocatedTime','locationCode'])assert.equal(restored[field],original.source_asset_before[field],field)
  const todo=await api('assetApprover','/api/workflow/task/todo?page=1&size=100')
  assert(!todo.records.some(row=>row.processInstanceId===rejected.processInstanceId))
  original.current=rejected;original.restored_asset=restored;state.objects.manualLife.current=restored;persist()
  pass('AST-15-real-'+kind+'-recovery-finished-by-independent-rejection',{operation:rejected,asset:restored,existing_business_history_preserved:true,owned_primary_unit:606,owned_role_ids:[40],not_a_start_failed_local_cancel_claim:true})
 }
 await sameSourceCards();state.completed[mode]=true;persist()
}
