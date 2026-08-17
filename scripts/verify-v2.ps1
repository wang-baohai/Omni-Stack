# Verify API responses with raw JSON output

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
$h = @{Authorization="Bearer $token";"X-Tenant-Id"="1"}

# 2. List instances - show raw JSON
Write-Host "=== List Process Instances ==="
$listRaw = Invoke-WebRequest -Uri "http://localhost:8102/api/workflow/process-instance/list?pageNo=1&pageSize=10" -Headers $h
$listJson = $listRaw.Content | ConvertFrom-Json
Write-Host "Total: $($listJson.data.total)"
foreach ($item in $listJson.data.list) {
    $pidShort = $item.processInstanceId.Substring(0,8)
    Write-Host "  [$pidShort] status=$($item.status) result=$($item.completionResult) title=$($item.title)"
}

# 3. Progress for Instance 1
$pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress Instance 1 ==="
$progRaw = Invoke-WebRequest -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/progress" -Headers $h
$progJson = $progRaw.Content | ConvertFrom-Json
Write-Host "Node count: $($progJson.data.Count)"
foreach ($n in $progJson.data) {
    Write-Host "  $($n.taskName) | status=$($n.status) | assignee=$($n.assigneeName) | result=$($n.completionResult)"
}

# 4. Approval records for Instance 1
Write-Host "`n=== Approval Records Instance 1 ==="
$recRaw = Invoke-WebRequest -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/approval-records" -Headers $h
$recJson = $recRaw.Content | ConvertFrom-Json
Write-Host "Record count: $($recJson.data.Count)"
foreach ($r in $recJson.data) {
    Write-Host "  $($r.taskName) | $($r.assigneeName) | approved=$($r.approved) | comment=$($r.comment)"
}

# 5. Progress for Instance 5 (running, 2 levels)
$pid5 = "7426e2b6-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress Instance 5 ==="
$prog5Raw = Invoke-WebRequest -Uri "http://localhost:8102/api/workflow/process-instance/$pid5/progress" -Headers $h
$prog5Json = $prog5Raw.Content | ConvertFrom-Json
Write-Host "Node count: $($prog5Json.data.Count)"
foreach ($n in $prog5Json.data) {
    Write-Host "  $($n.taskName) | status=$($n.status) | assignee=$($n.assigneeName)"
}

Write-Host "`n=== Done ==="
