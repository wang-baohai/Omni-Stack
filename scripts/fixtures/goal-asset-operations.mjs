import assert from 'node:assert/strict'
import {assetCancellationModes,runAssetCancellation} from './goal-asset-cancellation.mjs'
import {assetRecoveryModes,runAssetRecovery} from './goal-asset-recovery.mjs'

// 原WP-28.S02的正式业务操作；阶段边界只用于保留真实截图，不新增计划步骤。
export const assetOperationModes=['transfer-create','transfer-approve','transfer-complete','discard-create','discard-approve','discard-complete','scrap-create','scrap-approve','scrap-complete','transfer-reject-create','transfer-reject-decide','disposal-reject-create','disposal-reject-decide',...assetCancellationModes,...assetRecoveryModes]

export async function runAssetOperation(mode,ctx){
 const {state,local,api,command,pass,persist,sameSourceCards}=ctx
 const life=state.objects.manualLife
 const path='/api/asset/transfer'
 const assetPath='/api/asset/asset/'+life.id
 assert(state.completed.return,'先完成原计划两种归还路径')
 assert(!state.completed[mode],'不得重放已完成业务阶段')
 if(assetCancellationModes.includes(mode)){await runAssetCancellation(mode,ctx);return}
 if(assetRecoveryModes.includes(mode)){await runAssetRecovery(mode,ctx);return}
 const save=(key,value)=>{state.objects[key]=value;persist();return value}
 const readAsset=async(status,version,operationId)=>{
  const row=await api('assetManager',assetPath)
  assert.equal(row.status,status);assert.equal(row.version,version)
  assert.equal(row.ownerUserId,223);assert.equal(row.ownerUnitId,606)
  assert.equal(row.activeOperationType,operationId?'TRANSFER':null)
  if(operationId)assert.equal(String(row.activeOperationId),String(operationId))
  else assert.equal(row.activeOperationId,null)
  state.objects.manualLife.current=row;persist();return row
 }
 const operation=async()=>{
  assert(state.objects.transferMain?.id,'只读取已登记申请')
  const row=await api('assetManager',path+'/'+state.objects.transferMain.id)
  assert.equal(row.assetId,life.id);assert(row.reason.includes(state.namespace))
  return row
 }
 const taskFor=async op=>{
  // 此笔申请在补齐230候选范围前已真实启动，保留原223快照；新申请另验230独立指派。
  const list=await api('assetManager','/api/workflow/task/todo?page=1&size=100')
  const rows=list.records.filter(row=>row.processInstanceId===op.processInstanceId)
  assert.equal(rows.length,1,'原申请必须保留其唯一已记录任务')
  assert.equal(rows[0].assigneeId,223);assert(rows[0].taskId)
  state.objects.transferMain.approver_alias='assetManager'
  state.objects.transferMain.approver_id=223
  state.objects.transferMain.candidate_snapshot_note='Created before owned230 scope configuration; original223 assignment retained. Formal delegation403 preserved; no permission expansion. Independent230 approval remains required on new applications.'
  persist()
  return rows[0]
 }
 const waitStatus=async expected=>{
  for(let count=0;count<45;count++){
   const row=await operation()
   if(row.status===expected)return row
   assert.equal(row.status,'PENDING_APPROVAL','非预期终态不得重试审批写入')
   await new Promise(resolve=>setTimeout(resolve,1000))
  }
  throw Error('审批消息尚未收敛，保留成功命令并只读定位，禁止重放')
 }
 if(mode==='transfer-create'){
  const body={assetId:life.id,toUserId:222,toUnitId:606,toLocation:'ROOM-B606',reason:state.namespace+' actual approved transfer'}
  if(!state.commands['transfer-main-create']?.complete)await readAsset('IN_STOCK',8)
  await command('transfer-self-no-create','assetUser',path,'POST',body,403)
  await command('transfer-cross-unit-no-write','assetManager',path,'POST',{...body,toUserId:231,toUnitId:605},403)
  if(!state.commands['transfer-main-create']?.complete){
   const existing=await api('assetManager',path+'/list?page=1&size=100&keyword='+encodeURIComponent(body.reason))
   assert.equal(existing.total,0,'不得收编不明同自然键申请')
  }
  const op=await command('transfer-main-create','assetManager',path,'POST',body)
  assert(op.id&&op.transferNo&&op.processInstanceId)
  assert.equal(op.status,'PENDING_APPROVAL');assert.equal(op.workflowStartStatus,'STARTED')
  assert.equal(op.previousAssetStatus,'IN_STOCK');assert.equal(op.toUserId,222);assert.equal(op.toUnitId,606)
  save('transferMain',{id:op.id,no:op.transferNo,create_result:op,current:op})
  const row=await readAsset('TRANSFER',9,op.id)
  assert.equal(row.currentUserId,null);assert.equal(row.currentUnitId,null)
  await command('transfer-pending-no-complete','assetManager',path+'/'+op.id+'/complete','POST',{version:op.version},409)
  await command('transfer-started-no-local-cancel','assetManager',path+'/'+op.id+'/cancel','POST',{version:op.version},409)
  await command('transfer-started-no-retry','assetManager',path+'/'+op.id+'/retry-start','POST',{version:op.version},409)
  await command('transfer-active-no-second-transfer','assetManager',path,'POST',body,409)
  await command('transfer-active-no-disposal','assetManager','/api/asset/disposal','POST',{assetId:life.id,disposalType:'SCRAP',reason:state.namespace+' occupied negative',residualValue:'0.00'},409)
  await command('transfer-active-no-allocate','assetManager',assetPath+'/allocate','POST',{version:9,targetUserId:222,targetUnitId:606},409)
  const peerList=await api('assetPeer',path+'/list?page=1&size=100&keyword='+encodeURIComponent(op.transferNo))
  assert.equal(peerList.total,0);await api('assetPeer',path+'/'+op.id,'GET',undefined,404)
  const task=await taskFor(op)
  const view=await api('assetManager',path+'/'+op.id+'/approval-view?taskId='+encodeURIComponent(task.taskId))
  assert.equal(view.id,op.id);assert.equal(view.processInstanceId,task.processInstanceId)
  await api('assetApprover',path+'/'+op.id+'/approval-view?taskId='+encodeURIComponent(task.taskId),'GET',undefined,403)
  await api('assetOperator',path+'/'+op.id+'/approval-view?taskId='+encodeURIComponent(task.taskId),'GET',undefined,403)
  state.objects.transferMain.task=task;persist()
  await sameSourceCards()
  pass('AST-09-AST-14-AST-16-real-transfer-started-with-preserved-original-task-and-atomic-occupancy',{operation:op,assigned_user:223,independent_approval_claimed:false,negative_codes:[403,403,409,409,409,409,409,409,404,403,403],asset_version:9,source_cards_unchanged:6})
 }
 if(mode==='transfer-approve'){
  assert(state.completed['transfer-create'])
  const before=state.objects.transferMain.create_result
  if(!state.commands['transfer-main-approve']?.complete){
   assert.equal((await operation()).status,'PENDING_APPROVAL')
   const task=await taskFor(before)
   assert.equal(task.taskId,state.objects.transferMain.task.taskId)
   await command('transfer-main-approve','assetManager','/api/workflow/approval/'+encodeURIComponent(task.taskId)+'/complete','POST',{approved:true,comment:state.namespace+' original assigned approver accepted transfer; no independent-review claim',variables:{}})
  }
  const approved=await waitStatus('APPROVED')
  assert.equal(approved.workflowStartStatus,'STARTED');assert(approved.approvedTime);assert.equal(approved.completedTime,null)
  assert(approved.version>before.version)
  const row=await readAsset('TRANSFER',9,approved.id);assert.equal(row.currentUserId,null)
  await command('transfer-approved-stale-version','assetManager',path+'/'+approved.id+'/complete','POST',{version:before.version},409)
  await command('transfer-approved-self-no-complete','assetUser',path+'/'+approved.id+'/complete','POST',{version:approved.version},403)
  const todo=await api('assetManager','/api/workflow/task/todo?page=1&size=100')
  assert(!todo.records.some(item=>item.processInstanceId===approved.processInstanceId))
  state.objects.transferMain.current=approved;persist();await sameSourceCards()
  pass('AST-09-approved-is-not-physical-handover-occupancy-retained',{operation:approved,asset_status:row.status,current_user:row.currentUserId,asset_version:row.version,stale_complete:409,self_complete:403,source_cards_unchanged:6})
 }
 if(mode==='transfer-complete'){
  assert(state.completed['transfer-approve'])
  const before=state.objects.transferMain.current
  if(!state.commands['transfer-main-complete']?.complete)assert.equal((await operation()).status,'APPROVED')
  const completed=await command('transfer-main-complete','assetManager',path+'/'+before.id+'/complete','POST',{version:before.version})
  assert.equal(completed.status,'COMPLETED');assert(completed.completedTime)
  const row=await readAsset('IN_USE',10)
  assert.equal(row.currentUserId,222);assert.equal(row.currentUnitId,606);assert.equal(row.locationCode,'ROOM-B606')
  const mine=await api('assetUser','/api/asset/asset/my?page=1&size=100')
  assert(mine.records.some(item=>item.id===life.id))
  await command('transfer-terminal-no-repeat-complete','assetManager',path+'/'+before.id+'/complete','POST',{version:completed.version},409)
  await command('transfer-terminal-no-cancel','assetManager',path+'/'+before.id+'/cancel','POST',{version:completed.version},409)
  state.objects.transferMain.current=completed;persist();await sameSourceCards()
  pass('AST-09-real-transfer-handover-updates-use-and-location-keeps-management-owner',{operation:completed,asset:row,current_user:222,current_unit:606,location:'ROOM-B606',owner_user:223,owner_unit:606,source_cards_unchanged:6})
 }
 if(mode.startsWith('discard-')||mode.startsWith('scrap-')){
  assert(state.completed['transfer-complete'])
  assert.equal(local.state.accounts.assetApprover.candidate_scope?.unit_id,606,'新申请前必须登记真实候选配置')
  const [type,action]=mode.split('-'),key=type==='discard'?'disposalDiscard':'disposalScrap'
  const owned=state.objects[type==='discard'?'manualDiscard':'manualScrap']
  const kindPath='/api/asset/disposal',ownedPath='/api/asset/asset/'+owned.id
  const body={assetId:owned.id,disposalType:type.toUpperCase(),reason:state.namespace+' actual '+type+' disposal',residualValue:type==='discard'?'1.50':'0.00',disposalMethod:'Controlled local recycling of an owned Goal sample'}
  const readOwned=async(status,version,id)=>{
   const row=await api('assetManager',ownedPath)
   assert.equal(row.status,status);assert.equal(row.version,version)
   assert.equal(row.ownerUserId,223);assert.equal(row.ownerUnitId,606)
   assert.equal(row.currentUserId,null);assert.equal(row.currentUnitId,null)
   assert.equal(row.activeOperationType,id?'DISPOSAL':null)
   if(id)assert.equal(String(row.activeOperationId),String(id));else assert.equal(row.activeOperationId,null)
   owned.current=row;persist();return row
  }
  const readOperation=async()=>{
   const op=await api('assetManager',kindPath+'/'+state.objects[key].id)
   assert.equal(op.assetId,owned.id);assert.equal(op.disposalType,type.toUpperCase());assert.equal(op.reason,body.reason)
   assert.equal(typeof op.residualValue,'string');assert.equal(op.residualValue,body.residualValue)
   return op
  }
  if(action==='create'){
   if(!state.commands[type+'-main-create']?.complete){
    const before=await readOwned('IN_STOCK',0)
    const existing=await api('assetManager',kindPath+'/list?page=1&size=100&keyword='+encodeURIComponent(body.reason));assert.equal(existing.total,0)
    state.objects[key]={source_asset_before:before};persist()
   }
   await command(type+'-self-no-create','assetUser',kindPath,'POST',body,403)
   const op=await command(type+'-main-create','assetManager',kindPath,'POST',body)
   assert(op.id&&op.disposalNo&&op.processInstanceId);assert.equal(op.status,'PENDING_APPROVAL');assert.equal(op.workflowStartStatus,'STARTED');assert.equal(op.previousAssetStatus,'IN_STOCK')
   state.objects[key]={...state.objects[key],id:op.id,no:op.disposalNo,current:op,create_result:op,approver_alias:'assetApprover',approver_id:230};persist()
   await readOwned('DISPOSAL_PENDING',1,op.id)
   const tasks=await api('assetApprover','/api/workflow/task/todo?page=1&size=100')
   const matching=tasks.records.filter(row=>row.processInstanceId===op.processInstanceId)
   assert.equal(matching.length,1);assert.equal(matching[0].assigneeId,230)
   const task=matching[0];state.objects[key].task=task;persist()
   const original=await api('assetManager','/api/workflow/task/todo?page=1&size=100')
   assert.equal(original.records.filter(row=>row.processInstanceId===op.processInstanceId).length,1,'ANY模型应保留两位实际候选各一任务')
   const view=await api('assetApprover',kindPath+'/'+op.id+'/approval-view?taskId='+encodeURIComponent(task.taskId));assert.equal(view.id,op.id)
   await api('assetOperator',kindPath+'/'+op.id+'/approval-view?taskId='+encodeURIComponent(task.taskId),'GET',undefined,403)
   await api('assetPeer',kindPath+'/'+op.id,'GET',undefined,404)
   await command(type+'-pending-no-complete','assetManager',kindPath+'/'+op.id+'/complete','POST',{version:op.version},409)
   await command(type+'-started-no-local-cancel','assetManager',kindPath+'/'+op.id+'/cancel','POST',{version:op.version},409)
   await command(type+'-active-no-duplicate','assetManager',kindPath,'POST',body,409)
   pass('AST-10-AST-14-real-'+type+'-request-independently-assigned-to230',{operation:op,independent_assignee:230,requester:223,actual_candidates:[223,230],asset_version:1,negative_codes:[403,403,404,409,409,409]})
  }
  if(action==='approve'){
   assert(state.completed[type+'-create'])
   const before=state.objects[key].create_result,task=state.objects[key].task
   if(!state.commands[type+'-main-approve']?.complete){
    assert.equal((await readOperation()).status,'PENDING_APPROVAL')
    const tasks=await api('assetApprover','/api/workflow/task/todo?page=1&size=100')
    assert(tasks.records.some(row=>row.taskId===task.taskId&&row.assigneeId===230&&row.processInstanceId===before.processInstanceId))
    await command(type+'-main-approve','assetApprover','/api/workflow/approval/'+encodeURIComponent(task.taskId)+'/complete','POST',{approved:true,comment:state.namespace+' independent230 approved '+type,variables:{}})
   }
   let approved
   for(let count=0;count<45;count++){
    const row=await readOperation();if(row.status==='APPROVED'){approved=row;break}
    assert.equal(row.status,'PENDING_APPROVAL');await new Promise(resolve=>setTimeout(resolve,1000))
   }
   assert(approved,'可靠消息尚未收敛，保留原审批成功命令，禁止重复审批')
   assert(approved.approvedTime);assert.equal(approved.completedTime,null)
   await readOwned('DISPOSAL_PENDING',1,approved.id)
   await command(type+'-approved-stale-complete','assetManager',kindPath+'/'+approved.id+'/complete','POST',{version:before.version},409)
   await command(type+'-approved-self-no-complete','assetUser',kindPath+'/'+approved.id+'/complete','POST',{version:approved.version},403)
   state.objects[key].current=approved;persist()
   pass('AST-10-real-independent-'+type+'-approval-keeps-asset-occupied-until-physical-completion',{operation:approved,independent_approver:230,asset_status:'DISPOSAL_PENDING',asset_version:1})
  }
  if(action==='complete'){
   assert(state.completed[type+'-approve'])
   const before=state.objects[key].current
   if(!state.commands[type+'-main-complete']?.complete)assert.equal((await readOperation()).status,'APPROVED')
   const completed=await command(type+'-main-complete','assetManager',kindPath+'/'+before.id+'/complete','POST',{version:before.version})
   assert.equal(completed.status,'COMPLETED');assert(completed.completedTime)
   const target=type==='discard'?'DISPOSED':'SCRAPPED',row=await readOwned(target,2)
   await command(type+'-terminal-no-repeat-complete','assetManager',kindPath+'/'+before.id+'/complete','POST',{version:completed.version},409)
   await command(type+'-terminal-no-cancel','assetManager',kindPath+'/'+before.id+'/cancel','POST',{version:completed.version},409)
   await command(type+'-terminal-no-allocate','assetManager',ownedPath+'/allocate','POST',{version:2,targetUserId:222,targetUnitId:606},409)
   const history=await api('assetManager',ownedPath+'/history?page=1&size=100')
   assert(history.records.some(item=>item.toStatus===target))
   state.objects[key].current=completed;state.objects[key].final_asset=row;persist()
   pass('AST-10-real-'+type+'-physical-completion-distinct-irreversible-terminal',{operation:completed,asset:row,history_records:history.total,terminal:target,source_cards_unchanged:6})
  }
  await sameSourceCards()
 }
 if(mode.startsWith('transfer-reject-')||mode.startsWith('disposal-reject-')){
  const kind=mode.startsWith('transfer-')?'transfer':'disposal',key=kind==='transfer'?'transferReject':'disposalReject'
  const beforeVersion=kind==='transfer'?10:12,phase=mode.endsWith('-create')?'create':'decide'
  assert(state.completed['transfer-complete'])
  if(kind==='disposal')assert(state.completed['transfer-reject-decide'])
  assert.equal(local.state.accounts.assetApprover.candidate_scope?.unit_id,606)
  const base='/api/asset/'+kind,cardPath='/api/asset/asset/'+life.id
  const body=kind==='transfer'?{assetId:life.id,toUserId:223,toUnitId:606,toLocation:'ROOM-C606',reason:state.namespace+' reject transfer and restore actual in-use state'}:{assetId:life.id,disposalType:'SCRAP',residualValue:'0.00',disposalMethod:'Proposed test disposal; rejection preserves the owned card',reason:state.namespace+' reject disposal and restore actual in-use state'}
  const read=async()=>{
   const op=await api('assetManager',base+'/'+state.objects[key].id)
   assert.equal(op.assetId,life.id);assert.equal(op.reason,body.reason);return op
  }
  if(phase==='create'){
   if(!state.commands[kind+'-reject-create']?.complete){
    const original=await api('assetManager',cardPath)
    assert.equal(original.status,'IN_USE');assert.equal(original.version,beforeVersion)
    assert.equal(original.currentUserId,222);assert.equal(original.currentUnitId,606);assert.equal(original.locationCode,'ROOM-B606')
    assert.equal(original.activeOperationType,null);assert.equal(original.activeOperationId,null)
    const found=await api('assetManager',base+'/list?page=1&size=100&keyword='+encodeURIComponent(body.reason));assert.equal(found.total,0)
    state.objects[key]={source_asset_before:original};persist()
   }
   const op=await command(kind+'-reject-create','assetManager',base,'POST',body)
   assert.equal(op.status,'PENDING_APPROVAL');assert.equal(op.workflowStartStatus,'STARTED');assert.equal(op.previousAssetStatus,'IN_USE');assert(op.processInstanceId)
   state.objects[key]={...state.objects[key],id:op.id,no:op.transferNo??op.disposalNo,current:op,create_result:op,approver_alias:'assetApprover',approver_id:230};persist()
   const occupied=await api('assetManager',cardPath)
   assert.equal(occupied.status,kind==='transfer'?'TRANSFER':'DISPOSAL_PENDING');assert.equal(occupied.version,beforeVersion+1)
   assert.equal(occupied.currentUserId,222);assert.equal(occupied.currentUnitId,606)
   assert.equal(occupied.activeOperationType,kind.toUpperCase());assert.equal(String(occupied.activeOperationId),String(op.id))
   const todo=await api('assetApprover','/api/workflow/task/todo?page=1&size=100')
   const assigned=todo.records.filter(row=>row.processInstanceId===op.processInstanceId)
   assert.equal(assigned.length,1);assert.equal(assigned[0].assigneeId,230)
   const task=assigned[0];state.objects[key].task=task;persist()
   const viewPath=base+'/'+op.id+'/approval-view?taskId='+encodeURIComponent(task.taskId)
   const view=await api('assetApprover',viewPath);assert.equal(view.id,op.id)
   await api('assetManager',viewPath,'GET',undefined,403)
   state.objects.manualLife.current=occupied;persist()
   pass('AST-15-real-'+kind+'-request-preserves-in-use-binding-and-independent230-task',{operation:op,asset:occupied,independent_assignee:230,requester:223,other_candidate_cannot_use230_task:403})
  }
  if(phase==='decide'){
   assert(state.completed[kind+'-reject-create'])
   const task=state.objects[key].task
   if(!state.commands[kind+'-reject-decision']?.complete){
    assert.equal((await read()).status,'PENDING_APPROVAL')
    const todo=await api('assetApprover','/api/workflow/task/todo?page=1&size=100')
    assert(todo.records.some(row=>row.taskId===task.taskId&&row.assigneeId===230))
    await command(kind+'-reject-decision','assetApprover','/api/workflow/approval/'+encodeURIComponent(task.taskId)+'/complete','POST',{approved:false,comment:state.namespace+' independent230 rejects proposed '+kind,variables:{}})
   }
   let rejected
   for(let count=0;count<45;count++){
    const op=await read();if(op.status==='REJECTED'){rejected=op;break}
    assert.equal(op.status,'PENDING_APPROVAL');await new Promise(resolve=>setTimeout(resolve,1000))
   }
   assert(rejected,'拒绝事件尚未收敛；不重放已成功的审批请求')
   const restored=await api('assetManager',cardPath),original=state.objects[key].source_asset_before
   assert.equal(restored.status,'IN_USE');assert.equal(restored.version,beforeVersion+2)
   assert.equal(restored.activeOperationType,null);assert.equal(restored.activeOperationId,null)
   for(const field of ['id','assetNo','name','purchaseAmount','currencyCode','ownerUserId','ownerUnitId','currentUserId','currentUnitId','allocatedTime','locationCode'])assert.equal(restored[field],original[field],'拒绝必须精确保留原资产字段：'+field)
   await command(kind+'-reject-terminal-no-complete','assetManager',base+'/'+rejected.id+'/complete','POST',{version:rejected.version},409)
   await command(kind+'-reject-terminal-no-retry','assetManager',base+'/'+rejected.id+'/retry-start','POST',{version:rejected.version},409)
   await command(kind+'-reject-terminal-no-local-cancel','assetManager',base+'/'+rejected.id+'/cancel','POST',{version:rejected.version},409)
   const mine=await api('assetUser','/api/asset/asset/my?page=1&size=100');assert(mine.records.some(row=>row.id===life.id))
   state.objects[key].current=rejected;state.objects[key].restored_asset=restored;state.objects.manualLife.current=restored;persist()
   pass('AST-15-real-independent-'+kind+'-rejection-restores-exact-previous-use-and-clears-occupancy',{operation:rejected,restored_asset:restored,independent_approver:230,original_status:'IN_USE',source_cards_unchanged:6})
  }
  await sameSourceCards()
 }
 state.completed[mode]=true;persist()
}
