/** 实际生成 core 的四语拒绝与故障界面；拒绝使用真实后端，500 仅在浏览器显式注入。 */
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
assert.ok(fs.existsSync('/.dockerenv'))
const root='/workspace',e='/evidence',out=e+'/generated-core-error-captures-647.json',tokenFile=e+'/generated-core-tokens-647.private.json'
assert.ok(!fs.existsSync(out))
const require=createRequire(root+'/omni-frontend/package.json'),{chromium,expect}=require('@playwright/test')
const sha=b=>crypto.createHash('sha256').update(b).digest('hex')
const sourcePaths=['omni-frontend/src/router/index.ts','omni-frontend/src/views/error/status/index.vue','omni-frontend/src/views/system/user/index.vue','omni-frontend/src/api/request.ts','omni-frontend/src/api/user.ts','omni-frontend/src/api/menu.ts','omni-frontend/src/api/system-config.ts','omni-backend/omni-auth/src/main/java/com/omni/auth/controller/SystemConfigController.java','omni-frontend/src/stores/user.ts','omni-frontend/src/stores/permission.ts','omni-frontend/src/utils/access.ts','docker/frontend/nginx.conf','omni-backend/omni-auth/src/test/java/com/omni/auth/e2e/Wp008TokenFixture.java',...['zh-CN','en-US','ja-JP','ko-KR'].map(l=>'omni-frontend/src/locales/'+l+'.ts')]
const sources=sourcePaths.map(p=>({path:p,sha256:sha(fs.readFileSync(root+'/'+p))}))
const r={schema:1,at:new Date().toISOString(),phase:'STARTING',thread_id:'01a13442-0969-7ba1-b2fd-fd8329b21c7b',runtime:'local Docker',runtime_journal:'generated-core-runtime-journal-644.json',application_build:'generated-core-application-build-636.json',generated_project_source_head:'2ccb46cc281d176b4d443cb78b2a4a5598e8aef4',generated_project_volume:'omni-preset-independent-633-core-files',base:'http://omni-frontend:3000',preset:'core',native_profile:'lite',viewport:{width:1680,height:1050},identity_method:'Unmodified generated core Wp008TokenFixture test classpath;read-only enabled users and actual database role/permission claims;1200s tokens;not password/CAPTCHA login',sources,source_sha256:sha(JSON.stringify(sources)),captures:[],observations:[],blocked_mutations:[],external_requests:[],page_errors:[],recoveries:[],errors:[],forms_submitted:false,DOM_text_replaced:false,forbidden_403_mocked:false,temporary_role_fixture:'core-viewer-role-fixture-646.json;formal role API grants existing USER read-only role in owned core only;exact original roles restored after capture',API_500_scope:'Explicit deterministic client fault injection for user-list GET only;not backend outage proof',token_file_removed:false,visual_review:'PENDING_INDIVIDUAL_ORIGINAL_IMAGE_INSPECTION'}
const save=()=>fs.writeFileSync(out,JSON.stringify(r,null,2)+'\n')
save();let browser
const messages=Object.fromEntries(['zh-CN','en-US','ja-JP','ko-KR'].map(l=>{const text=fs.readFileSync(root+'/omni-frontend/src/locales/'+l+'.ts','utf8');const read=k=>{const m=new RegExp('\\b'+k+":\\s*'([^']+)'",'u').exec(text);assert.ok(m,k+' '+l);return m[1]};return [l,{forbiddenTitle:read('forbiddenTitle'),serverError:read('serverError')}]}))
try{
 const tokens=JSON.parse(fs.readFileSync(tokenFile,'utf8'));assert.ok(tokens.admin&&tokens.employee)
 const decode=token=>JSON.parse(Buffer.from(token.split('.')[1],'base64url').toString())
 const admin=decode(tokens.admin),employee=decode(tokens.employee)
 assert.ok(admin.exp*1000>Date.now()+120000);assert.ok(employee.exp*1000>Date.now()+120000)
 assert.ok(admin.roles.includes('SUPER_ADMIN'));assert.ok(employee.roles.includes('EMPLOYEE'));assert.ok(!employee.roles.includes('SUPER_ADMIN'));assert.ok(employee.roles.includes('USER'));assert.ok(employee.scope.includes('system:user:list'));assert.ok(!employee.scope.includes('system:config:list'))
 r.actors={admin:{tenant:admin.tenant_id,roles:admin.roles},employee:{tenant:employee.tenant_id,roles:employee.roles,configuration_permission:false,temporary_readonly_USER_role:true}};save()
 const probe=async(actor,token)=>{const response=await fetch(r.base+'/api/auth/system-config/login-policy',{headers:{Authorization:'Bearer '+token,'X-Tenant-Id':'1'},signal:AbortSignal.timeout(20000)});const body=await response.json();const item={actor,path:'/api/auth/system-config/login-policy',http:response.status,business_code:body.code,total:body.data?.total};r.observations.push(item);save();return item}
 const allow=await probe('admin',tokens.admin),deny=await probe('employee',tokens.employee)
 assert.equal(allow.http,200);assert.equal(allow.business_code,200);assert.equal(deny.http,403);assert.equal(deny.business_code,403)
 browser=await chromium.launch();r.tools={node:process.version,playwright:require('@playwright/test/package.json').version,chromium:browser.version()};save()
 const capture=async(page,locale,name,extra)=>{
  await page.evaluate(async()=>{await document.fonts.ready;await new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(done)))})
  const image='generated-core-errors-647/'+locale+'/'+name+'.png',dest=e+'/'+image
  fs.mkdirSync(path.dirname(dest),{recursive:true});assert.ok(!fs.existsSync(dest))
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  const metrics=await page.evaluate(()=>({viewport:innerWidth,document_width:document.documentElement.scrollWidth,body_width:document.body.scrollWidth}))
  assert.ok(metrics.document_width<=metrics.viewport+1,'Unexpected whole-page horizontal overflow')
  await page.screenshot({path:dest,fullPage:false,animations:'disabled'})
  r.captures.push({locale,id:name,image,target:'docs/images/'+locale+'/'+name+'.png',module:'permissions-exceptions',route:'/admin/system/user',captured_at:new Date().toISOString(),sha256:sha(fs.readFileSync(dest)),viewport:r.viewport,metrics,visual_review:'PENDING',...extra});save()
 }
 for(const locale of ['zh-CN','en-US','ja-JP','ko-KR'])for(const scenario of ['forbidden','failure']){
  const context=await browser.newContext({viewport:r.viewport,baseURL:r.base,locale,timezoneId:'Asia/Shanghai',reducedMotion:'reduce'})
  let injected=false
  try{
   const page=await context.newPage(),responses=[]
   page.on('pageerror',err=>{r.page_errors.push({locale,scenario,during_explicit_fault:injected,message:String(err.message).slice(0,300)});save()})
   page.on('response',res=>{const url=new URL(res.url());if(url.pathname.startsWith('/api/'))responses.push({path:url.pathname,http:res.status()})})
   await page.route('**/*',async route=>{
    const request=route.request(),url=new URL(request.url())
    if(url.origin!==r.base){r.external_requests.push({locale,scenario,origin:url.origin,path:url.pathname});save();await route.abort('blockedbyclient');return}
    if(!['GET','HEAD','OPTIONS'].includes(request.method())){r.blocked_mutations.push({locale,scenario,method:request.method(),path:url.pathname});save();await route.abort('blockedbyclient');return}
    await route.continue()
   })
   const token=tokens[scenario==='forbidden'?'employee':'admin']
   await page.addInitScript(({locale,token,name})=>{localStorage.setItem('omni-lang',locale);localStorage.setItem('token',token);localStorage.setItem('username',name)},{locale,token,name:scenario==='forbidden'?'zhangsan':'admin'})
   if(scenario==='forbidden'){
    await page.goto('/admin/system/config',{waitUntil:'domcontentloaded'})
    await expect(page.locator('.status-page .el-result__title')).toHaveText('403 '+messages[locale].forbiddenTitle)
    await expect(page).toHaveURL('/admin/system/config');assert.equal(await page.evaluate(()=>Boolean(localStorage.getItem('token'))),true)
    assert.ok(responses.some(x=>x.path==='/api/auth/menus'&&x.http===200))
    await capture(page,locale,'core-config-forbidden-403',{route:'/admin/system/config',flow:'forbidden-403',actor:'employee',HTTP_403_separate_live_API_observation:deny,client_fault_injection:false,responses})
   }else{
    const first=page.waitForResponse(res=>new URL(res.url()).pathname==='/api/auth/user/list'&&res.status()===200)
    await page.goto('/admin/system/user',{waitUntil:'domcontentloaded'});assert.equal((await (await first).json()).code,200)
    await expect(page.locator('.user-table .el-table__body-wrapper tbody tr')).not.toHaveCount(0)
    const fault=async route=>{assert.equal(route.request().method(),'GET');await route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({code:500,message:'deliberate client-only documentation test'})})}
    await page.route('**/api/auth/user/list*',fault);injected=true
    const failure=page.waitForResponse(res=>new URL(res.url()).pathname==='/api/auth/user/list'&&res.status()===500)
    await page.reload({waitUntil:'domcontentloaded'});assert.equal((await failure).status(),500)
    await expect(page.locator('.el-message--error').filter({hasText:messages[locale].serverError}).first()).toBeVisible()
    await expect(page).toHaveURL('/admin/system/user');assert.equal(await page.evaluate(()=>Boolean(localStorage.getItem('token'))),true)
    assert.ok(!(await page.locator('body').innerText()).includes('deliberate client-only documentation test'))
    await capture(page,locale,'core-user-list-failure',{flow:'api-failure',actor:'admin',client_fault_injection:true,HTTP:500,localized_error:messages[locale].serverError,responses})
    await page.unroute('**/api/auth/user/list*',fault);injected=false
    const restored=page.waitForResponse(res=>new URL(res.url()).pathname==='/api/auth/user/list'&&res.status()===200)
    await page.reload({waitUntil:'domcontentloaded'});const result=await (await restored).json();assert.equal(result.code,200);assert.ok(result.data.records.length>0)
    await expect(page.locator('.user-table .el-table__body-wrapper tbody tr')).toHaveCount(result.data.records.length)
    await expect(page.locator('.el-message--error')).toHaveCount(0)
    r.recoveries.push({locale,path:'/api/auth/user/list',action:'Remove explicit client fault and reload native page',HTTP:200,business_code:result.code,visible_rows:result.data.records.length,session_retained:true});save()
   }
  }finally{await context.close()}
 }
 assert.equal(r.captures.length,8);assert.equal(r.recoveries.length,4);assert.equal(r.blocked_mutations.length,0);assert.equal(r.external_requests.length,0)
 assert.ok(r.page_errors.every(item=>item.scenario==='failure'&&item.during_explicit_fault),'Unrelated native page error')
 r.phase='CAPTURE_AND_RECOVERY_PASS_PENDING_RAW_VISUAL_REVIEW';save()
}catch(err){r.phase='FAILED_WITH_COMPLETED_SIDE_EFFECTS_PRESERVED';r.errors.push({category:err.name,message:String(err.message).slice(0,600)});save();throw err}
finally{if(browser)await browser.close();if(fs.existsSync(tokenFile)){fs.unlinkSync(tokenFile);r.token_file_removed=true};r.ended_at=new Date().toISOString();save()}
console.log(JSON.stringify({phase:r.phase,captures:r.captures.length,recoveries:r.recoveries.length,real_API_observations:r.observations,blocked_mutations:r.blocked_mutations.length,external_requests:r.external_requests.length,page_errors_during_explicit_fault:r.page_errors.length,token_file_removed:r.token_file_removed}))
