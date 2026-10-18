<#
.SYNOPSIS
为独立教学环境中的已有角色建立 Workflow 组织作用域，不创建用户或授予新角色。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProjectName,
    [Parameter(Mandatory)][string[]]$Usernames,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [long]$TenantId = 1,
    [switch]$VerifyOnly
)

$ErrorActionPreference = 'Stop'
if ($ProjectName -notmatch '^omni-(e2e|wiki-acceptance)-[a-z0-9][a-z0-9-]+$') {
    throw '仅接受显式命名的 omni-e2e-* / omni-wiki-acceptance-* 隔离项目，禁止标准 omni-stack'
}
if ($TenantId -le 0) { throw 'TenantId 必须为正整数' }
if ($Usernames.Count -lt 1 -or $Usernames.Count -gt 50 -or
    @($Usernames | Select-Object -Unique).Count -ne $Usernames.Count) {
    throw 'Usernames 必须包含 1~50 个不重复的教学账号'
}
foreach ($username in $Usernames) {
    if ($username -notmatch '^wiki[a-z0-9]+_[a-z0-9_]+$') {
        throw '仅允许 wiki{run}_name 格式的明确教学账号'
    }
}
if (-not $VerifyOnly -and $env:E2E_MUTATIONS -ne 'true') {
    throw '写入需要显式设置 E2E_MUTATIONS=true'
}
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (-not [IO.Path]::IsPathRooted($OutputDirectory)) { throw '证据目录必须为仓库外绝对路径' }
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ($output.Equals($repository, [StringComparison]::OrdinalIgnoreCase) -or
    $output.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw '教学数据和执行证据禁止写入仓库'
}
$receipt = Join-Path $output 'workflow-role-scopes.json'
if (Test-Path -LiteralPath $receipt) { throw '证据文件已存在，请指定新的执行目录' }

$ids = @(& docker ps --filter "label=com.docker.compose.project=$ProjectName" --filter 'label=com.docker.compose.service=mysql' --format '{{.ID}}')
if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1) { throw '必须存在唯一、运行中的目标 MySQL 容器' }
$containers = @(& docker inspect $ids[0] | ConvertFrom-Json)
if ($LASTEXITCODE -ne 0) { throw '无法核实目标容器' }
$container = $containers[0]
if ($container.Config.Labels.'com.docker.compose.project' -ne $ProjectName) { throw '容器项目归属不一致' }
if (@($container.NetworkSettings.Ports.PSObject.Properties | Where-Object { $null -ne $_.Value }).Count -gt 0) {
    throw '目标 MySQL 不得映射宿主端口'
}
foreach ($network in $container.NetworkSettings.Networks.PSObject.Properties.Name) {
    if (-not $network.StartsWith($ProjectName + '_', [StringComparison]::Ordinal)) { throw '目标连接了非本项目网络' }
}
foreach ($mount in $container.Mounts | Where-Object { $_.Type -eq 'volume' }) {
    if (-not $mount.Name.StartsWith($ProjectName + '_', [StringComparison]::Ordinal)) { throw '目标使用了非本项目数据卷' }
}

function Invoke-TeachingSql([string]$Sql) {
    # 数据库客户端和凭据读取均在目标容器内，口令不进入命令参数或输出。
    $result = @($Sql | & docker exec -i $ids[0] sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names omni_auth')
    if ($LASTEXITCODE -ne 0) { throw '容器内 SQL 执行失败，保留证据后再核对，不盲目重试' }
    return $result
}

# 账号已经按严格字符集校验；SQL 只匹配明确列表及同租户的已有启用角色和主组织。
$names = ($Usernames | ForEach-Object { "'" + $_ + "'" }) -join ','
$joins = @"
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.tenant_id=u.tenant_id AND r.status=1
JOIN sys_org_unit ou ON ou.id=u.primary_unit_id AND ou.tenant_id=u.tenant_id AND ou.status=1
WHERE u.tenant_id=$TenantId AND u.status=1 AND u.username IN ($names)
AND r.role_code NOT IN ('USER','SUPER_ADMIN')
"@
$eligibleUsers = @(Invoke-TeachingSql "SELECT COUNT(*) FROM sys_user u JOIN sys_org_unit ou ON ou.id=u.primary_unit_id AND ou.tenant_id=u.tenant_id AND ou.status=1 WHERE u.tenant_id=$TenantId AND u.status=1 AND u.username IN ($names);")
if ($eligibleUsers.Count -ne 1 -or [int]$eligibleUsers[0] -ne $Usernames.Count) {
    throw '存在缺失、停用或没有有效主组织的教学账号，未执行写入'
}
$expectedRows = @(Invoke-TeachingSql "SELECT COUNT(*) $joins;")
$bindingQuery = @"
SELECT u.username,r.role_code,s.unit_id,s.scope_mode,s.status
FROM sys_user_role_scope s JOIN sys_user u ON u.id=s.user_id AND u.tenant_id=s.tenant_id
JOIN sys_role r ON r.id=s.role_id AND r.tenant_id=s.tenant_id
WHERE s.tenant_id=$TenantId AND u.username IN ($names) AND s.unit_id=u.primary_unit_id
AND r.role_code NOT IN ('USER','SUPER_ADMIN') ORDER BY u.username,r.role_code;
"@
$sql = "START TRANSACTION;`nINSERT IGNORE INTO sys_user_role_scope (tenant_id,user_id,role_id,unit_id,scope_mode,status)`nSELECT u.tenant_id,u.id,r.id,u.primary_unit_id,'SAME_UNIT',1 $joins;`nCOMMIT;"
New-Item -ItemType Directory -Path $output -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $output 'workflow-role-scopes.sql'), $sql, [Text.UTF8Encoding]::new($false))
$before = @(Invoke-TeachingSql $bindingQuery)
$record = [ordered]@{
    schema = 1; project = $ProjectName; tenantId = $TenantId; usernames = $Usernames
    containerId = $container.Id; verifyOnly = [bool]$VerifyOnly; startedAt = [DateTime]::UtcNow.ToString('o')
    status = 'PREPARED'; expectedRows = [int]$expectedRows[0]; before = $before
    sqlSha256 = (Get-FileHash -LiteralPath (Join-Path $output 'workflow-role-scopes.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
}
[IO.File]::WriteAllText($receipt, ($record | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
try {
    if (-not $VerifyOnly) { Invoke-TeachingSql $sql | Out-Null }
    $after = @(Invoke-TeachingSql $bindingQuery)
    $invalid = @($after | Where-Object { $_ -notmatch "`tSAME_UNIT`t1$" })
    if ($after.Count -ne [int]$expectedRows[0] -or $invalid.Count -gt 0) {
        throw '作用域数量或状态不符；不覆盖已有冲突，检查私有回执并人工修正教学配置'
    }
    $record.after = $after
    $record.status = 'VERIFIED'
} catch {
    $record.status = 'FAILED_OR_COMMIT_UNKNOWN'
    $record.error = $_.Exception.Message
    throw
} finally {
    $record.completedAt = [DateTime]::UtcNow.ToString('o')
    [IO.File]::WriteAllText($receipt, ($record | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}
Write-Output "WORKFLOW-SCOPE-FIXTURE-OK project=$ProjectName bindings=$($record.after.Count) verifyOnly=$VerifyOnly"
