# Verify workflow instance page data via API

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
$h = @{Authorization="Bearer $token";"X-Tenant-Id"="1"}
Write-Host "Token OK"

# 2. List all process instances
Write-Host "`n=== List Process Instances ==="
$listR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/list?pageNo=1&pageSize=10" -Headers $h
Write-Host "  Total: $($listR.data.total)"
foreach ($item in $listR.data.list) {
    Write-Host "  [$($item.processInstanceId.Substring(0,8))] $($item.title) | status=$($item.status) | result=$($item.completionResult)"
}

# 3. Test progress endpoint for completed instance (Instance 1)
$pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress for Instance 1 (completed) ==="
try {
    $progR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/progress" -Headers $h
    Write-Host "  Nodes: $($progR.data.Count)"
    foreach ($n in $progR.data) {
        Write-Host "    $($n.name) | status=$($n.status) | assignee=$($n.assigneeName)"
    }
} catch {
    Write-Host "  Error: $($_.ErrorDetails.Message)"
}

# 4. Test approval records for completed instance
Write-Host "`n=== Approval Records for Instance 1 ==="
try {
    $recR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/approval-records" -Headers $h
    Write-Host "  Records: $($recR.data.Count)"
    foreach ($r in $recR.data) {
        Write-Host "    $($r.taskName) | $($r.assigneeName) | approved=$($r.approved) | comment=$($r.comment)"
    }
} catch {
    Write-Host "  Error: $($_.ErrorDetails.Message)"
}

# 5. Test progress for running instance (Instance 2)
$pid2 = "73f9b7d6-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress for Instance 2 (running) ==="
try {
    $progR2 = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid2/progress" -Headers $h
    Write-Host "  Nodes: $($progR2.data.Count)"
    foreach ($n in $progR2.data) {
        Write-Host "    $($n.name) | status=$($n.status) | assignee=$($n.assigneeName)"
    }
} catch {
    Write-Host "  Error: $($_.ErrorDetails.Message)"
}

# 6. Test progress for Instance 4 (completed via manual cleanup)
$pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress for Instance 4 (completed-manual) ==="
try {
    $progR4 = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid4/progress" -Headers $h
    Write-Host "  Nodes: $($progR4.data.Count)"
    foreach ($n in $progR4.data) {
        Write-Host "    $($n.name) | status=$($n.status) | assignee=$($n.assigneeName)"
    }
} catch {
    Write-Host "  Error: $($_.ErrorDetails.Message)"
}

Write-Host "`n=== Verification Complete ==="
