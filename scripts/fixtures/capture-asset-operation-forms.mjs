import assert from 'node:assert/strict'
import fs from 'node:fs'
import crypto from 'node:crypto'
import {stripTypeScriptTypes} from 'node:module'
import {createContext,SourceTextModule} from 'node:vm'
import {dirname,resolve} from 'node:path'
import {chromium} from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import {createLocalContext,privateRoot} from './goal-local-identities.mjs'

// 原S02申请表单原生预览；填写后取消，正式业务由单独登记的API命令执行。
assert.equal(process.cwd(),'/validation/source');assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
const [kind,key,attempt]=process.argv.slice(2)
assert(['transfer','disposal'].includes(kind));assert(['manualLife','manualDiscard','manualScrap'].includes(key))
assert.match(attempt??'',new RegExp('^asset-'+kind+'-form-images-r[1-9][0-9]*$'))
const build=JSON.parse(fs.readFileSync('/validation/evidence/codex-2026-10-22/wp28-s02/asset-layout-image-r5/result.json'))
assert.equal(build.image_id,process.env.E2E_RUNTIME_IMAGE_ID)
const local=createLocalContext(),state=JSON.parse(fs.readFileSync(privateRoot+'/asset-scenarios.json')),owned=state.objects[key]
assert.equal(state.namespace,'GOAL20261028ASSET');assert(owned?.id&&owned.assetNo)
const token=await local.login('assetManager'),assetPath='/api/asset/asset/'+owned.id
const checked=await (await fetch('http://omni-gateway:8080'+assetPath,{headers:{Authorization:'Bearer '+token}})).json()
assert.equal(checked.code,200);assert(['IN_STOCK','IN_USE','ALLOCATED'].includes(checked.data.status));assert.equal(checked.data.activeOperationId,null)
const out='/validation/evidence/codex-2026-10-22/wp28-s02/'+attempt,sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex')
assert(!fs.existsSync(out));fs.mkdirSync(out,{recursive:true});fs.writeFileSync(out+'/source-used.mjs',fs.readFileSync(new URL(import.meta.url)))
const report={original_step:'WP-28.S02',kind,key,attempt,started_at:new Date().toISOString(),workers:1,frontend_image:build.image_id,identity_source:'test-fixture',human_captcha_login_claimed:false,business_write_performed:false,dom_content_modified:false,response_mocked:false,asset_id:owned.id,asset_no:owned.assetNo,baseline:checked.data,images:[],blocked_mutations:[],page_errors:[],visual_review:'pending',not_a_submission_receipt:true}
const save=()=>fs.writeFileSync(out+'/result.json',JSON.stringify(report,null,2)+'\n')
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
  const dict=await dictionary(locale),context=await browser.newContext({viewport:{width:1440,height:900},locale,timezoneId:'Asia/Shanghai',colorScheme:'light'});context.setDefaultTimeout(25000)
  await context.route('**/api/**',route=>{if(['GET','HEAD','OPTIONS'].includes(route.request().method()))return route.continue();report.blocked_mutations.push({locale,path:new URL(route.request().url()).pathname,method:route.request().method()});save();return route.abort()})
  await context.addInitScript(({token,username,locale})=>{if(location.origin!=='http://omni-frontend:3000')return;localStorage.setItem('token',token);localStorage.setItem('username',username);localStorage.setItem('omni-lang',locale);localStorage.setItem('omni-theme','light')},{token,username:local.state.accounts.assetManager.username,locale})
  const page=await context.newPage();page.on('pageerror',error=>{report.page_errors.push({locale,message:error.message});save()})
  await page.goto('http://omni-frontend:3000/admin/asset/'+kind,{waitUntil:'networkidle'})
  await page.locator('.asset-operation-page').getByRole('button',{name:kind==='transfer'?dict.assetTransfer.initiate:dict.assetDisposal.initiate,exact:true}).click()
  const dialog=page.locator('.el-dialog:visible');await dialog.waitFor()
  await dialog.evaluate(async node=>{await Promise.all((node.closest('.el-overlay')??node).getAnimations({subtree:true}).map(item=>item.finished.catch(()=>{})))})
  async function shot(scene){
   await dialog.locator('.el-dialog__header').click()
   for(const dropdown of await page.locator('.el-select-dropdown').all())await dropdown.waitFor({state:'hidden'})
   if(kind==='disposal'){
    const expected=scene==='filled'?'SCRAP':'DISCARD'
    await dialog.locator('.el-radio-button.is-active input[value="'+expected+'"]').waitFor({state:'attached'})
    report.radio_observations??=[]
    report.radio_observations.push({locale,scene,expected,buttons:await dialog.locator('.el-radio-button').evaluateAll(nodes=>nodes.map(node=>({value:node.querySelector('input').value,checked:node.querySelector('input').checked,className:node.className,background:getComputedStyle(node.querySelector('.el-radio-button__inner')).backgroundColor})))})
    save()
   }
   await dialog.evaluate(async node=>{await Promise.all(node.getAnimations({subtree:true}).map(item=>item.finished.catch(()=>{})))})
   await page.waitForLoadState('networkidle');await page.evaluate(()=>document.fonts.ready)
   const box=await dialog.boundingBox();assert(box&&box.y>=0&&box.y+box.height<=900.5,'表单和底部操作必须完整位于本帧')
   const footer=dialog.locator('.el-dialog__footer');assert(await footer.getByRole('button',{name:dict.common.cancel,exact:true}).isVisible());assert(await footer.getByRole('button',{name:dict.assetDisposal.submitApproval,exact:true}).isVisible())
   const filename='asset-'+kind+'-form-'+scene+'-'+locale+'.png',bytes=await page.screenshot({path:out+'/'+filename,fullPage:false,animations:'allow'})
   report.images.push({scene,locale,filename,actor:'assetManager',user_id:223,route:new URL(page.url()).pathname,sha256:sha(bytes),width:1440,height:900,captured_at:new Date().toISOString(),purpose:'Actual native '+kind+' form '+scene+'; cancelled without submission',visual_review:'pending'});save()
  }
  await shot('empty')
  const selects=dialog.getByRole('combobox')
  await dialog.locator('.el-select__wrapper').nth(0).click();await selects.nth(0).fill(owned.assetNo)
  const assetOption=page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({hasText:owned.assetNo});await assetOption.waitFor();assert.equal(await assetOption.count(),1);await assetOption.click()
  const items=dialog.locator('.el-form-item')
  if(kind==='transfer'){
   await dialog.locator('.el-select__wrapper').nth(1).click();await selects.nth(1).fill(local.state.accounts.assetManager.username)
   const userOption=page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({hasText:local.state.accounts.assetManager.username});await userOption.waitFor();assert.equal(await userOption.count(),1);await userOption.click()
   assert.equal(await items.nth(2).locator('input').inputValue(),'606')
   await items.nth(3).locator('input').fill('ROOM-C606')
   await items.nth(4).locator('textarea').fill(state.namespace+' reject transfer and restore actual in-use state')
  }else{
   await dialog.locator('.el-dialog__header').click()
   for(const dropdown of await page.locator('.el-select-dropdown').all())await dropdown.waitFor({state:'hidden'})
   await dialog.locator('.el-radio-button').filter({hasText:dict.assetDisposal.scrap}).click()
   assert(await dialog.getByRole('radio',{name:dict.assetDisposal.scrap,exact:true}).isChecked())
   await items.nth(2).locator('input').fill('0.00')
   await items.nth(3).locator('input').fill('Proposed test disposal; rejection preserves the owned card')
   await items.nth(4).locator('textarea').fill(state.namespace+' reject disposal and restore actual in-use state')
  }
  await shot('filled')
  await dialog.locator('.el-dialog__footer').getByRole('button',{name:dict.common.cancel,exact:true}).click();await dialog.waitFor({state:'hidden'})
  assert.equal(report.blocked_mutations.length,0);await context.close()
  console.log(JSON.stringify({kind,locale,images:2,result:'CAPTURED_NOT_SUBMITTED_OR_VISUAL_REVIEWED'}))
 }
 assert.equal(report.images.length,8);assert.equal(report.page_errors.length,0)
 report.result='PASS_REAL_NATIVE_FORM_PREVIEWS_VISUAL_REVIEW_PENDING';report.finished_at=new Date().toISOString();save()
}catch(error){report.result='FAIL_PRESERVE_NATIVE_FORM_AND_NO_SUBMISSION';report.error=error.message;save();throw error}
finally{await browser.close()}
