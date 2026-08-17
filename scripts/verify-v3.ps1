# Verify API - use Invoke-RestMethod and ConvertTo-Json

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
$h = @{Authorization="Bearer $token";"X-Tenant-Id"="1"}
Write-Host "Token OK"

# 2. List instances
Write-Host "`n=== List Process Instances ==="
$listR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/list?pageNo=1&pageSize=10" -Headers $h
Write-Host "Total: $($listR.data.total)"
$listR.data.list | ForEach-Object {
    Write-Host "  [$($_.processInstanceId.Substring(0,8))] status=$($_.status) result=$($_.completionResult)"
}

# 3. Progress for Instance 1
$pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress Instance 1 ==="
try {
    $progR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/progress" -Headers $h
    Write-Host ($progR | ConvertTo-Json -Depth 5)
} catch {
    Write-Host "  Error: $($_.Exception.Message)"
}

# 4. Approval records for Instance 1
Write-Host "`n=== Approval Records Instance 1 ==="
try {
    $recR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/approval-records" -Headers $h
    Write-Host ($recR | ConvertTo-Json -Depth 5)
} catch {
    Write-Host "  Error: $($_.Exception.Message)"
}

# 5. Progress for Instance 5 (running)
$pid5 = "7426e2b6-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Progress Instance 5 ==="
try {
    $prog5R = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid5/progress" -Headers $h
    Write-Host ($prog5R | ConvertTo-Json -Depth 5)
} catch {
    Write-Host "  Error: $($_.Exception.Message)"
}

Write-Host "`n=== Done ==="
