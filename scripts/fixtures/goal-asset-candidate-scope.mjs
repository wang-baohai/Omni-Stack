import assert from 'node:assert/strict'
import fs from 'node:fs'
import {spawnSync} from 'node:child_process'

// 仅补本次专用审批身份的一行配置，不启动服务、不构建应用、不接受任意SQL。
assert.equal(process.cwd(),'/validation/source')
assert(fs.existsSync('/.dockerenv')&&!fs.existsSync('/var/run/docker.sock'))
assert.equal(process.env.OMNI_GOAL_ALLOW_LOCAL_FIXTURE,'2026-10-22')
const action=process.argv[2]
assert(['apply','audit'].includes(action))
const xml=fs.readFileSync('omni-backend/omni-auth/target/surefire-reports/TEST-com.omni.auth.e2e.LocalChallengeFixtureTest.xml','utf8')
const found=xml.match(/<property name="surefire\.test\.class\.path" value="([^"]*)"/)
assert(found)
const cp=found[1].replaceAll('&quot;','"').replaceAll('&apos;',"'").replaceAll('&lt;','<').replaceAll('&gt;','>').replaceAll('&amp;','&')
for(const value of cp.split(':').filter(Boolean))assert((value.startsWith('/validation/source/omni-backend/')||value.startsWith('/validation/cache/m2/'))&&fs.existsSync(value))
const result=spawnSync('java',['--enable-native-access=ALL-UNNAMED','-Djava.io.tmpdir=/validation/test-fixtures/tmp','-cp',cp,'scripts/fixtures/com/omni/auth/e2e/LocalAssetCandidateScopeFixture.java',action],{env:process.env,encoding:'utf8',maxBuffer:4*1024*1024})
const output='/validation/test-fixtures/goal-20261022/asset-candidate-scope-java-output-'+Date.now()+'.json'
fs.writeFileSync(output,JSON.stringify({exit_code:result.status,stdout:result.stdout,stderr:result.stderr}),{mode:0o600})
assert.equal(result.status,0,'配置夹具失败；仅在受限卷检查诊断，禁止输出数据库凭据：'+output)
const evidence='/validation/evidence/codex-2026-10-22/wp28-s02'
const receipts=fs.readdirSync(evidence).filter(name=>/^asset-approver-scope-\d+\.json$/.test(name)).sort()
assert(receipts.length)
const last=JSON.parse(fs.readFileSync(evidence+'/'+receipts.at(-1)))
assert.equal(last.user_id,230);assert.equal(last.role_id,41);assert.equal(last.after?.unit_id,606)
const identityFile='/validation/test-fixtures/goal-20261022/identities.json'
const identity=JSON.parse(fs.readFileSync(identityFile))
assert.equal(identity.accounts.assetApprover.id,230)
const original=receipts.map(name=>({name,data:JSON.parse(fs.readFileSync(evidence+'/'+name))})).find(row=>row.data.created_by_this_run)
assert(original?.data.rollback)
identity.accounts.assetApprover.candidate_scope={id:last.after.id,role_id:41,unit_id:606,scope_mode:'SAME_UNIT',status:1,original_receipt:evidence+'/'+original.name,rollback:original.data.rollback}
fs.writeFileSync(identityFile,JSON.stringify(identity,null,2)+'\n',{mode:0o600});fs.chmodSync(identityFile,0o600)
console.log(JSON.stringify({original_step:'WP-28.S02',result:'OWNED_ASSET_APPROVER_SCOPE_'+action.toUpperCase()+'_COMPLETE',business_state_modified:false,shared_role_modified:false}))
