/** 仅对新建、无宿主端口的 core 隔离项目，经正式角色 API 建立并恢复临时只读夹具。 */
import fs from 'node:fs'
import assert from 'node:assert/strict'
assert.ok(fs.existsSync('/.dockerenv'))
const e='/evidence',file=e+'/core-viewer-role-fixture-646.json',mode=process.argv[2],base='http://omni-frontend:3000'
assert.ok(['grant','restore'].includes(mode))
const journal=JSON.parse(fs.readFileSync(e+'/generated-core-runtime-journal-644.json','utf8'))
assert.equal(journal.thread_id,'01a13442-0969-7ba1-b2fd-fd8329b21c7b');assert.equal(journal.host_ports_published,0);assert.equal(journal.original_data_volumes_mounted,false);assert.equal(journal.network,'omni-generated-core-644')
const token=JSON.parse(fs.readFileSync(e+'/generated-core-initial-tokens-647.private.json','utf8')).admin
const claims=JSON.parse(Buffer.from(token.split('.')[1],'base64url').toString());assert.ok(claims.roles.includes('SUPER_ADMIN'));assert.ok(claims.exp*1000>Date.now()+60000)
let r
const save=()=>fs.writeFileSync(file,JSON.stringify(r,null,2)+'\n')
const request=async(method,url,body)=>{const res=await fetch(base+url,{method,headers:{Authorization:'Bearer '+token,'X-Tenant-Id':'1',...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(20000)});const result=await res.json();assert.equal(res.status,200);assert.equal(result.code,200);return result.data}
const sorted=a=>a.map(Number).sort((a,b)=>a-b)
if(mode==='grant'){
 assert.ok(!fs.existsSync(file),'Existing grant intent must be reconciled, never blindly repeated')
 const users=(await request('GET','/api/auth/user/list?page=1&size=100')).records,roles=(await request('GET','/api/auth/role/list?page=1&size=100')).records
 const selected=users.filter(u=>u.username==='zhangsan'&&Number(u.tenantId)===1&&u.status===1);assert.equal(selected.length,1);const user=selected[0];assert.equal(Number(user.id),100)
 const viewer=roles.filter(v=>v.roleCode==='USER'&&v.status===1);assert.equal(viewer.length,1);assert.equal(Number(viewer[0].id),2)
 const before=sorted(await request('GET','/api/auth/user/'+user.id+'/roles'));assert.deepEqual(before,[10])
 r={at:new Date().toISOString(),phase:'GRANT_INTENT',thread_id:journal.thread_id,runtime_journal:'generated-core-runtime-journal-644.json',scope:'Temporary existing USER read-only role on zhangsan in fresh owned core DB only;use real DB claims;restore exact original role set',user_id:Number(user.id),username:'zhangsan',tenant_id:1,role_id:Number(viewer[0].id),before_roles:before,fixture_roles:sorted([...before,Number(viewer[0].id)]),formal_API_mutations:[],direct_SQL_writes:0,global_role_permissions_changed:false,original_data_used:false};save()
 r.formal_API_mutations.push({at:new Date().toISOString(),action:'GRANT_READONLY_FIXTURE',method:'POST',path:'/api/auth/user/100/roles',body:r.fixture_roles,status:'REQUEST_INTENT'});save()
 await request('POST','/api/auth/user/100/roles',r.fixture_roles)
 const actual=sorted(await request('GET','/api/auth/user/100/roles'));assert.deepEqual(actual,r.fixture_roles);r.formal_API_mutations.at(-1).status='CONFIRMED_200_AND_READBACK';r.phase='GRANTED_TEMPORARY_OWNED_FIXTURE';save()
}else{
 r=JSON.parse(fs.readFileSync(file,'utf8'));assert.equal(r.user_id,100);assert.equal(r.runtime_journal,'generated-core-runtime-journal-644.json');assert.deepEqual(r.before_roles,[10]);assert.deepEqual(r.fixture_roles,[2,10])
 const actual=sorted(await request('GET','/api/auth/user/100/roles'))
 if(JSON.stringify(actual)===JSON.stringify(r.before_roles)){r.phase='RESTORED_EXACT_ORIGINAL_ROLES';r.restore_write_repeated=false;save()}
 else{
  assert.deepEqual(actual,r.fixture_roles,'Unexpected role change;preserve and require reconciliation')
  assert.ok(!r.formal_API_mutations.some(v=>v.action==='RESTORE_ORIGINAL_ROLES'),'Restore intent exists;read actual state before any retry')
  r.phase='RESTORE_INTENT';r.formal_API_mutations.push({at:new Date().toISOString(),action:'RESTORE_ORIGINAL_ROLES',method:'POST',path:'/api/auth/user/100/roles',body:r.before_roles,status:'REQUEST_INTENT'});save()
  await request('POST','/api/auth/user/100/roles',r.before_roles)
  assert.deepEqual(sorted(await request('GET','/api/auth/user/100/roles')),r.before_roles);r.formal_API_mutations.at(-1).status='CONFIRMED_200_AND_READBACK';r.phase='RESTORED_EXACT_ORIGINAL_ROLES';r.restored_at=new Date().toISOString();save()
 }
}
console.log(JSON.stringify({phase:r.phase,username:r.username,before_roles:r.before_roles,fixture_roles:r.fixture_roles,formal_API_mutations:r.formal_API_mutations.length,direct_SQL_writes:r.direct_SQL_writes}))
