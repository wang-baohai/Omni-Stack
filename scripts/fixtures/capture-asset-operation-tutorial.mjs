import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import {stripTypeScriptTypes} from 'node:module'
import {createContext,SourceTextModule} from 'node:vm'
import {dirname,resolve} from 'node:path'
import {chromium} from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import {createLocalContext,privateRoot} from './goal-local-identities.mjs'

// 原WP-28.S02真实调拨/处置截图；所有业务写请求拦截，确认框仅预览后取消。
assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(),'/validation/source')
const [kind,key,phase,attempt]=process.argv.slice(2)
assert(['transfer','disposal'].includes(kind))
assert(['transferMain','transferReject','transferCancel','transferRecovery','disposalDiscard','disposalScrap','disposalReject','disposalCancel','disposalRecovery'].includes(key))
const states={pending:'PENDING_APPROVAL',approved:'APPROVED',completed:'COMPLETED',rejected:'REJECTED',cancelled:'CANCELLED',failed:'START_FAILED',unknown:'PENDING_APPROVAL'}
assert(Object.hasOwn(states,phase));assert.match(attempt??'',new RegExp('^asset-'+key+'-'+phase+'-images-r[1-9][0-9]*$'))
const imageAttempt=process.argv[6]??'asset-layout-image-r4'
assert.match(imageAttempt,/^asset-layout-image-r[1-9][0-9]*$/)
const imageProof=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/'+imageAttempt+'/result.json'))
assert.equal(imageProof.result,'BUILT_NOT_DEPLOYED');assert.equal(process.env.E2E_RUNTIME_IMAGE_ID,imageProof.image_id)
const state=JSON.parse(fs.readFileSync(privateRoot+'/asset-scenarios.json')),op=state.objects[key]
assert.equal(state.namespace,'GOAL20261028ASSET');assert(op?.id&&op.current)
assert.equal(op.current.status,states[phase],'禁止重建已过去的中间态拍照')
const local=createLocalContext(),out='/validation/evidence/codex-2026-10-22/wp28-s02/'+attempt
const sha=value=>crypto.createHash('sha256').update(value).digest('hex')
assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/source-used.mjs',fs.readFileSync(new URL(import.meta.url)))
const raw=privateRoot+'/'+attempt+'-wire.ndjson';fs.writeFileSync(raw,'',{flag:'wx',mode:0o600})
const report={original_step:'WP-28.S02',kind,key,phase,attempt,started_at:new Date().toISOString(),workers:1,frontend_image:process.env.E2E_RUNTIME_IMAGE_ID,identity_source:'test-fixture',human_captcha_login_claimed:false,business_write_performed:false,dom_content_modified:false,response_mocked:false,operation_id:op.id,operation_no:op.no,expected_status:states[phase],images:[],api_reads:[],page_errors:[],blocked_mutations:[],visual_review:'pending',global_release_claimed:false}
const save=()=>fs.writeFileSync(out+'/result.json',JSON.stringify(report,null,2)+'\n')
const tokens={},manager='assetManager',approver=op.approver_alias??'assetApprover'
for(const actor of new Set([manager,approver]))tokens[actor]=await local.login(actor)
const baseline=await fetch('http://omni-gateway:8080/api/asset/'+kind+'/'+op.id,{headers:{Authorization:'Bearer '+tokens[manager]},signal:AbortSignal.timeout(30000)})
const checked=await baseline.json();assert.equal(checked.code,200);assert.equal(checked.data.status,states[phase]);assert.equal(checked.data.version,op.current.version)
report.current_operation=checked.data;report.baseline_sha256=sha(JSON.stringify(checked));save()
const locales=['zh-CN','en-US','ja-JP','ko-KR'],modules=new Map(),vm=createContext({})
async function dictionary(locale){
 const root='/source/omni-frontend/src/locales'
 const compile=file=>{assert(locales.some(value=>file===root+'/'+value+'.ts'));if(!modules.has(file))modules.set(file,new SourceTextModule(stripTypeScriptTypes(fs.readFileSync(file,'utf8')),{context:vm,identifier:file}));return modules.get(file)}
 const mod=compile(root+'/'+locale+'.ts')
 if(mod.status==='unlinked')await mod.link((specifier,parent)=>{assert.match(specifier,/^\.\/(zh-CN|en-US|ja-JP|ko-KR)(\.ts)?$/);return compile(resolve(dirname(parent.identifier),specifier.endsWith('.ts')?specifier:specifier+'.ts'))})
 if(mod.status==='linked')await mod.evaluate()
 return mod.namespace.default
}
const browser=await chromium.launch({headless:true})
try{
 for(const locale of locales){
  const dict=await dictionary(locale)
  const context=await browser.newContext({viewport:{width:1440,height:900},locale,timezoneId:'Asia/Shanghai',colorScheme:'light'});context.setDefaultTimeout(25000)
  let actor=manager
  await context.route('**/api/**',route=>{if(['GET','HEAD','OPTIONS'].includes(route.request().method()))return route.continue();report.blocked_mutations.push({actor,locale,path:new URL(route.request().url()).pathname,method:route.request().method()});save();return route.abort()})
  await context.addInitScript(({token,username,locale})=>{if(location.origin!=='http://omni-frontend:3000')return;localStorage.setItem('token',token);localStorage.setItem('username',username);localStorage.setItem('omni-lang',locale);localStorage.setItem('omni-theme','light')},{token:tokens[actor],username:local.state.accounts[actor].username,locale})
  const page=await context.newPage(),pending=[]
  page.on('pageerror',error=>{report.page_errors.push({actor,locale,message:error.message});save()})
  page.on('response',response=>{const url=new URL(response.url());if(!url.pathname.startsWith('/api/'))return;pending.push((async()=>{const json=await response.json();fs.appendFileSync(raw,JSON.stringify({actor,locale,path:url.pathname+url.search,http:response.status(),response:json})+'\n');report.api_reads.push({actor,locale,path:url.pathname+url.search,http:response.status(),code:json.code});save()})())})
  async function settled(){await page.waitForLoadState('networkidle');for(const loader of await page.locator('.el-loading-mask').all())await loader.waitFor({state:'hidden'});await Promise.all(pending);await page.evaluate(()=>document.fonts.ready)}
  async function animation(locator){await locator.evaluate(async node=>{await Promise.all((node.closest('.el-overlay')??node).getAnimations({subtree:true}).map(item=>item.finished.catch(()=>{})))})}
  async function shot(scene,purpose){
   await settled();assert(!/\b(?:assetTransfer|assetDisposal|workspaceApproval|common)\.[A-Za-z][A-Za-z0-9_.]+/.test(await page.locator('body').innerText()))
   const filename='asset-op-'+key+'-'+phase+'-'+scene+'-'+locale+'.png'
   const bytes=await page.screenshot({path:out+'/'+filename,fullPage:false,animations:'allow'})
   assert.equal(bytes.readUInt32BE(16),1440);assert.equal(bytes.readUInt32BE(20),900)
   report.images.push({scene,locale,actor,user_id:local.state.accounts[actor].id,filename,route:new URL(page.url()).pathname,sha256:sha(bytes),width:1440,height:900,captured_at:new Date().toISOString(),purpose,visual_review:'pending'});save()
  }
  await page.goto('http://omni-frontend:3000/admin/asset/'+kind,{waitUntil:'networkidle'});assert(!/login|403|menu-load-error/.test(page.url()))
  const panel=page.locator('.asset-operation-page');await panel.waitFor();await settled()
  await panel.locator('.el-form').first().locator('input').first().fill(op.no)
  await panel.getByRole('button',{name:dict.common.search,exact:true}).click();await settled()
  const row=panel.locator('.el-table__body-wrapper tbody tr');assert.equal(await row.count(),1)
  const expected=dict.assetDisposal[{PENDING_APPROVAL:'statusPending',APPROVED:'statusApproved',COMPLETED:'statusCompleted',REJECTED:'statusRejected',CANCELLED:'statusCancelled',START_FAILED:'statusStartFailed'}[states[phase]]]
  const statusTag=row.locator('.el-tag').filter({hasText:expected})
  assert.equal(await statusTag.count(),1);assert.equal((await statusTag.innerText()).trim(),expected)
  const wrap=panel.locator('.el-table__body-wrapper .el-scrollbar__wrap')
  const scroll=await wrap.evaluate(node=>({width:node.scrollWidth,client:node.clientWidth}))
  if(scroll.width>scroll.client){await wrap.hover();await page.mouse.wheel(3000,0);await page.waitForFunction(()=>{const x=document.querySelector('.asset-operation-page .el-table__body-wrapper .el-scrollbar__wrap');return x&&x.scrollLeft>=x.scrollWidth-x.clientWidth-1})}
  const tag=await statusTag.boundingBox(),actions=await row.locator('.fixed-actions-col').boundingBox()
  report.horizontal_geometry??=[];report.horizontal_geometry.push({locale,tag,actions,scroll:await wrap.evaluate(node=>({left:node.scrollLeft,width:node.scrollWidth,client:node.clientWidth}))});save()
  assert(tag&&actions&&tag.x+tag.width<=actions.x,'实际原生横滚后审批状态仍不得被固定操作列遮挡')
  await shot('list','Actual current operation filtered by its exact number; translated status and commands after native horizontal scroll')
  await row.getByRole('button',{name:dict.assetDisposal.details,exact:true}).click()
  const drawer=page.locator('.el-drawer:visible');await drawer.getByText(op.no,{exact:true}).waitFor();await animation(drawer)
  const box=await drawer.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=1440.5)
  await shot('detail','Actual formal operation, previous state, target, location and workflow reference; raw persisted state/free text are not falsely called localized')
  await drawer.locator('button.el-drawer__close-btn').click();await drawer.waitFor({state:'hidden'})
  if(phase==='approved'||phase==='failed'||phase==='unknown'){
   const label=phase==='approved'?(kind==='transfer'?dict.assetTransfer.complete:dict.assetDisposal.complete):dict.assetDisposal.retry
   await row.getByRole('button',{name:label,exact:true}).click()
   const confirm=page.locator('.el-message-box:visible');await confirm.getByText(op.no,{exact:false}).waitFor();await animation(confirm)
   await shot('command-confirm','Actual native command confirmation; preview is cancelled and does not prove a business write')
   await confirm.locator('.el-message-box__btns .el-button').first().click();await confirm.waitFor({state:'hidden'})
  }
  if(['completed','rejected','cancelled'].includes(phase)){
   await page.goto('http://omni-frontend:3000/admin/asset/asset',{waitUntil:'networkidle'})
   const ledger=page.locator('.asset-ledger-page');await ledger.waitFor();await settled()
   await ledger.locator('.el-form').first().locator('input').first().fill(checked.data.assetNo)
   await ledger.getByRole('button',{name:dict.common.search,exact:true}).click();await settled()
   const card=ledger.locator('.el-table__body-wrapper tbody tr');assert.equal(await card.count(),1)
   const detailResponse=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/asset/asset/'+checked.data.assetId&&response.request().method()==='GET')
   await card.getByRole('button',{name:dict.assetDisposal.details,exact:true}).click()
   const asset=await (await detailResponse).json();assert.equal(asset.code,200)
   const target=phase==='completed'?(kind==='transfer'?'IN_USE':checked.data.disposalType==='DISCARD'?'DISPOSED':'SCRAPPED'):checked.data.previousAssetStatus
   assert.equal(asset.data.status,target);assert.equal(asset.data.activeOperationType,null);assert.equal(asset.data.activeOperationId,null)
   if(kind==='transfer'&&phase==='completed'){assert.equal(asset.data.currentUserId,checked.data.toUserId);assert.equal(asset.data.currentUnitId,checked.data.toUnitId);assert.equal(asset.data.locationCode,checked.data.toLocation)}
   const assetDrawer=page.locator('.el-drawer:visible');await assetDrawer.getByText(checked.data.assetNo,{exact:true}).waitFor();await animation(assetDrawer)
   const bounds=await assetDrawer.boundingBox();assert(bounds&&bounds.x>=0&&bounds.x+bounds.width<=1440.5)
   await shot('asset-detail','Actual card result after the formal terminal command: final or restored state and cleared occupancy; full native detail/history drawer')
  }
  if(phase==='pending'){
   assert(op.task?.taskId&&op.current.processInstanceId)
   await context.close()
   const approvalContext=await browser.newContext({viewport:{width:1440,height:900},locale,timezoneId:'Asia/Shanghai',colorScheme:'light'})
   approvalContext.setDefaultTimeout(25000)
   actor=approver
   await approvalContext.route('**/api/**',route=>{if(['GET','HEAD','OPTIONS'].includes(route.request().method()))return route.continue();report.blocked_mutations.push({actor,locale,path:new URL(route.request().url()).pathname,method:route.request().method()});save();return route.abort()})
   await approvalContext.addInitScript(({token,username,locale})=>{if(location.origin!=='http://omni-frontend:3000')return;localStorage.setItem('token',token);localStorage.setItem('username',username);localStorage.setItem('omni-lang',locale);localStorage.setItem('omni-theme','light')},{token:tokens[actor],username:local.state.accounts[actor].username,locale})
   const ap=await approvalContext.newPage();ap.on('pageerror',error=>{report.page_errors.push({actor,locale,message:error.message});save()})
   await ap.goto('http://omni-frontend:3000/workspace/overview#approvals',{waitUntil:'networkidle'})
   const taskRow=ap.locator('tr').filter({hasText:op.no});await taskRow.first().waitFor();assert.equal(await taskRow.count(),1)
   await taskRow.getByRole('button',{name:dict.workflow.approve,exact:true}).click()
   const dialog=ap.locator('.el-dialog:visible');await dialog.getByText(op.no,{exact:true}).waitFor();await animation(dialog)
   assert(await dialog.locator('.approval-business-form').isVisible(),'真实业务审批视图必须成功加载')
   if(key.endsWith('Reject')){
    await dialog.getByText(dict.workflow.reject,{exact:true}).click()
    assert(await dialog.getByRole('radio',{name:dict.workflow.reject,exact:true}).isChecked())
    await dialog.locator('textarea').fill(state.namespace+' independent230 rejects proposed '+kind)
    await animation(dialog)
    await ap.waitForFunction(()=>{
     const group=[...document.querySelectorAll('.el-dialog .el-radio-group')].find(node=>node.getBoundingClientRect().width>0)
     if(!group)return false
     return [...group.querySelectorAll('.el-radio')].every(label=>{
      const inner=label.querySelector('.el-radio__inner'),input=label.querySelector('input')
      if(!inner||!input)return false
      const scale=new DOMMatrixReadOnly(getComputedStyle(inner,'::after').transform).a
      return input.checked?scale>0.999:Math.abs(scale)<0.001
     })
    },null,{timeout:5000})
    report.rejection_radio_visual_state??=[]
    report.rejection_radio_visual_state.push({locale,options:await dialog.locator('.el-radio-group').evaluate(group=>[...group.querySelectorAll('.el-radio')].map(label=>({text:label.innerText,checked:label.querySelector('input')?.checked,dot_transform:getComputedStyle(label.querySelector('.el-radio__inner'),'::after').transform})))})
    save()
   }
   const filename='asset-op-'+key+'-'+phase+'-approval-'+locale+'.png',bytes=await ap.screenshot({path:out+'/'+filename,fullPage:false,animations:'allow'})
   report.images.push({scene:'approval',locale,actor,user_id:local.state.accounts[actor].id,filename,route:new URL(ap.url()).pathname,sha256:sha(bytes),width:1440,height:900,captured_at:new Date().toISOString(),purpose:key.endsWith('Reject')?'Actual independently assigned task: native rejection choice and comment preview, not submitted during capture':'Actual assigned task and actual loaded asset business approval form; approval not submitted during capture',visual_review:'pending'});save();await approvalContext.close()
  }else await context.close()
  assert.equal(report.blocked_mutations.length,0)
  console.log(JSON.stringify({kind,key,phase,locale,images:report.images.filter(row=>row.locale===locale).length,result:'CAPTURED_NOT_VISUAL_REVIEWED'}))
 }
 assert.equal(report.images.length,12);assert.equal(report.page_errors.length,0)
 report.result='PASS_REAL_NATIVE_OPERATION_IMAGES_VISUAL_REVIEW_PENDING';report.raw_private_sha256=sha(fs.readFileSync(raw));report.finished_at=new Date().toISOString();save()
}catch(error){report.result='FAIL_PRESERVE_CURRENT_APPLICATION_AND_NATIVE_STATE';report.error=error.message;save();throw error}
finally{await browser.close()}
