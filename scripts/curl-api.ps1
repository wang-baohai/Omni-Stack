# Get token and call API from inside workflow container

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token: $($token.Substring(0,30))..."

# 2. Call from workflow container using curl, save to file
$pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
docker exec omni-workflow curl -s -H "Authorization: Bearer $token" -H "X-Tenant-Id: 1" "http://localhost:8080/api/workflow/process-instance/$pid4/approval-records" -o /tmp/api-result.json 2>&1

# 3. Copy file out and read
docker cp omni-workflow:/tmp/api-result.json c:\WorkSpace\QODER\Omni-Stack\scripts\api-result.json 2>&1
Write-Host "`nRaw JSON:"
$content = [System.IO.File]::ReadAllText("c:\WorkSpace\QODER\Omni-Stack\scripts\api-result.json", [System.Text.Encoding]::UTF8)
Write-Host $content
