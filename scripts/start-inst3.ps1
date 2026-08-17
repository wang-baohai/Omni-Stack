# Start Instance 3 with zhangsan(100) instead of qianqi(200)
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()

$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token OK"

$h = @{"Authorization"="Bearer $token";"X-Tenant-Id"="1";"Content-Type"="application/json"}

# Instance 3: RAW_MATERIAL, 80000, zhangsan(100) -> raw-low
$b3 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u539f\u6750\u6599-\u751f\u4ea7\u7ebf\u94dd\u6750\u8865\u8d27","businessKey":"SIM-RAW-003","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"RAW_MATERIAL","totalAmount":80000}}'
try {
    $r = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/workflow/process-instance/start" -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b3)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 3 (raw-low): pid=$($r.data)"
} catch {
    Write-Host "Instance 3 FAILED: $($_.ErrorDetails.Message)"
}
