import assert from 'node:assert/strict'

// 原S02的公开流程终止路径；仅使用登记账号、既有角色和正式业务接口。
export const assetCancellationModes=['recovery-prepare','transfer-cancel-create','transfer-cancel-decide','disposal-cancel-create','disposal-cancel-decide','recovery-restore']

export async function runAssetCancellation(mode,ctx){
 const {state,local,api,command,pass,persist,sameSourceCards}=ctx
 assert.equal(local.state.accounts.assetOperator.id,232)
 assert.equal(local.state.accounts.assetOperator.username,'goal_20261028_assetoperator')
 const rolesPath='/api/auth/user/232/roles',userPath='/api/auth/user/232'
 const permissions=account=>Array.isArray(account.actual_permissions)?account.actual_permissions:String(account.actual_permissions??'').split(/\s+/)
 if(mode==='recovery-prepare'){
  assert(state.completed['scrap-complete']&&state.completed['disposal-reject-decide'])
  const user=await api('seedAdmin',userPath)
  assert.equal(user.tenantId,1);assert.equal(user.status,1);assert.equal(user.primaryUnitId,606)
  assert.equal(user.username,'goal_20261028_assetoperator')
  const roles=await api('seedAdmin','/api/auth/role/all')
  assert(roles.some(row=>row.id===10&&row.roleCode==='EMPLOYEE'&&row.status===1))
  assert(roles.some(row=>row.id===40&&row.roleCode==='ASSET_ADMIN'&&row.status===1))
  const before=(await api('seedAdmin',rolesPath)).map(Number).sort((a,b)=>a-b)
  if(!state.recovery_configuration){
   assert.deepEqual(before,[40])
   state.recovery_configuration={user_id:232,tenant_id:1,username:user.username,before_role_ids:before,temporary_existing_role_id:10,after_role_ids:[10,40],original_primary_unit:606,at:new Date().toISOString(),rollback:{method:'POST',path:rolesPath,body:[40],note:'仅恢复本次专用232原角色；保留真实业务审计历史'}}
   persist()
  }else assert(before.every(id=>[10,40].includes(id))&&before.includes(40))
  await command('asset-recovery-temporary-existing-employee-role','seedAdmin',rolesPath,'POST',[10,40])
  assert.deepEqual((await api('seedAdmin',rolesPath)).map(Number).sort((a,b)=>a-b),[10,40])
  await local.login('assetOperator')
  assert.deepEqual([...local.state.accounts.assetOperator.actual_roles].sort(),['ASSET_ADMIN','EMPLOYEE'])
  assert(permissions(local.state.accounts.assetOperator).includes('workflow:instance:terminate'))
  local.state.accounts.assetOperator.role_ids=[10,40]
  local.state.accounts.assetOperator.configuration_changes.push({at:new Date().toISOString(),method:'formal-api-owned-test-identity',purpose:'原S02本人流程终止与恢复验证',before:{role_ids:[40]},after:{role_ids:[10,40]},rollback:state.recovery_configuration.rollback})
  local.save();state.recovery_configuration.actual_terminate_permission=true;persist()
  pass('AST-15-minimal-existing-employee-role-only-on-owned232-with-exact-rollback',{configuration:state.recovery_configuration,shared_role_modified:false})
 }
 if(mode==='recovery-restore'){
  assert(state.completed['transfer-cancel-decide']&&state.completed['disposal-cancel-decide'])
  assert.deepEqual(state.recovery_configuration.before_role_ids,[40])
  assert.deepEqual((await api('seedAdmin',rolesPath)).map(Number).sort((a,b)=>a-b),[10,40])
  await command('asset-recovery-restore-original-roles','seedAdmin',rolesPath,'POST',[40])
  assert.deepEqual((await api('seedAdmin',rolesPath)).map(Number),[40])
  await local.login('assetOperator');assert.deepEqual(local.state.accounts.assetOperator.actual_roles,['ASSET_ADMIN'])
  assert(!permissions(local.state.accounts.assetOperator).includes('workflow:instance:terminate'))
  local.state.accounts.assetOperator.role_ids=[40];local.save()
  state.recovery_configuration.restored_at=new Date().toISOString();persist()
  pass('AST-15-owned232-original-role-set-restored-without-deleting-business-history',{user_id:232,role_ids:[40],temporary_role_removed:true})
 }
 if(mode.includes('-cancel-')){
  assert(state.completed['recovery-prepare'])
  const kind=mode.startsWith('transfer-')?'transfer':'disposal',key=kind==='transfer'?'transferCancel':'disposalCancel'
  const beforeVersion=kind==='transfer'?14:16,phase=mode.endsWith('-create')?'create':'decide'
  if(kind==='disposal')assert(state.completed['transfer-cancel-decide'])
  const life=state.objects.manualLife,assetPath='/api/asset/asset/'+life.id,base='/api/asset/'+kind
  const reason=state.namespace+' real initiator cancels '+kind+' through public workflow endpoint'
  const body=kind==='transfer'?{assetId:life.id,toUserId:223,toUnitId:606,toLocation:'ROOM-C606',reason}:{assetId:life.id,disposalType:'SCRAP',residualValue:'0.00',disposalMethod:'Cancelled proposal; owned in-use asset must remain unchanged',reason}
  if(phase==='create'){
   if(!state.commands[kind+'-cancel-create']?.complete){
    const before=await api('assetOperator',assetPath)
    assert.equal(before.status,'IN_USE');assert.equal(before.version,beforeVersion)
    assert.equal(before.currentUserId,222);assert.equal(before.currentUnitId,606);assert.equal(before.locationCode,'ROOM-B606')
    assert.equal(before.activeOperationType,null);assert.equal(before.activeOperationId,null)
    assert.equal((await api('assetOperator',base+'/list?page=1&size=100&keyword='+encodeURIComponent(reason))).total,0)
    state.objects[key]={source_asset_before:before};persist()
   }
   const op=await command(kind+'-cancel-create','assetOperator',base,'POST',body)
   assert.equal(op.status,'PENDING_APPROVAL');assert.equal(op.workflowStartStatus,'STARTED');assert.equal(op.previousAssetStatus,'IN_USE');assert(op.processInstanceId)
   state.objects[key]={...state.objects[key],id:op.id,no:op.transferNo??op.disposalNo,current:op,create_result:op,initiator_alias:'assetOperator',initiator_id:232,approver_alias:'assetApprover',approver_id:230};persist()
   const occupied=await api('assetManager',assetPath)
   assert.equal(occupied.status,kind==='transfer'?'TRANSFER':'DISPOSAL_PENDING');assert.equal(occupied.version,beforeVersion+1)
   assert.equal(occupied.currentUserId,222);assert.equal(occupied.currentUnitId,606)
   assert.equal(occupied.activeOperationType,kind.toUpperCase());assert.equal(String(occupied.activeOperationId),String(op.id))
   const tasks=(await api('assetApprover','/api/workflow/task/todo?page=1&size=100')).records.filter(row=>row.processInstanceId===op.processInstanceId)
   assert.equal(tasks.length,1);assert.equal(tasks[0].assigneeId,230)
   state.objects[key].task=tasks[0];state.objects.manualLife.current=occupied;persist()
   const own=await api('assetOperator','/api/workflow/process-instance/my-initiated?page=1&size=100')
   assert(own.records.some(row=>row.processInstanceId===op.processInstanceId&&row.startUserId===232&&row.status===1))
   await command(kind+'-started-cancel-must-not-use-local-failure-path','assetOperator',base+'/'+op.id+'/cancel','POST',{version:op.version},409)
   pass('AST-15-real-'+kind+'-owned232-process-and-original-in-use-binding-before-cancel',{operation:op,asset:occupied,initiator:232,assignee:230,local_cancel_on_started:409})
  }else{
   assert(state.completed[kind+'-cancel-create'])
   const original=state.objects[key],opPath=base+'/'+original.id
   const terminate='/api/workflow/process-instance/'+encodeURIComponent(original.current.processInstanceId)+'/terminate?reason='+encodeURIComponent(reason)
   if(!state.commands[kind+'-public-initiator-terminate']?.complete){
    assert.equal((await api('assetOperator',opPath)).status,'PENDING_APPROVAL')
    await local.login('employee');assert(permissions(local.state.accounts.employee).includes('workflow:instance:terminate'))
    await command(kind+'-public-terminate-wrong-initiator','employee',terminate,'PUT',undefined,403)
    await command(kind+'-public-initiator-terminate','assetOperator',terminate,'PUT')
   }
   let cancelled
   for(let count=0;count<45;count++){
    const row=await api('assetManager',opPath)
    if(row.status==='CANCELLED'){cancelled=row;break}
    assert.equal(row.status,'PENDING_APPROVAL');await new Promise(resolve=>setTimeout(resolve,1000))
   }
   assert(cancelled,'真实终止事件尚未收敛，保留成功命令，不重复终止')
   const restored=await api('assetManager',assetPath)
   assert.equal(restored.status,'IN_USE');assert.equal(restored.version,beforeVersion+2)
   assert.equal(restored.activeOperationType,null);assert.equal(restored.activeOperationId,null)
   for(const field of ['id','assetNo','name','purchaseAmount','currencyCode','ownerUserId','ownerUnitId','currentUserId','currentUnitId','allocatedTime','locationCode'])assert.equal(restored[field],original.source_asset_before[field],'取消必须精确保留原字段：'+field)
   const tasks=await api('assetApprover','/api/workflow/task/todo?page=1&size=100')
   assert(!tasks.records.some(row=>row.processInstanceId===cancelled.processInstanceId))
   const mine=await api('assetOperator','/api/workflow/process-instance/my-initiated?page=1&size=100')
   assert(mine.records.some(row=>row.processInstanceId===cancelled.processInstanceId&&row.status===0))
   await command(kind+'-cancelled-no-physical-complete','assetManager',opPath+'/complete','POST',{version:cancelled.version},409)
   await command(kind+'-cancelled-no-retry','assetManager',opPath+'/retry-start','POST',{version:cancelled.version},409)
   await command(kind+'-cancelled-no-local-repeat','assetManager',opPath+'/cancel','POST',{version:cancelled.version},409)
   state.objects[key].current=cancelled;state.objects[key].restored_asset=restored;state.objects.manualLife.current=restored;persist()
   pass('AST-15-real-public-'+kind+'-cancellation-restores-exact-in-use-state',{operation:cancelled,asset:restored,initiator:232,wrong_initiator_with_permission:403,real_workflow_status:0,database_business_state_modified:false})
  }
 }
 await sameSourceCards();state.completed[mode]=true;persist()
}
