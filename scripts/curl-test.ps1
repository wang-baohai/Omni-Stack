# Use curl to get raw API response with proper encoding

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token OK"

# 2. Use docker curl to get raw response
$pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
Write-Host "`n=== curl API call ==="
$result = docker exec omni-mysql curl -s -H "Authorization: Bearer $token" -H "X-Tenant-Id: 1" "http://host.docker.internal:8102/api/workflow/process-instance/$pid4/approval-records" 2>&1
Write-Host $result
