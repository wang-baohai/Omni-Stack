# Verify SRM Overview API works (cold-start retry fix)

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
$h = @{Authorization="Bearer $token";"X-Tenant-Id"="1"}
Write-Host "Token OK"

# 2. Test SRM Overview
Write-Host "`n=== SRM Overview ==="
try {
    $r = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/srm/overview/summary" -Headers $h
    Write-Host "  code=$($r.code), msg=$($r.msg)"
    if ($r.data) { Write-Host "  data keys: $($r.data.PSObject.Properties.Name -join ', ')" }
} catch {
    Write-Host "  ERROR: $($_.ErrorDetails.Message)"
    Write-Host "  Status: $($_.Exception.Response.StatusCode)"
}

# 3. Test Procurement Overview
Write-Host "`n=== Procurement Overview ==="
try {
    $r2 = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/procurement/overview/summary" -Headers $h
    Write-Host "  code=$($r2.code), msg=$($r2.msg)"
} catch {
    Write-Host "  ERROR: $($_.ErrorDetails.Message)"
    Write-Host "  Status: $($_.Exception.Response.StatusCode)"
}

Write-Host "`n=== Done ==="
