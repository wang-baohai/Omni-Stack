# === Deploy fixed BPMN and start workflow instances ===

# 1. Get captcha + login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1
$capCode = $capCode.Trim()
Write-Host "Captcha: key=$capKey code=$capCode"

$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token obtained: $($token.Substring(0,50))..."

$h = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id"   = "1"
    "Content-Type"  = "application/json"
}

# 2. Deploy the fixed BPMN XML
$bpmnContent = [System.IO.File]::ReadAllText("C:\WorkSpace\QODER\Omni-Stack\scripts\bpmn\procurement-approval.bpmn20.xml", [System.Text.Encoding]::UTF8)
$deployBody = @{name="procurement-approval";category="purchase";bpmnXml=$bpmnContent} | ConvertTo-Json -Depth 3
$deployBytes = [System.Text.Encoding]::UTF8.GetBytes($deployBody)
$deployR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-definition/deploy" -Headers $h -Body $deployBytes -ContentType "application/json; charset=utf-8"
$deploymentId = $deployR.data
Write-Host "=== Deploy result: deploymentId=$deploymentId ==="

# 3. Get the new process definition ID
Start-Sleep -Seconds 1
$defListR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-definition/list?name=procurement-approval&category=purchase&page=1&size=10" -Headers $h
Write-Host "=== Process definitions ==="
$procDefId = $null
foreach ($def in $defListR.data.records) {
    Write-Host "  id=$($def.id), key=$($def.key), version=$($def.version), deploymentId=$($def.deploymentId)"
    if ($def.deploymentId -eq $deploymentId) {
        $procDefId = $def.id
    }
}
Write-Host "New processDefinitionId: $procDefId"

# 4. Update wf_process_model_version record (id=2) with new deploymentId and processDefinitionId
if ($procDefId) {
    $updateSql = "UPDATE wf_process_model_version SET deployment_id='$deploymentId', process_definition_id='$procDefId' WHERE id=2"
    docker exec omni-mysql mysql -uroot -proot omni_workflow -e "$updateSql" 2>&1
    Write-Host "=== Updated model version 2 with new processDefinitionId ==="
}

# 5. Start 5 process instances
Write-Host ""
Write-Host "=== Starting 5 process instances ==="

# Instance 1: OFFICE_SUPPLY, 4200, zhangsan(100) -> office-low
$b1 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u529e\u516c\u7528\u54c1-\u884c\u653f\u90e8\u6587\u5177\u91c7\u8d2d","businessKey":"SIM-OFFICE-001","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"OFFICE_SUPPLY","totalAmount":4200}}'
try {
    $r1 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-instance/start" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b1)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 1 (office-low): SUCCESS pid=$($r1.data)"
} catch {
    Write-Host "Instance 1 FAILED: $_"
}

# Instance 2: IT_DEVICE, 35400, zhangsan(100) -> it-low (PENDING)
$b2 = '{"modelVersionId":2,"title":"\u91c7\u8d2dIT\u8bbe\u5907-\u6280\u672f\u90e8\u7b14\u8bb0\u672c\u91c7\u8d2d","businessKey":"SIM-IT-002","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"IT_DEVICE","totalAmount":35400}}'
try {
    $r2 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-instance/start" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b2)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 2 (it-low, pending): SUCCESS pid=$($r2.data)"
} catch {
    Write-Host "Instance 2 FAILED: $_"
}

# Instance 3: RAW_MATERIAL, 80000, qianqi(200) -> raw-low
$b3 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u539f\u6750\u6599-\u751f\u4ea7\u7ebf\u94dd\u6750\u8865\u8d27","businessKey":"SIM-RAW-003","category":"purchase","simulateUserId":200,"simulateUserName":"qianqi","variables":{"materialCategory":"RAW_MATERIAL","totalAmount":80000}}'
try {
    $r3 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-instance/start" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b3)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 3 (raw-low): SUCCESS pid=$($r3.data)"
} catch {
    Write-Host "Instance 3 FAILED: $_"
}

# Instance 4: IT_DEVICE, 65000, zhangsan(100) -> it-high (2-level)
$b4 = '{"modelVersionId":2,"title":"\u91c7\u8d2dIT\u8bbe\u5907-\u670d\u52a1\u5668\u96c6\u7fa4\u6269\u5bb9","businessKey":"SIM-IT-004","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"IT_DEVICE","totalAmount":65000}}'
try {
    $r4 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-instance/start" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b4)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 4 (it-high, 2-level): SUCCESS pid=$($r4.data)"
} catch {
    Write-Host "Instance 4 FAILED: $_"
}

# Instance 5: OFFICE_SUPPLY, 15000, zhangsan(100) -> office-high (2-level)
$b5 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u529e\u516c\u7528\u54c1-\u5e74\u5ea6\u529e\u516c\u8017\u6750\u6846\u67b6","businessKey":"SIM-OFFICE-005","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"OFFICE_SUPPLY","totalAmount":15000}}'
try {
    $r5 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-instance/start" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b5)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 5 (office-high, 2-level): SUCCESS pid=$($r5.data)"
} catch {
    Write-Host "Instance 5 FAILED: $_"
}

Write-Host ""
Write-Host "=== Done ==="
