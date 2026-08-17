# Complete approvals for instances 1, 3, 4 (all), instance 5 (first level only)

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token OK"

function Approve-Task($taskId, $userId, $comment) {
    $h = @{
        "Authorization" = "Bearer $token"
        "X-Tenant-Id"   = "1"
        "X-User-Id"     = "$userId"
        "Content-Type"  = "application/json"
    }
    $body = @{approved=$true;comment=$comment} | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/approval/$taskId/complete" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -ContentType "application/json; charset=utf-8"
        Write-Host "  Approved task $taskId (user=$userId): OK"
        return $true
    } catch {
        Write-Host "  FAILED task $taskId (user=$userId): $($_.ErrorDetails.Message)"
        return $false
    }
}

# Get pending tasks from DB
function Get-Tasks($procInstId) {
    $sql = "SELECT ID_, ASSIGNEE_, TASK_DEF_KEY_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='$procInstId'"
    $result = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "$sql" 2>&1
    return $result | Where-Object { $_ -notmatch "Warning" -and $_ -notmatch "Error" -and $_.Trim() -ne "" }
}

$pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
$pid2 = "73f9b7d6-8af2-11f1-b230-d23947eb06c7"
$pid3 = "eef8b06e-8af2-11f1-b230-d23947eb06c7"
$pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
$pid5 = "7426e2b6-8af2-11f1-b230-d23947eb06c7"

# === Instance 1 (office-low): ANY mode, approve 1 task ===
Write-Host "`n=== Instance 1 (office-low): approve 1 task ==="
$tasks = Get-Tasks $pid1
Write-Host "  Tasks: $tasks"
Approve-Task "73e03c63-8af2-11f1-b230-d23947eb06c7" 1 "同意采购"

# === Instance 3 (raw-low): ANY mode, approve 1 task ===
Write-Host "`n=== Instance 3 (raw-low): approve 1 task ==="
$tasks = Get-Tasks $pid3
Write-Host "  Tasks: $tasks"
Approve-Task "eefb208c-8af2-11f1-b230-d23947eb06c7" 1 "材料补货审批通过"

# === Instance 4 (it-high): 2-level, first approve level 1, then level 2 ===
Write-Host "`n=== Instance 4 (it-high): level 1 ==="
$tasks = Get-Tasks $pid4
Write-Host "  Tasks: $tasks"
Approve-Task "741ad4c3-8af2-11f1-b230-d23947eb06c7" 1 "部门审批通过"

Start-Sleep -Seconds 1

# Check level 2 tasks
Write-Host "`n=== Instance 4 (it-high): level 2 (CTO) ==="
$tasks2 = Get-Tasks $pid4
Write-Host "  Tasks: $tasks2"
foreach ($line in $tasks2) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 2) {
        $tid = $parts[0].Trim()
        $assignee = $parts[1].Trim()
        Write-Host "  Approving level 2: taskId=$tid, assignee=$assignee"
        Approve-Task $tid ([int]$assignee) "CTO审批通过"
        break  # ANY mode, only need 1
    }
}

# === Instance 5 (office-high): first level only ===
Write-Host "`n=== Instance 5 (office-high): level 1 only ==="
$tasks = Get-Tasks $pid5
Write-Host "  Tasks: $tasks"
Approve-Task "742dc0ab-8af2-11f1-b230-d23947eb06c7" 1 "行政审批通过"

# Verify remaining tasks
Start-Sleep -Seconds 1
Write-Host "`n=== Remaining tasks ==="
$allTasks = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "SELECT PROC_INST_ID_, TASK_DEF_KEY_, ASSIGNEE_ FROM ACT_RU_TASK ORDER BY PROC_INST_ID_" 2>&1 | Where-Object { $_ -notmatch "Warning" -and $_ -notmatch "Error" -and $_.Trim() -ne "" }
foreach ($t in $allTasks) { Write-Host "  $t" }

Write-Host "`n=== Ext table ==="
$ext = docker exec omni-mysql mysql -uroot -proot -N omni_workflow -e "SELECT process_instance_id, title, status, completion_result FROM wf_process_instance_ext" 2>&1 | Where-Object { $_ -notmatch "Warning" -and $_ -notmatch "Error" -and $_.Trim() -ne "" }
foreach ($e in $ext) { Write-Host "  $e" }

Write-Host "`n=== Done ==="
