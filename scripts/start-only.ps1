# Get fresh token and start 5 instances

# 1. Login
$capR = Invoke-RestMethod -Method Get -Uri "http://localhost:8102/api/auth/captcha" -Headers @{"X-Tenant-Id"="1"}
$capKey = $capR.data.captchaKey
$capCode = (docker exec omni-redis redis-cli GET "captcha:$capKey" 2>&1).Trim()
Write-Host "Captcha: $capCode"

$loginBody = @{username="admin";password="admin123";tenantId=1;captchaKey=$capKey;captchaCode=$capCode} | ConvertTo-Json
$loginR = Invoke-RestMethod -Method Post -Uri "http://localhost:8102/api/auth/login" -Body $loginBody -ContentType "application/json" -Headers @{"X-Tenant-Id"="1"}
$token = $loginR.data.accessToken
Write-Host "Token OK"

$h = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id"   = "1"
    "Content-Type"  = "application/json"
}

$base = "http://localhost:8102/api/workflow/process-instance/start"

# Instance 1: OFFICE_SUPPLY, 4200, zhangsan(100)
$b1 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u529e\u516c\u7528\u54c1-\u884c\u653f\u90e8\u6587\u5177\u91c7\u8d2d","businessKey":"SIM-OFFICE-001","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"OFFICE_SUPPLY","totalAmount":4200}}'
try {
    $r = Invoke-RestMethod -Method Post -Uri $base -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b1)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 1 (office-low): pid=$($r.data)"
} catch {
    $err = $_.ErrorDetails.Message
    Write-Host "Instance 1 FAILED: $err"
}

# Instance 2: IT_DEVICE, 35400, zhangsan(100)
$b2 = '{"modelVersionId":2,"title":"\u91c7\u8d2dIT\u8bbe\u5907-\u6280\u672f\u90e8\u7b14\u8bb0\u672c\u91c7\u8d2d","businessKey":"SIM-IT-002","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"IT_DEVICE","totalAmount":35400}}'
try {
    $r = Invoke-RestMethod -Method Post -Uri $base -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b2)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 2 (it-low): pid=$($r.data)"
} catch {
    $err = $_.ErrorDetails.Message
    Write-Host "Instance 2 FAILED: $err"
}

# Instance 3: RAW_MATERIAL, 80000, qianqi(200)
$b3 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u539f\u6750\u6599-\u751f\u4ea7\u7ebf\u94dd\u6750\u8865\u8d27","businessKey":"SIM-RAW-003","category":"purchase","simulateUserId":200,"simulateUserName":"qianqi","variables":{"materialCategory":"RAW_MATERIAL","totalAmount":80000}}'
try {
    $r = Invoke-RestMethod -Method Post -Uri $base -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b3)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 3 (raw-low): pid=$($r.data)"
} catch {
    $err = $_.ErrorDetails.Message
    Write-Host "Instance 3 FAILED: $err"
}

# Instance 4: IT_DEVICE, 65000, zhangsan(100)
$b4 = '{"modelVersionId":2,"title":"\u91c7\u8d2dIT\u8bbe\u5907-\u670d\u52a1\u5668\u96c6\u7fa4\u6269\u5bb9","businessKey":"SIM-IT-004","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"IT_DEVICE","totalAmount":65000}}'
try {
    $r = Invoke-RestMethod -Method Post -Uri $base -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b4)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 4 (it-high): pid=$($r.data)"
} catch {
    $err = $_.ErrorDetails.Message
    Write-Host "Instance 4 FAILED: $err"
}

# Instance 5: OFFICE_SUPPLY, 15000, zhangsan(100)
$b5 = '{"modelVersionId":2,"title":"\u91c7\u8d2d\u529e\u516c\u7528\u54c1-\u5e74\u5ea6\u529e\u516c\u8017\u6750\u6846\u67b6","businessKey":"SIM-OFFICE-005","category":"purchase","simulateUserId":100,"simulateUserName":"zhangsan","variables":{"materialCategory":"OFFICE_SUPPLY","totalAmount":15000}}'
try {
    $r = Invoke-RestMethod -Method Post -Uri $base -Headers $h -Body ([System.Text.Encoding]::UTF8.GetBytes($b5)) -ContentType "application/json; charset=utf-8"
    Write-Host "Instance 5 (office-high): pid=$($r.data)"
} catch {
    $err = $_.ErrorDetails.Message
    Write-Host "Instance 5 FAILED: $err"
}

Write-Host "=== Done ==="
