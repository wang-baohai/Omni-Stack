import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import {stripTypeScriptTypes} from 'node:module'
import {createContext,SourceTextModule} from 'node:vm'
import {dirname,resolve} from 'node:path'
import {chromium} from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import {createLocalContext,privateRoot} from './goal-local-identities.mjs'

// 原WP28S02真实中间状态截图；只读业务请求，确认框只展示并取消。
assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(),'/validation/source')
const [stage,attempt]=process.argv.slice(2)
const stages={allocated:{phase:'allocate',status:'ALLOCATED',version:2},accepted:{phase:'accept',status:'IN_USE',version:3},maintenance:{phase:'maintenance',status:'MAINTENANCE',version:4},'maintenance-complete':{phase:'maintenance-complete',status:'IN_USE',version:5},'returned-from-use':{phase:'return-in-use',status:'IN_STOCK',version:6},reallocated:{phase:'reallocate',status:'ALLOCATED',version:7},returned:{phase:'return',status:'IN_STOCK',version:8}}
assert(Object.hasOwn(stages,stage));assert.match(attempt??'',new RegExp('^asset-'+stage+'-images-r[1-9][0-9]*$'))
const imageAttempt=process.argv[4]??'asset-layout-image-r2'
assert.match(imageAttempt,/^asset-layout-image-r[1-9][0-9]*$/)
const builtImage=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/'+imageAttempt+'/result.json'))
assert.equal(builtImage.result,'BUILT_NOT_DEPLOYED');assert.equal(process.env.E2E_RUNTIME_IMAGE_ID,builtImage.image_id)
const state=JSON.parse(fs.readFileSync(privateRoot+'/asset-scenarios.json')),expected=stages[stage],life=state.objects.manualLife
assert.equal(state.namespace,'GOAL20261028ASSET');assert(state.completed[expected.phase]);assert.equal(life.current.status,expected.status);assert.equal(life.current.version,expected.version)
const later=Object.values(stages).slice(Object.keys(stages).indexOf(stage)+1);assert(later.every(row=>!state.completed[row.phase]),'Do not recreate historical intermediate states')
const local=createLocalContext(),out='/validation/evidence/codex-2026-10-22/wp28-s02/'+attempt,sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex')
assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/source-used.mjs',fs.readFileSync(new URL(import.meta.url)))
const raw=privateRoot+'/'+attempt+'-wire.ndjson';fs.writeFileSync(raw,'',{flag:'wx',mode:0o600})
const report={original_step:'WP-28.S02',stage,attempt,started_at:new Date().toISOString(),workers:1,frontend_image:process.env.E2E_RUNTIME_IMAGE_ID,identity_source:'test-fixture',human_captcha_login_claimed:false,business_write_performed:false,dom_content_modified:false,response_mocked:false,asset_id:life.id,asset_no:life.assetNo,status:expected.status,version:expected.version,images:[],api_reads:[],page_errors:[],blocked_mutations:[],visual_review:'pending',global_release_claimed:false}
const save=()=>fs.writeFileSync(out+'/result.json',JSON.stringify(report,null,2)+'\n')
const locales=['zh-CN','en-US','ja-JP','ko-KR'],modules=new Map(),vmContext=createContext({})
async function dictionary(locale){
 const root='/source/omni-frontend/src/locales'
 const compile=file=>{assert(locales.some(value=>file===root+'/'+value+'.ts'));if(!modules.has(file))modules.set(file,new SourceTextModule(stripTypeScriptTypes(fs.readFileSync(file,'utf8')),{context:vmContext,identifier:file}));return modules.get(file)}
 const module=compile(root+'/'+locale+'.ts')
 if(module.status==='unlinked')await module.link((specifier,parent)=>{assert.match(specifier,/^\.\/(zh-CN|en-US|ja-JP|ko-KR)(\.ts)?$/);return compile(resolve(dirname(parent.identifier),specifier.endsWith('.ts')?specifier:specifier+'.ts'))})
 if(module.status==='linked')await module.evaluate()
 return module.namespace.default
}
const tokens={}
for(const actor of ['assetManager','assetUser'])tokens[actor]=await local.login(actor)
const baseline=await fetch('http://omni-gateway:8080/api/asset/asset/'+life.id,{headers:{Authorization:'Bearer '+tokens.assetManager},signal:AbortSignal.timeout(30000)})
const baselineJson=await baseline.json();assert.equal(baselineJson.code,200);assert.equal(baselineJson.data.status,expected.status);assert.equal(baselineJson.data.version,expected.version)
assert.equal(baselineJson.data.currentUserId,expected.status==='IN_STOCK'?null:222)
report.baseline_sha256=sha(JSON.stringify(baselineJson));save()
const browser=await chromium.launch({headless:true})
try{
 for(const locale of locales){
  const dict=await dictionary(locale),statusLabel=dict.assetLedger[{ALLOCATED:'statusAllocated',IN_USE:'statusInUse',MAINTENANCE:'statusMaintenance',IN_STOCK:'statusInStock'}[expected.status]]
  for(const actor of ['assetManager','assetUser']){
   const context=await browser.newContext({viewport:{width:1440,height:900},locale,timezoneId:'Asia/Shanghai',colorScheme:'light'});context.setDefaultTimeout(25000)
   await context.route('**/api/**',route=>{if(['GET','HEAD','OPTIONS'].includes(route.request().method()))return route.continue();report.blocked_mutations.push({actor,locale,path:new URL(route.request().url()).pathname,method:route.request().method()});save();return route.abort()})
   await context.addInitScript(({token,username,locale})=>{if(location.origin!=='http://omni-frontend:3000')return;localStorage.setItem('token',token);localStorage.setItem('username',username);localStorage.setItem('omni-lang',locale);localStorage.setItem('omni-theme','light')},{token:tokens[actor],username:local.state.accounts[actor].username,locale})
   const page=await context.newPage(),pending=[]
   page.on('pageerror',error=>{report.page_errors.push({actor,locale,message:error.message});save()})
   page.on('response',response=>{const url=new URL(response.url());if(!url.pathname.startsWith('/api/'))return;pending.push((async()=>{const json=await response.json();fs.appendFileSync(raw,JSON.stringify({at:new Date().toISOString(),actor,locale,path:url.pathname+url.search,http:response.status(),response:json})+'\n');report.api_reads.push({actor,locale,path:url.pathname+url.search,http:response.status(),code:json.code});save()})())})
   async function settled(){await page.waitForLoadState('networkidle');for(const loader of await page.locator('.el-loading-mask').all())await loader.waitFor({state:'hidden'});await Promise.all(pending);await page.evaluate(()=>document.fonts.ready)}
   async function capture(scene,purpose){
    await settled();assert(!/\b(?:assetLedger|assetDisposal|common)\.[A-Za-z][A-Za-z0-9_.]+/.test(await page.locator('body').innerText()))
    if(scene==='history'){
     const drawer=page.locator('.el-drawer:visible')
     await drawer.evaluate(async node=>{await Promise.all((node.closest('.el-overlay')??node).getAnimations({subtree:true}).map(animation=>animation.finished.catch(()=>{})))})
     const box=await drawer.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=1440.5&&box.width>720,'History drawer must finish native opening transition before capture')
    }
    if(scene.endsWith('-confirm'))await page.locator('.el-message-box:visible').getByText(life.assetNo,{exact:false}).waitFor()
    const filename='asset-life-'+stage+'-'+scene+'-'+locale+'.png',bytes=await page.screenshot({path:out+'/'+filename,fullPage:false,animations:'allow'})
    assert.equal(bytes.readUInt32BE(16),1440);assert.equal(bytes.readUInt32BE(20),900)
    report.images.push({scene,locale,filename,actor,user_id:local.state.accounts[actor].id,route:new URL(page.url()).pathname,sha256:sha(bytes),width:1440,height:900,captured_at:new Date().toISOString(),purpose,visual_review:'pending'});save()
   }
   async function preview(row,label,scene){
    await row.getByRole('button',{name:label,exact:true}).click();const box=page.locator('.el-message-box');await box.waitFor();assert((await box.innerText()).includes(life.assetNo))
    await box.evaluate(async node=>{await Promise.all((node.closest('.el-overlay')??node).getAnimations({subtree:true}).map(animation=>animation.finished.catch(()=>{})))})
    await capture(scene,'Actual native confirmation for the recorded asset and current actor; cancelled without business mutation')
    await box.locator('.el-message-box__btns .el-button').first().click();await box.waitFor({state:'hidden'})
   }
   async function revealStatus(row){
    const wrap=panel.locator('.el-table__body-wrapper .el-scrollbar__wrap')
    await wrap.hover();await page.mouse.wheel(2000,0)
    await page.waitForFunction(()=>{const wrap=document.querySelector('.asset-ledger-page .el-table__body-wrapper .el-scrollbar__wrap');return wrap&&wrap.scrollLeft>0&&wrap.scrollLeft>=wrap.scrollWidth-wrap.clientWidth-1})
    const tag=await row.locator('.el-tag').boundingBox(),actions=await row.locator('.fixed-actions-col').boundingBox()
    report.horizontal_geometry??=[];report.horizontal_geometry.push({actor,locale,tag,actions,scroll:await wrap.evaluate(node=>({left:node.scrollLeft,width:node.scrollWidth,client:node.clientWidth}))});save()
    assert(tag&&actions&&tag.x>=220&&tag.x+tag.width<=actions.x,'Native horizontal scrolling must reveal status before fixed actions; offscreen text is not visual proof')
   }
   await page.goto('http://omni-frontend:3000/admin/asset/asset',{waitUntil:'networkidle'});assert(!/login|403|menu-load-error/.test(page.url()))
   const panel=page.locator('.asset-ledger-page');await panel.waitFor();await settled()
   await panel.locator('.el-form').first().locator('input').first().fill(life.assetNo)
   await panel.getByRole('button',{name:dict.common.search,exact:true}).click();await settled()
   const rows=panel.locator('.el-table__body-wrapper tbody tr')
   if(actor==='assetManager'){
    assert.equal(await rows.count(),1);assert.equal((await rows.locator('.el-tag').innerText()).trim(),statusLabel);await revealStatus(rows)
    await capture('manager-state','Actual management ledger filtered to the exact card; native horizontal scroll exposes the translated lifecycle state and allowed commands')
    if(stage==='accepted')await preview(rows,dict.assetLedger.sendMaintenance,'maintenance-confirm')
    if(stage==='maintenance')await preview(rows,dict.assetLedger.completeMaintenance,'maintenance-complete-confirm')
    if(stage==='returned'){
     await rows.getByRole('button',{name:dict.assetDisposal.details,exact:true}).click();const drawer=page.locator('.el-drawer:visible');await drawer.getByText(life.assetNo,{exact:true}).waitFor();assert.equal(await drawer.locator('.el-table__body-wrapper tbody tr').count(),9)
     await capture('history','Actual nine immutable history records from creation through both return paths; current use cleared')
    }
   }else{
    for(const label of [dict.assetLedger.manualStock,dict.common.edit,dict.assetDisposal.details])assert.equal(await panel.getByRole('button',{name:label,exact:true}).count(),0,'Self user must not inherit management actions')
    assert.equal(await rows.count(),expected.status==='IN_STOCK'?0:1)
    if(expected.status!=='IN_STOCK'){
     assert.equal((await rows.locator('.el-tag').innerText()).trim(),statusLabel);await revealStatus(rows)
     assert.equal(await rows.getByRole('button',{name:dict.assetLedger.accept,exact:true}).count(),expected.status==='ALLOCATED'?1:0)
     assert.equal(await rows.getByRole('button',{name:dict.assetLedger.return,exact:true}).count(),stage==='maintenance'?0:1)
    }
    await capture('self-state',expected.status==='IN_STOCK'?'Actual fixed-self query becomes empty after formal return':'Actual current-user-only asset list and state-dependent actions')
    if(expected.status==='ALLOCATED')await preview(rows,dict.assetLedger.accept,'accept-confirm')
    if(stage==='reallocated')await preview(rows,dict.assetLedger.return,'direct-return-confirm')
    if(stage==='maintenance-complete')await preview(rows,dict.assetLedger.return,'return-confirm')
   }
   assert.equal(report.blocked_mutations.length,0);await context.close()
  }
  console.log(JSON.stringify({stage,locale,images:report.images.filter(row=>row.locale===locale).length,result:'CAPTURED_NOT_VISUAL_REVIEWED'}))
 }
 assert.equal(report.images.length,stage==='returned-from-use'?8:stage==='reallocated'?16:12);assert.equal(report.page_errors.length,0)
 report.result='PASS_REAL_NATIVE_LIFECYCLE_IMAGES_VISUAL_REVIEW_PENDING';report.raw_private_sha256=sha(fs.readFileSync(raw));report.finished_at=new Date().toISOString();save()
}catch(error){report.result='FAIL_PRESERVE_REAL_NATIVE_STATE';report.error=error.message;save();throw error}
finally{await browser.close()}
