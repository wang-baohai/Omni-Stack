# 受控身份入口：宿主仅调用 Docker，JDK 与 Maven 在容器运行。
# 全部连接变量及显式测试 network 由调用方环境提供，不输出秘密。
# 从调用方受控环境继承；缺失即列出变量名并以非零退出，不打印任何值、不提供默认值。
# E2eTokenFixture 的仓库外新文件保护保留：输出文件若已存在由其校验并失败。
# 不 dump token/响应内容；mvnw 退出码原样透传，不截尾不掩盖。
$ErrorActionPreference = 'Stop'

$required = @(
    'E2E_FIXTURE_OUTPUT', 'E2E_FIXTURE_TENANT_ID', 'E2E_FIXTURE_DB_URL',
    'E2E_FIXTURE_DB_USERNAME', 'E2E_FIXTURE_DB_PASSWORD',
    'E2E_FIXTURE_REDIS_HOST', 'E2E_FIXTURE_REDIS_PORT', 'E2E_FIXTURE_REDIS_PASSWORD',
    'E2E_FIXTURE_JWK_ENCRYPT_KEY', 'E2E_FIXTURE_DOCKER_NETWORK'
)
$missing = @($required | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
})
if ($missing.Count -gt 0) {
    Write-Output "MISSING-INPUTS: $($missing -join ',')"
    Write-Output '本脚本不含凭证字面量；按授权映射的受控引用方式注入环境后重试。'
    exit 2
}

$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$output = [IO.Path]::GetFullPath($env:E2E_FIXTURE_OUTPUT)
if (-not [IO.Path]::IsPathRooted($env:E2E_FIXTURE_OUTPUT)) {
    throw 'E2E_FIXTURE_OUTPUT 必须为仓库外绝对路径'
}
if ($output.Equals($repository, [StringComparison]::OrdinalIgnoreCase) -or
    $output.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'E2E Token 文件禁止写入仓库'
}
if (Test-Path -LiteralPath $output) { throw 'E2E Token 输出文件已存在' }
$parent = [IO.Path]::GetDirectoryName($output)
if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'E2E Token 输出目录不存在' }
if ($repository -match '[,\r\n]' -or $parent -match '[,\r\n]') { throw '挂载路径包含 Docker mount 不支持的字符' }
if ($env:E2E_FIXTURE_DOCKER_NETWORK -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]+$') { throw '测试 Docker network 名非法' }

$arguments = @(
    'run', '--rm', '--network', $env:E2E_FIXTURE_DOCKER_NETWORK,
    '--mount', "type=bind,source=$repository,target=/workspace",
    '--mount', "type=bind,source=$parent,target=/fixture-output",
    '--mount', 'type=volume,source=omni-stack-tools_maven-cache,target=/root/.m2',
    '--workdir', '/workspace/omni-backend',
    '--env', 'JAVA_HOME=/opt/java/openjdk',
    '--env', ('E2E_FIXTURE_OUTPUT=/fixture-output/' + [IO.Path]::GetFileName($output))
)
foreach ($variable in Get-ChildItem Env: | Where-Object { $_.Name -like 'E2E_FIXTURE_*' -and $_.Name -ne 'E2E_FIXTURE_OUTPUT' }) {
    $arguments += @('--env', $variable.Name)
}
$arguments += @(
    'eclipse-temurin:25-jdk', 'sh', '-c',
    'sh ./mvnw -pl omni-auth -am install -DskipTests -B -q && sh ./mvnw -pl omni-auth exec:java -Dexec.mainClass=com.omni.auth.e2e.E2eTokenFixture -Dexec.classpathScope=test -B -q'
)
& docker @arguments
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    Write-Output "TOKEN-FIXTURE-FAILED exit=$exitCode"
    exit $exitCode
}
Write-Output "TOKEN-FIXTURE-OK output=$output"
exit 0
