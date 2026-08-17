# Clean up Instance 4 residual tasks and mark as completed

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token OK: $($token.Substring(0,20))..."

# Try to approve remaining CTO tasks via API
$h = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id"   = "1"
    "Content-Type"  = "application/json"
}

# Task 1: user 107
Write-Host "`n=== Trying to approve CTO task (user 107) ==="
$body1 = @{approved=$true;comment="CTO审批通过-补充"} | ConvertTo-Json
try {
    $r1 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/approval/638ce4e3-8af3-11f1-b230-d23947eb06c7/complete" -Headers @{Authorization="Bearer $token";"X-Tenant-Id"="1";"X-User-Id"="107";"Content-Type"="application/json"} -Body ([System.Text.Encoding]::UTF8.GetBytes($body1)) -ContentType "application/json; charset=utf-8"
    Write-Host "  Approved task 638ce4e3 (user=107): OK"
} catch {
    Write-Host "  FAILED: $($_.ErrorDetails.Message)"
}

Start-Sleep -Seconds 1

# Task 2: user 108
Write-Host "`n=== Trying to approve CTO task (user 108) ==="
$body2 = @{approved=$true;comment="CTO审批通过-补充"} | ConvertTo-Json
try {
    $r2 = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/approval/638d0bf8-8af3-11f1-b230-d23947eb06c7/complete" -Headers @{Authorization="Bearer $token";"X-Tenant-Id"="1";"X-User-Id"="108";"Content-Type"="application/json"} -Body ([System.Text.Encoding]::UTF8.GetBytes($body2)) -ContentType "application/json; charset=utf-8"
    Write-Host "  Approved task 638d0bf8 (user=108): OK"
} catch {
    Write-Host "  FAILED: $($_.ErrorDetails.Message)"
}

Start-Sleep -Seconds 1

# Check if process instance 4 is still running
Write-Host "`n=== Check Instance 4 status ==="
$checkSql = "SELECT ID_, END_TIME_ FROM ACT_HI_PROCINST WHERE ID_='7416dcfe-8af2-11f1-b230-d23947eb06c7'"
$checkResult = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "$checkSql" 2>&1
Write-Host "  ACT_HI_PROCINST: $checkResult"

# Check remaining tasks
$taskSql = "SELECT ID_, TASK_DEF_KEY_, ASSIGNEE_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='7416dcfe-8af2-11f1-b230-d23947eb06c7'"
$taskResult = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "$taskSql" 2>&1
Write-Host "  Remaining tasks: $taskResult"

# Check ext table
$extSql = "SELECT status, completion_result FROM wf_process_instance_ext WHERE process_instance_id='7416dcfe-8af2-11f1-b230-d23947eb06c7'"
$extResult = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "$extSql" 2>&1
Write-Host "  Ext table: $extResult"

Write-Host "`n=== Done ==="
