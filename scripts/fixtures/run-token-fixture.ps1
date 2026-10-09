# scripts/fixtures/run-token-fixture.ps1 — E2E token 受控签发入口（WP-005 返修 W5-R05）。
# 凭证零字面量：全部 9 个 E2E_FIXTURE_* 输入（与 omni-auth E2eTokenFixture 当前源码一致）
# 从调用方受控环境继承；缺失即列出变量名并以非零退出，不打印任何值、不提供默认值。
# E2eTokenFixture 的仓库外新文件保护保留：输出文件若已存在由其校验并失败。
# 不 dump token/响应内容；mvnw 退出码原样透传，不截尾不掩盖。
$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:/APP/JDK25/jdk-25.0.2'
}
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"

$required = @(
    'E2E_FIXTURE_OUTPUT', 'E2E_FIXTURE_TENANT_ID', 'E2E_FIXTURE_DB_URL',
    'E2E_FIXTURE_DB_USERNAME', 'E2E_FIXTURE_DB_PASSWORD',
    'E2E_FIXTURE_REDIS_HOST', 'E2E_FIXTURE_REDIS_PORT', 'E2E_FIXTURE_REDIS_PASSWORD',
    'E2E_FIXTURE_JWK_ENCRYPT_KEY'
)
$missing = @($required | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
})
if ($missing.Count -gt 0) {
    Write-Output "MISSING-INPUTS: $($missing -join ',')"
    Write-Output '本脚本不含凭证字面量；按授权映射的受控引用方式注入环境后重试。'
    exit 2
}

$backendDir = Join-Path $PSScriptRoot '..\..\omni-backend'
Set-Location $backendDir
& .\mvnw.cmd -pl omni-auth exec:java `
    "-Dexec.mainClass=com.omni.auth.e2e.E2eTokenFixture" `
    "-Dexec.classpathScope=test"
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    Write-Output "TOKEN-FIXTURE-FAILED exit=$exitCode"
    exit $exitCode
}
Write-Output "TOKEN-FIXTURE-OK output=$env:E2E_FIXTURE_OUTPUT"
exit 0
