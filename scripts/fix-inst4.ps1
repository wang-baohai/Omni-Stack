# Fix Instance 4 - complete remaining CTO task, then verify all

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken

function Approve-Task($taskId, $userId, $comment) {
    $h = @{
        "Authorization" = "Bearer $token"
        "X-Tenant-Id"   = "1"
        "X-User-Id"     = "$userId"
        "Content-Type"  = "application/json"
    }
    $body = @{approved=$true;comment=$comment} | ConvertTo-Json
    try {
        Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/approval/$taskId/complete" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -ContentType "application/json; charset=utf-8" | Out-Null
        Write-Host "  Approved task $taskId (user=$userId): OK"
    } catch {
        Write-Host "  FAILED: $($_.ErrorDetails.Message)"
    }
}

# Complete remaining CTO task for Instance 4 (user 108)
Write-Host "=== Instance 4: complete CTO task (user 108) ==="
Approve-Task "638d0bf8-8af3-11f1-b230-d23947eb06c7" 108 "CTO审批通过"

Start-Sleep -Seconds 1

# Check remaining tasks
Write-Host "`n=== Remaining tasks ==="
$allTasks = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "SELECT PROC_INST_ID_, TASK_DEF_KEY_, ASSIGNEE_ FROM ACT_RU_TASK ORDER BY PROC_INST_ID_" 2>&1 | Where-Object { $_ -notmatch "Warning" -and $_ -notmatch "Error" -and $_.Trim() -ne "" }
foreach ($t in $allTasks) { Write-Host "  $t" }

# Check ext table
Write-Host "`n=== wf_process_instance_ext ==="
docker exec omni-mysql mysql -uroot -proot omni_workflow -e "SELECT SUBSTRING(process_instance_id,1,8) as pid_short, title, status, completion_result FROM wf_process_instance_ext" 2>&1 | Where-Object { $_ -notmatch "Warning" }

# Check ACT_HI_PROCINST for completed instances
Write-Host "`n=== ACT_HI_PROCINST (completed) ==="
docker exec omni-mysql mysql -uroot -proot omni_workflow -e "SELECT SUBSTRING(ID_,1,8) as pid, END_TIME_ IS NOT NULL as is_completed FROM ACT_HI_PROCINST" 2>&1 | Where-Object { $_ -notmatch "Warning" }

# Check ACT_RU_EXECUTION for running instances
Write-Host "`n=== Running instances (ACT_RU_EXECUTION root) ==="
docker exec omni-mysql mysql -uroot -proot omni_workflow -e "SELECT PROC_INST_ID_, ACT_ID_ FROM ACT_RU_EXECUTION WHERE PARENT_ID_ IS NULL" 2>&1 | Where-Object { $_ -notmatch "Warning" }

Write-Host "`n=== Done ==="
