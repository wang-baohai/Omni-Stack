import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import {stripTypeScriptTypes} from 'node:module'
import {createContext,SourceTextModule} from 'node:vm'
import {dirname,resolve} from 'node:path'
import {chromium} from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import {createLocalContext,privateRoot} from './goal-local-identities.mjs'

// 原WP28S02原生只读截图；允许填写未提交表单，禁止业务写请求、DOM替换或响应伪造。
assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(),'/validation/source')
const [mode,attempt]=process.argv.slice(2)
assert.equal(mode,'catalog');assert.match(attempt??'',/^asset-catalog-images-r[1-9][0-9]*$/)
const imageAttempt=process.argv[4]??'asset-layout-image-r1'
assert.match(imageAttempt,/^asset-layout-image-r[1-9][0-9]*$/)
const builtImage=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/'+imageAttempt+'/result.json'))
assert.equal(builtImage.result,'BUILT_NOT_DEPLOYED')
assert.equal(process.env.E2E_RUNTIME_IMAGE_ID,builtImage.image_id)
const state=JSON.parse(fs.readFileSync(privateRoot+'/asset-scenarios.json'))
assert.equal(state.namespace,'GOAL20261028ASSET');assert(state.completed.catalog&&!state.completed.allocate)
const local=createLocalContext(),sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex')
const out='/validation/evidence/codex-2026-10-22/wp28-s02/'+attempt
assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true})
fs.writeFileSync(out+'/source-used.mjs',fs.readFileSync(new URL(import.meta.url)))
const raw=privateRoot+'/'+attempt+'-wire.ndjson';fs.writeFileSync(raw,'',{flag:'wx',mode:0o600})
const report={original_step:'WP-28.S02',mode,attempt,started_at:new Date().toISOString(),workers:1,frontend_image:process.env.E2E_RUNTIME_IMAGE_ID,namespace:state.namespace,identity_source:'test-fixture',human_captcha_login_claimed:false,business_write_performed:false,dom_content_modified:false,response_mocked:false,images:[],form_geometry:[],blocked_mutations:[],page_errors:[],api_reads:[],visual_review:'pending',global_release_claimed:false}
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
for(const actor of ['assetManager','assetPeer','assetUser'])tokens[actor]=await local.login(actor)
const browser=await chromium.launch({headless:true})
try{
 for(const locale of locales){
  const dict=await dictionary(locale)
  for(const actor of ['assetManager','assetPeer','assetUser']){
   const context=await browser.newContext({viewport:{width:1440,height:900},locale,timezoneId:'Asia/Shanghai',colorScheme:'light'})
   context.setDefaultTimeout(25000)
   await context.route('**/api/**',async route=>{
    if(['GET','HEAD','OPTIONS'].includes(route.request().method()))return route.continue()
    report.blocked_mutations.push({actor,locale,method:route.request().method(),path:new URL(route.request().url()).pathname});save();return route.abort()
   })
   await context.addInitScript(({token,username,locale})=>{if(location.origin!=='http://omni-frontend:3000')return;localStorage.setItem('token',token);localStorage.setItem('username',username);localStorage.setItem('omni-lang',locale);localStorage.setItem('omni-theme','light')},{token:tokens[actor],username:local.state.accounts[actor].username,locale})
   const page=await context.newPage(),pending=[]
   page.on('pageerror',error=>{report.page_errors.push({actor,locale,message:error.message});save()})
   page.on('response',response=>{
    const url=new URL(response.url())
    if(url.origin!=='http://omni-frontend:3000'||!url.pathname.startsWith('/api/'))return
    pending.push((async()=>{const json=await response.json();fs.appendFileSync(raw,JSON.stringify({at:new Date().toISOString(),actor,locale,method:response.request().method(),path:url.pathname+url.search,http:response.status(),response:json})+'\n');report.api_reads.push({actor,locale,path:url.pathname+url.search,http:response.status(),code:json.code});save()})())
   })
   async function settled(){await page.waitForLoadState('networkidle');for(const loader of await page.locator('.el-loading-mask').all())await loader.waitFor({state:'hidden'});await Promise.all(pending);await page.evaluate(()=>document.fonts.ready)}
   async function navigate(route,selector){await page.goto('http://omni-frontend:3000'+route,{waitUntil:'networkidle'});assert(!/login|menu-load-error/.test(page.url()));const panel=page.locator(selector);await panel.waitFor();await settled();return panel}
   async function search(panel,value){await panel.locator('.el-form').first().locator('input').first().fill(value);await panel.getByRole('button',{name:dict.common.search,exact:true}).click();await settled()}
   const rowBy=(panel,text)=>panel.locator('.el-table__body-wrapper tbody tr').filter({hasText:text})
   const exact=text=>new RegExp('^'+text.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'$')
   const item=(dialog,label)=>dialog.locator('.el-form-item').filter({has:page.locator('.el-form-item__label').filter({hasText:exact(label)})})
   async function chooseUser(dialog,label,alias){
    const field=item(dialog,label);assert.equal(await field.count(),1)
    await field.locator('.el-select input').fill(local.state.accounts[alias].username)
    const option=page.getByRole('option').filter({hasText:local.state.accounts[alias].username});await option.waitFor();assert.equal(await option.count(),1);await option.click();await settled()
   }
   async function capture(scene,purpose,objects={}){
    await settled();assert(!/\b(?:assetLedger|assetTransfer|assetDisposal|common)\.[A-Za-z][A-Za-z0-9_.]+/.test(await page.locator('body').innerText()))
    if(scene.endsWith('-form')){
     await page.locator('.el-dialog:visible .el-dialog__header').scrollIntoViewIfNeeded()
     const labels=await page.locator('.el-dialog:visible .el-form-item__label').evaluateAll(nodes=>nodes.map(node=>({text:node.textContent,client_height:node.clientHeight,scroll_height:node.scrollHeight})))
     report.form_geometry.push({scene,locale,labels});save()
     assert(labels.length>0&&labels.every(label=>label.scroll_height<=label.client_height+1),'Real form labels overflow; keep failed measurement and do not accept image')
     const values=await page.locator('.el-dialog:visible input.el-input__inner').evaluateAll(nodes=>nodes.filter(node=>/^\d{4}-\d{2}-\d{2}$|^\d+\.\d{2}$|^CNY$/.test(node.value)).map(node=>{const style=getComputedStyle(node),canvas=document.createElement('canvas'),context=canvas.getContext('2d');context.font=style.font;return{value:node.value,available:node.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight),required:context.measureText(node.value).width}}))
     report.form_geometry.at(-1).finite_values=values;save()
     assert(values.every(value=>value.available>=value.required+2),'Actual date/money/currency value is clipped; labels alone cannot prove readable inputs')
    }
    const filename=scene+'-'+locale+'.png',bytes=await page.screenshot({path:out+'/'+filename,fullPage:false,animations:'disabled'})
    assert.equal(bytes.readUInt32BE(16),1440);assert.equal(bytes.readUInt32BE(20),900)
    report.images.push({scene,locale,filename,actor,user_id:local.state.accounts[actor].id,route:new URL(page.url()).pathname,sha256:sha(bytes),width:1440,height:900,captured_at:new Date().toISOString(),purpose,objects,visual_review:'pending'});save()
    if(scene==='asset-current-create-form'||scene==='asset-current-edit-form'){
     const footer=page.locator('.el-dialog:visible .el-dialog__footer');await footer.scrollIntoViewIfNeeded();await settled()
     const box=await footer.boundingBox();assert(box&&box.y>=0&&box.y+box.height<=900,'Actual dialog save/cancel controls must be reachable by native scrolling')
     await capture(scene+'-footer','Native vertical scroll to save/cancel controls; unsaved form, no business write',{...objects,unsaved:true,native_scroll:true})
    }
   }
   if(actor==='assetManager'){
    const overview=await navigate('/admin/asset/overview','.asset-overview-page')
    assert.equal(await overview.locator('.el-segmented__item').count(),4)
    assert.equal((await overview.locator('.el-table__body-wrapper tbody tr').first().locator('td').first().innerText()).trim(),dict.assetLedger.statusInStock)
    await capture('asset-current-overview','Actual scoped summary and four visible dimension choices; currently six source cards plus three manual cards',{manual_cards:3,source_cards:6})
    const panel=await navigate('/admin/asset/asset','.asset-ledger-page')
    await search(panel,'Goal 20261028 asset ')
    assert.equal(await panel.locator('.el-table__body-wrapper tbody tr').count(),3)
    await capture('asset-current-ledger','Actual three owned manual cards, filters and pagination',{manual_cards:3})
    const row=rowBy(panel,state.objects.manualLife.assetNo);assert.equal(await row.count(),1)
    await row.getByRole('button',{name:dict.assetDisposal.details,exact:true}).click();await page.locator('.el-drawer:visible').waitFor()
    await page.locator('.el-drawer:visible').getByText(state.objects.manualLife.assetNo,{exact:true}).waitFor()
    assert.equal(await page.locator('.el-drawer:visible .el-descriptions').count(),1)
    assert.equal(await page.locator('.el-drawer:visible .el-table__body-wrapper tbody tr').count(),2)
    await capture('asset-current-manual-detail','Actual manually created and edited card; source absent and two real history records',{asset_id:state.objects.manualLife.id,purchase_amount:'125.50',version:1})
    await page.locator('.el-drawer:visible .el-drawer__close-btn').click();await page.locator('.el-drawer:visible').waitFor({state:'hidden'})
    await panel.getByRole('button',{name:dict.assetLedger.manualStock,exact:true}).click()
    let dialog=page.locator('.el-dialog:visible');await dialog.waitFor()
    await item(dialog,dict.assetDisposal.assetName).locator('input').fill('Goal 20261028 asset new example')
    await item(dialog,dict.assetLedger.categoryCode).locator('input').fill('OFFICE')
    await item(dialog,dict.assetLedger.assetValue).locator('input').fill('123.45')
    await chooseUser(dialog,dict.assetLedger.managerUser,'assetManager')
    assert.equal(await item(dialog,dict.assetLedger.managerUnit).locator('input').inputValue(),'606')
    await capture('asset-current-create-form','Real native filled example with automatic owner department; not submitted',{unsaved:true,owner_user:223,owner_unit:606})
    await dialog.getByRole('button',{name:dict.common.cancel,exact:true}).click()
    await row.getByRole('button',{name:dict.common.edit,exact:true}).click();dialog=page.locator('.el-dialog:visible');await dialog.waitFor()
    assert.equal(await item(dialog,dict.assetLedger.assetValue).locator('input').inputValue(),'125.50')
    await capture('asset-current-edit-form','Actual updated metadata and editable management owner; no direct status/current-user/location fields; not resaved',{asset_id:state.objects.manualLife.id,unsaved:true})
    await dialog.getByRole('button',{name:dict.common.cancel,exact:true}).click()
    await row.getByRole('button',{name:dict.assetLedger.allocate,exact:true}).click();dialog=page.locator('.el-dialog:visible');await dialog.waitFor()
    await chooseUser(dialog,dict.assetTransfer.toUser,'assetUser')
    assert.equal(await item(dialog,dict.assetTransfer.toUnit).locator('input').inputValue(),'606')
    await capture('asset-current-allocation-form','Actual enabled recipient selector and automatic primary department; not submitted',{asset_id:state.objects.manualLife.id,target_user:222,target_unit:606,unsaved:true})
    await dialog.getByRole('button',{name:dict.common.cancel,exact:true}).click()
   }else if(actor==='assetPeer'){
    const panel=await navigate('/admin/asset/asset','.asset-ledger-page');await search(panel,'Goal 20261028 asset ')
    assert.equal(await panel.locator('.el-table__body-wrapper tbody tr').count(),0)
    await capture('asset-current-peer-empty','Authorized sibling-department manager sees no other-department owned fixture cards; actual empty result',{owned_fixture_matches:0})
   }else{
    await page.goto('http://omni-frontend:3000/admin/asset/transfer',{waitUntil:'networkidle'});await settled()
    assert((await page.locator('body').innerText()).includes('403'),'Expect actual unauthorized route component, do not fabricate error')
    await capture('asset-current-forbidden','Self-only asset user reaches real unauthorized403 route; managed transfer list API also denied in read-only proof',{})
   }
   assert.equal(report.blocked_mutations.length,0,'No mutation was attempted by read-only native capture')
   await context.close()
  }
  console.log(JSON.stringify({locale,images:report.images.filter(row=>row.locale===locale).length,result:'NATIVE_CAPTURE_PENDING_ACTUAL_VISUAL_REVIEW'}))
 }
 assert.equal(report.images.length,40);assert.equal(report.page_errors.length,0)
 report.raw_private_sha256=sha(fs.readFileSync(raw));report.result='PASS_NATIVE40_CATALOG_IMAGES_ACTUAL_VISUAL_REVIEW_PENDING';report.finished_at=new Date().toISOString();save()
}catch(error){report.result='FAIL_KEEP_REAL_NATIVE_CAPTURE';report.error=error.message;save();throw error}
finally{await browser.close()}
