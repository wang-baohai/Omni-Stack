# Verify approval records after comment fix

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
$h = @{Authorization="Bearer $token";"X-Tenant-Id"="1"}
Write-Host "Token OK"

# 2. Check Instance 1 approval records
$pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Instance 1 (office-low) Approval Records ==="
$r1 = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/approval-records" -Headers $h
foreach ($rec in $r1.data) {
    Write-Host "  $($rec.assigneeName) | result=$($rec.result) | comment=$($rec.comment)"
}

# 3. Check Instance 4 approval records (has 2 levels)
$pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Instance 4 (it-high) Approval Records ==="
$r4 = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid4/approval-records" -Headers $h
foreach ($rec in $r4.data) {
    Write-Host "  $($rec.assigneeName) | result=$($rec.result) | comment=$($rec.comment)"
}

# 4. Check Instance 5 approval records
$pid5 = "7426e2b6-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== Instance 5 (office-high) Approval Records ==="
$r5 = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/workflow/process-instance/$pid5/approval-records" -Headers $h
foreach ($rec in $r5.data) {
    Write-Host "  $($rec.assigneeName) | result=$($rec.result) | comment=$($rec.comment)"
}

Write-Host "`n=== Done ==="
