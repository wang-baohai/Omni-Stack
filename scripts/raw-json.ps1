# Verify approval records - output raw JSON to file then read
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
$h = @{Authorization="Bearer $token";"X-Tenant-Id"="1"}

$pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
$resp = Invoke-WebRequest -Uri "http://localhost:8102/api/workflow/process-instance/$pid1/approval-records" -Headers $h
[System.IO.File]::WriteAllText("c:\WorkSpace\QODER\Omni-Stack\scripts\api-output.json", $resp.Content, [System.Text.Encoding]::UTF8)
Write-Host "Written to api-output.json"
