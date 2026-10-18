/** 本机 Docker 内只读采集十类系统页面，使用真实 API，不提交配置或业务修改。 */
import assert from 'node:assert/strict'
import {createHash} from 'node:crypto'
import fs from 'node:fs'
import {createRequire} from 'node:module'
import {resolve} from 'node:path'
assert.ok(fs.existsSync('/.dockerenv'))
const require=createRequire('/workspace/omni-frontend/package.json'),{chromium,expect}=require('@playwright/test'),e='/evidence',sha=b=>createHash('sha256').update(b).digest('hex')
const tokenPath=e+'/security-tokens-398.private.json',receiptPath=e+'/security-lists-capture-398.json'
assert.ok(!fs.existsSync(receiptPath),'Keep prior attempt receipts')
const scenarios=[
 {name:'system-tenants',route:'/admin/system/tenant',apis:['/api/auth/tenant/list'],view:'system/tenant'},
 {name:'system-organizations',route:'/admin/system/org',apis:['/api/auth/org/tree'],view:'system/org'},
 {name:'system-roles',route:'/admin/system/role',apis:['/api/auth/role/list'],view:'system/role'},
 {name:'system-permissions',route:'/admin/system/permission',apis:['/api/auth/permission/tree'],view:'system/permission'},
 {name:'system-dictionaries',route:'/admin/base/dict',apis:['/api/base/dict/type/list'],view:'base/dict'},
 {name:'system-online-users',route:'/admin/system/online',apis:['/api/auth/online/list'],view:'system/online'},
 {name:'system-audit-log',route:'/admin/system/auditlog',apis:['/api/auth/audit-log/list'],view:'system/auditlog'},
 {name:'system-auth-records',route:'/admin/system/authrecord',apis:['/api/auth/auth-record/list'],view:'system/authrecord'},
 {name:'system-xss-config',route:'/admin/system/xssconfig',apis:['/api/auth/xss-config/settings','/api/auth/xss-config/rules/list'],view:'system/xssconfig'},
 {name:'system-operation-log',route:'/admin/base/operlog',apis:['/api/base/oper-log/list'],view:'monitor/oper-log'}
]
const sourcePaths=[...scenarios.map(s=>'omni-frontend/src/views/'+s.view+'/index.vue'),'omni-frontend/src/router/index.ts',...['tenant','org','role','permission','dict','online','audit-log','auth-record','xss-config','oper-log','system-config','request'].map(s=>'omni-frontend/src/api/'+s+'.ts'),'omni-frontend/src/i18n/index.ts','docker/frontend/nginx.conf','omni-backend/omni-auth/src/test/java/com/omni/auth/e2e/E2eTokenFixture.java']
const sources=sourcePaths.map(path=>({path,sha256:sha(fs.readFileSync('/workspace/'+path))}))
const receipt={schema:1,started_at:new Date().toISOString(),phase:'STARTING',thread_id:'01a13442-0969-7ba1-b2fd-fd8329b21c7b',head:'7a4219e9a4469966c723eb5cac503e138b046f26',base:'http://omni-frontend:3000',runtime_journal:'security-runtime-journal-395c.json',artifact_proof:'permissions-runtime-artifact-proof-370.json',identity_method:'Current test-classpath E2eTokenFixture;enabled DB users and actual roles/permissions;short-lived1200second tokens;not password/CAPTCHA login',viewport:{width:1680,height:1050},sources,source_sha256:sha(JSON.stringify(sources)),scenarios,captures:[],observations:[],guard_checks:[],blocked_mutations:[],page_errors:[],errors:[],forms_submitted:false,mocks:false,token_file_removed:false,visual_review:'PENDING_INDIVIDUAL_RAW_IMAGE_INSPECTION'}
function save(){fs.writeFileSync(receiptPath+'.next',JSON.stringify(receipt,null,2)+'\n');fs.renameSync(receiptPath+'.next',receiptPath)}
save();let browser
try{
 const tokens=JSON.parse(fs.readFileSync(tokenPath,'utf8')),claims=JSON.parse(Buffer.from(tokens.adminToken.split('.')[1],'base64url').toString())
 assert.ok(claims.exp*1000>Date.now()+180000);assert.equal(claims.tenant_id,1);assert.ok(claims.roles.includes('SUPER_ADMIN'))
 receipt.actor={username:'admin',tenantId:claims.tenant_id,roles:claims.roles,expires_at:new Date(claims.exp*1000).toISOString()};save()
 for(const [actor,token,expected]of [['admin',tokens.adminToken,200],['employee',tokens.employeeToken,403]]){
  const endpoint='/api/auth/system-config/login-policy',res=await fetch(receipt.base+endpoint,{headers:{Authorization:'Bearer '+token,'X-Tenant-Id':'1'},signal:AbortSignal.timeout(20000)}),body=await res.json()
  receipt.guard_checks.push({at:new Date().toISOString(),actor,method:'GET',path:endpoint,http:res.status,code:body.code,policy:expected===200?body.data:undefined});save();assert.equal(res.status,expected);assert.equal(body.code,expected)
 }
 browser=await chromium.launch();receipt.tools={node:process.version,playwright:require('@playwright/test/package.json').version,chromium:browser.version()};save()
 for(const locale of ['zh-CN','en-US','ja-JP','ko-KR'])for(const scenario of scenarios){
  const context=await browser.newContext({baseURL:receipt.base,viewport:receipt.viewport,locale,timezoneId:'Asia/Shanghai',reducedMotion:'reduce'})
  const responses=[];let page
  receipt.current_scenario={locale,name:scenario.name,route:scenario.route};save()
  try{
   page=await context.newPage()
   page.on('pageerror',error=>{receipt.page_errors.push({locale,scenario:scenario.name,message:String(error.message).slice(0,300)});save()})
   page.on('response',response=>{if(response.url().includes('/api/'))responses.push({path:new URL(response.url()).pathname,http:response.status()})})
   await page.route('**/api/**',async route=>{const request=route.request();if(['GET','HEAD','OPTIONS'].includes(request.method()))await route.continue();else{receipt.blocked_mutations.push({locale,scenario:scenario.name,method:request.method(),path:new URL(request.url()).pathname});save();await route.abort('blockedbyclient')}})
   await page.addInitScript(({locale,token})=>{localStorage.setItem('omni-lang',locale);localStorage.setItem('token',token);localStorage.setItem('username','admin')},{locale,token:tokens.adminToken})
   const ready=Promise.all(scenario.apis.map(api=>page.waitForResponse(res=>new URL(res.url()).pathname===api&&res.request().method()==='GET',{timeout:25000})))
   const [,apiResponses]=await Promise.all([page.goto(scenario.route,{waitUntil:'domcontentloaded'}),ready])
   const dataSummaries=[]
   for(let i=0;i<apiResponses.length;i++){
    const res=apiResponses[i],body=await res.json(),data=body.data
    const summary={at:new Date().toISOString(),locale,scenario:scenario.name,method:'GET',path:scenario.apis[i],http:res.status(),code:body.code,data_kind:Array.isArray(data)?'array':typeof data,records:Array.isArray(data)?data.length:Array.isArray(data?.records)?data.records.length:null,total:typeof data?.total==='number'?data.total:null}
    receipt.observations.push(summary);dataSummaries.push(summary);save();assert.equal(res.status(),200,scenario.apis[i]);assert.equal(body.code,200,scenario.apis[i])
   }
   await expect(page).toHaveURL(receipt.base+scenario.route)
   await expect(page.locator('main')).toBeVisible()
   await expect(page.locator('.el-card').first()).toBeVisible()
   await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
   await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
   await expect(page.locator('.el-message--error:visible')).toHaveCount(0)
   assert.equal(await page.locator('html').getAttribute('lang'),locale)
   await page.evaluate(async()=>{await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))})
   const heading=(await page.locator('.card-header').allTextContents()).map(x=>x.trim()).filter(Boolean).slice(0,3)
   const metrics=await page.locator('main').evaluate(el=>({width:el.clientWidth,scrollWidth:el.scrollWidth,tableRows:el.querySelectorAll('.el-table__body-wrapper tbody tr').length,emptyStates:[...el.querySelectorAll('.el-table__empty-text,.el-empty__description')].map(x=>x.textContent?.trim())}))
   const folder=resolve(e,'security-lists-398',locale);fs.mkdirSync(folder,{recursive:true});const file=resolve(folder,scenario.name+'.png');assert.ok(!fs.existsSync(file))
   await page.screenshot({path:file,fullPage:false,animations:'disabled',mask:[page.locator('input[type="password"],.captcha-image,.captcha-img,[data-docs-mask="true"]')]})
   receipt.captures.push({locale,name:scenario.name,image:'security-lists-398/'+locale+'/'+scenario.name+'.png',target:'docs/images/'+locale+'/'+scenario.name+'.png',route:scenario.route,viewport:receipt.viewport,captured_at:new Date().toISOString(),sha256:sha(fs.readFileSync(file)),heading,metrics,api_observations:dataSummaries,responses,visual_review:'PENDING'});receipt.phase='CAPTURED_'+receipt.captures.length+'_OF_40';save()
  }catch(error){receipt.failed_scenario={locale,name:scenario.name,route:scenario.route,actual_url:page?.url(),responses};save();throw error}finally{await context.close()}
 }
 assert.equal(receipt.captures.length,40);assert.equal(receipt.observations.length,44);assert.equal(receipt.blocked_mutations.length,0);assert.equal(receipt.page_errors.length,0)
 receipt.phase='CAPTURE_COMPLETE_PENDING_40_RAW_IMAGE_REVIEWS';receipt.current_scenario=null;save()
}catch(error){receipt.phase='FAILED';receipt.errors.push({category:error.name,message:String(error.message).slice(0,600)});save();throw error}
finally{if(browser)await browser.close();if(fs.existsSync(tokenPath)){fs.unlinkSync(tokenPath);receipt.token_file_removed=true}receipt.finished_at=new Date().toISOString();save()}
process.stdout.write(JSON.stringify({phase:receipt.phase,captures:receipt.captures.length,real_get_observations:receipt.observations.length,blocked_mutations:receipt.blocked_mutations.length,token_file_removed:receipt.token_file_removed})+'\n')
