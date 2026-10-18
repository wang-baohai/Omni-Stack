/** Docker 内只读采集四语用户列表；不模拟接口、不改页面文本、不提交业务表单。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
assert.ok(existsSync('/.dockerenv'))
const require=createRequire('/workspace/omni-frontend/package.json')
const {chromium,expect}=require('@playwright/test')
const root='/evidence'
const tokenPath=root+'/permissions-runtime-374/tokens.private.json'
const receiptPath=root+'/permissions-users-capture-377.json'
assert.ok(!existsSync(receiptPath),'Preserve previous receipt')
const sha=value=>createHash('sha256').update(value).digest('hex')
const sourcePaths=['omni-frontend/src/views/system/user/index.vue','omni-frontend/src/views/system/role/index.vue','omni-frontend/src/api/user.ts','omni-frontend/src/api/role.ts','omni-frontend/src/api/request.ts','omni-frontend/src/i18n/index.ts','omni-frontend/src/stores/user.ts','docker/frontend/nginx.conf','omni-backend/omni-auth/src/test/java/com/omni/auth/e2e/E2eTokenFixture.java']
const sources=sourcePaths.map(path=>({path,sha256:sha(readFileSync('/workspace/'+path))}))
const receipt={schema:1,started_at:new Date().toISOString(),phase:'STARTING',thread_id:'01a13442-0969-7ba1-b2fd-fd8329b21c7b',head:'bdc4c5c4013514949d5629f21ce0c51e4714101f',artifact_proof:'permissions-runtime-artifact-proof-370.json',runtime_journal:'permissions-runtime-374/journal.json',runtime_profile:'native core lite',base:'http://omni-frontend:3000',viewport:{width:1680,height:1050},identity_method:'Current test-classpath E2eTokenFixture; enabled database users and actual role/permission claims; 1200-second tokens; not password/CAPTCHA login',sources,source_sha256:sha(JSON.stringify(sources)),observations:[],captures:[],blocked_mutations:[],errors:[],token_file_removed:false,forms_submitted:false,mocks:false,visual_review:'PENDING_ACTUAL_RAW_IMAGE_INSPECTION'}
function save(){writeFileSync(receiptPath+'.next',JSON.stringify(receipt,null,2)+'\n');renameSync(receiptPath+'.next',receiptPath)}
save()
let browser
try{
 const tokens=JSON.parse(readFileSync(tokenPath,'utf8'))
 const claims=JSON.parse(Buffer.from(tokens.adminToken.split('.')[1],'base64url').toString())
 assert.ok(claims.exp*1000>Date.now()+120000)
 assert.ok(claims.roles.includes('SUPER_ADMIN'))
 receipt.actor={username:'admin',tenantId:claims.tenant_id,roles:claims.roles,expires_at:new Date(claims.exp*1000).toISOString()}
 const get=async(path,token,actor)=>{
   const response=await fetch(receipt.base+path,{headers:{Authorization:'Bearer '+token,'X-Tenant-Id':'1'},signal:AbortSignal.timeout(20000)})
   const body=await response.json()
   receipt.observations.push({at:new Date().toISOString(),actor,method:'GET',path,http:response.status,code:body.code,total:body.data?.total,records:body.data?.records?.length});save()
   return {response,body}
 }
 const admin=await get('/api/auth/user/list?page=1&size=10',tokens.adminToken,'admin')
 assert.equal(admin.response.status,200);assert.equal(admin.body.code,200);assert.ok(admin.body.data.records.length>0)
 const employee=await get('/api/auth/user/list?page=1&size=10',tokens.employeeToken,'employee')
 assert.equal(employee.response.status,403,'Functional denial must be real HTTP403')
 browser=await chromium.launch()
 receipt.tools={node:process.version,playwright:require('@playwright/test/package.json').version,chromium:browser.version()}
 const titles={'zh-CN':'用户管理','en-US':'User Management','ja-JP':'ユーザー管理','ko-KR':'사용자 관리'}
 for(const locale of ['zh-CN','en-US','ja-JP','ko-KR']){
  const context=await browser.newContext({viewport:receipt.viewport,baseURL:receipt.base,locale,timezoneId:'Asia/Shanghai',reducedMotion:'reduce'})
  try{
   const page=await context.newPage()
   page.on('pageerror',error=>{receipt.errors.push({locale,category:'pageerror',message:String(error.message).slice(0,300)});save()})
   await page.route('**/api/**',async route=>{
     const request=route.request()
     if(['GET','HEAD','OPTIONS'].includes(request.method()))await route.continue()
     else{receipt.blocked_mutations.push({locale,method:request.method(),path:new URL(request.url()).pathname});save();await route.abort('blockedbyclient')}
   })
   const responses=[]
   page.on('response',response=>{if(response.url().includes('/api/'))responses.push({path:new URL(response.url()).pathname,http:response.status()})})
   await page.addInitScript(({locale,token})=>{localStorage.setItem('omni-lang',locale);localStorage.setItem('token',token);localStorage.setItem('username','admin')},{locale,token:tokens.adminToken})
   const listReady=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/user/list'&&r.status()===200)
   await page.goto('/admin/system/user',{waitUntil:'domcontentloaded'})
   const listResponse=await listReady
   const data=await listResponse.json()
   assert.equal(data.code,200);assert.ok(data.data.records.length>0)
   await expect(page.locator('.card-header').first()).toContainText(titles[locale])
   await expect(page.locator('.user-table .el-table__body-wrapper tbody tr')).toHaveCount(data.data.records.length)
   await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
   await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
   await page.evaluate(async()=>{await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))})
   await expect(page).not.toHaveURL(/login|menu-load-error/)
   const metrics=await page.locator('.user-table').evaluate(el=>({width:el.clientWidth,scrollWidth:el.scrollWidth,tableText:el.innerText,tagWidths:[...el.querySelectorAll('.el-tag')].slice(0,10).map(x=>({client:x.clientWidth,scroll:x.scrollWidth,text:x.textContent}))}))
   assert.ok(metrics.width>1000)
   const bodyText=await page.locator('.user-table').innerText()
   assert.ok(!/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/.test(bodyText.replace(/[\w]\*\*\*@[\w.-]+/g,'')),'Email must use source mask')
   assert.ok(!/\b1[3-9]\d{9}\b/.test(bodyText),'Phone must use source mask')
   const directory=resolve(root,'permissions-users-377',locale);mkdirSync(directory,{recursive:true})
   const image=resolve(directory,'system-users.png');assert.ok(!existsSync(image))
   await page.screenshot({path:image,fullPage:false,animations:'disabled'})
   receipt.captures.push({locale,image:'permissions-users-377/'+locale+'/system-users.png',target:'docs/images/'+locale+'/system-users.png',route:'/admin/system/user',viewport:receipt.viewport,captured_at:new Date().toISOString(),sha256:sha(readFileSync(image)),total:data.data.total,visible_rows:data.data.records.length,title:titles[locale],responses,table_metrics:metrics,visual_review:'PENDING'});save()
  }finally{await context.close()}
 }
 assert.equal(receipt.captures.length,4);assert.equal(receipt.blocked_mutations.length,0);assert.equal(receipt.errors.length,0)
 receipt.phase='CAPTURE_COMPLETE_PENDING_RAW_VISUAL_REVIEW';save()
}catch(error){receipt.phase='FAILED';receipt.errors.push({category:error.name,message:String(error.message).slice(0,500)});save();throw error}
finally{
 if(browser)await browser.close()
 if(existsSync(tokenPath)){unlinkSync(tokenPath);receipt.token_file_removed=true}
 receipt.finished_at=new Date().toISOString();save()
}
process.stdout.write(JSON.stringify({phase:receipt.phase,captures:receipt.captures.length,observations:receipt.observations,token_file_removed:receipt.token_file_removed})+'\n')
