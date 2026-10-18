<#
.SYNOPSIS
在独立教学项目中准备 Outbox 路由失败、修复和消费幂等数据；不停止 Broker。
.DESCRIPTION
宿主仅编排 Docker。SQL 客户端、口令读取和数据操作都在目标 MySQL 容器中执行。
Prepare 暂缓首次投递以便核对 PENDING；Release 后由真实中继按默认20/40秒退避运行。
Repair 只修复本次消息的 broker_type，重发/忽略仍必须使用正式管理 API。
Replay 复制已发送消息的原始 payload/eventId，验证真实消费者幂等。
Cleanup 仅删除本夹具的终态消息和合成日志，不触及业务历史或正式种子。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProjectName,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9][a-z0-9-]{2,23}$')][string]$RunId,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][ValidateSet('Prepare','Release','Inspect','Repair','Replay','Cleanup')][string]$Action,
    [long]$TenantId = 1
)

$ErrorActionPreference = 'Stop'
if ($ProjectName -notmatch '^omni-(e2e|wiki-acceptance)-[a-z0-9][a-z0-9-]+$') {
    throw '仅接受明确的独立教学项目；禁止标准 omni-stack'
}
if ($TenantId -le 0) { throw 'TenantId 必须为正整数' }
if ($Action -ne 'Inspect' -and $env:E2E_MUTATIONS -ne 'true') { throw '写入需要 E2E_MUTATIONS=true' }
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (-not [IO.Path]::IsPathRooted($OutputDirectory)) { throw '必须指定仓库外绝对输出路径' }
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ($output.Equals($repository,[StringComparison]::OrdinalIgnoreCase) -or
    $output.StartsWith($repository+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
    throw '夹具回执和 SQL 禁止写入仓库'
}
$endpoint = & docker context inspect --format '{{.Endpoints.docker.Host}}'
if ($LASTEXITCODE -ne 0 -or $endpoint -notmatch '^npipe:////\./pipe/(dockerDesktopLinuxEngine|docker_engine)$') {
    throw '仅支持本机 Windows Docker Linux 引擎'
}
$ids = @(& docker ps --filter "label=com.docker.compose.project=$ProjectName" --filter 'label=com.docker.compose.service=mysql' --format '{{.ID}}')
if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1) { throw '需要唯一运行中的教学 MySQL' }
$container = @(& docker inspect $ids[0] | ConvertFrom-Json)[0]
if ($LASTEXITCODE -ne 0 -or $container.Config.Labels.'com.docker.compose.project' -ne $ProjectName -or
    $container.State.Health.Status -ne 'healthy') { throw 'MySQL 项目或健康状态不符' }
if (@($container.NetworkSettings.Ports.PSObject.Properties | Where-Object {$null -ne $_.Value}).Count -gt 0) {
    throw '教学 MySQL 不得映射宿主端口'
}
foreach ($network in $container.NetworkSettings.Networks.PSObject.Properties.Name) {
    if (-not $network.StartsWith($ProjectName+'_',[StringComparison]::Ordinal)) { throw '连接了其他项目网络' }
}
foreach ($mount in $container.Mounts | Where-Object {$_.Type -eq 'volume'}) {
    if (-not $mount.Name.StartsWith($ProjectName+'_',[StringComparison]::Ordinal)) { throw '使用了其他项目数据卷' }
}
function Invoke-TeachingSql([string]$Sql) {
    $result = @($Sql | & docker exec -i $ids[0] sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names --default-character-set=utf8mb4 omni_base')
    if ($LASTEXITCODE -ne 0) { throw 'SQL失败或提交状态未知；先Inspect，不盲目重放' }
    return $result
}
function Save-Receipt {
    [IO.File]::WriteAllText($receiptPath,($record | ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
}
function Require-Count([string]$Where,[int]$Expected) {
    $count = @(Invoke-TeachingSql "SELECT COUNT(*) FROM sys_mq_message WHERE $Where;")
    if ($count.Count -ne 1 -or [int]$count[0] -ne $Expected) { throw '当前行数/状态不符；先Inspect，不覆盖其他状态' }
}
$receiptPath = Join-Path $output 'mq-teaching.json'
$locales = @('zh-CN','en-US','ja-JP','ko-KR')
$prefix = "WIKI-MQ-$RunId"
if ($Action -eq 'Prepare') {
    if (Test-Path -LiteralPath $receiptPath) { throw '回执已存在；先Inspect，禁止重复Prepare' }
    $resources = @()
    foreach ($locale in $locales) {
        foreach ($kind in @('recover','skip')) {
            $messageId = [guid]::NewGuid().ToString()
            $resources += [pscustomobject]@{locale=$locale;kind=$kind;msgId=$messageId;eventId=$messageId;msgKey="$prefix-$locale-$kind"}
        }
    }
    $record = [ordered]@{schema=1;project=$ProjectName;runId=$RunId;tenantId=$TenantId;prefix=$prefix;
        containerId=$container.Id;resources=$resources;actions=@();status='PREPARED';
        startedAt=[DateTime]::UtcNow.ToString('o');synthetic=$true;producerTraceId=$null}
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    Save-Receipt
} else {
    if (-not (Test-Path -LiteralPath $receiptPath)) { throw '缺少原始回执，不推测资源归属' }
    $record = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json -AsHashtable
    if ($record.project -ne $ProjectName -or $record.runId -ne $RunId -or $record.tenantId -ne $TenantId -or
        $record.containerId -ne $container.Id -or $record.prefix -ne $prefix -or $record.synthetic -ne $true) {
        throw '回执与当前项目、容器、租户或运行批次不符'
    }
}
foreach ($resource in $record.resources) {
    if ($resource.locale -notin $locales -or $resource.kind -notin @('recover','skip','replay') -or
        $resource.msgId -notmatch '^[a-f0-9-]{36}$' -or $resource.eventId -notmatch '^[a-f0-9-]{36}$' -or
        $resource.msgKey -ne "$prefix-$($resource.locale)-$($resource.kind)") { throw '回执资源格式或归属不符' }
}
if (@($record.resources | ForEach-Object {$_.msgId} | Select-Object -Unique).Count -ne $record.resources.Count) { throw '回执消息ID重复' }
$ownedPairs = ($record.resources | ForEach-Object {"(msg_id='$($_.msgId)' AND msg_key='$($_.msgKey)')"}) -join ' OR '
$ownedWhere = "tenant_id=$TenantId AND service_name='omni-base' AND ($ownedPairs)"
$sql = ''
switch ($Action) {
    'Prepare' {
        $keys = ($record.resources | ForEach-Object {"'$($_.msgKey)'"}) -join ','
        Require-Count "tenant_id=$TenantId AND msg_key IN ($keys)" 0
        $inserts = foreach ($resource in $record.resources) {
            $payload = "JSON_OBJECT('eventId','$($resource.eventId)','operUsername','wiki_mq_fixture','tenantId',$TenantId,'operTime',DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s'),'module','$prefix','operType','UPDATE','requestMethod','POST','requestUrl','/fixture/mq/$($resource.msgId)','requestParams','{}','responseStatus',200,'ipAddress','127.0.0.1','userAgent','OmniStackWikiFixture','executionTime',0)"
            "INSERT INTO sys_mq_message(msg_id,topic,binding_name,msg_key,payload,broker_type,status,retry_count,max_retry,next_retry_time,service_name,tenant_id) VALUES('$($resource.msgId)','operlog-topic','operlog-out-0','$($resource.msgKey)',$payload,'wiki-unregistered',0,0,3,DATE_ADD(NOW(),INTERVAL 30 MINUTE),'omni-base',$TenantId);"
        }
        $sql = "START TRANSACTION;`n$($inserts -join "`n")`nCOMMIT;"
    }
    'Release' {
        Require-Count "$ownedWhere AND status=0 AND retry_count=0 AND broker_type='wiki-unregistered' AND next_retry_time>NOW()" 8
        $sql = "UPDATE sys_mq_message SET next_retry_time=NULL WHERE $ownedWhere AND status=0 AND retry_count=0 AND broker_type='wiki-unregistered';"
    }
    'Repair' {
        $recoverIds = ($record.resources | Where-Object {$_.kind -eq 'recover'} | ForEach-Object {"'$($_.msgId)'"}) -join ','
        Require-Count "$ownedWhere AND msg_id IN ($recoverIds) AND status=3 AND retry_count=3 AND broker_type='wiki-unregistered'" 4
        $sql = "UPDATE sys_mq_message SET broker_type='rocketmq' WHERE $ownedWhere AND msg_id IN ($recoverIds) AND status=3 AND retry_count=3 AND broker_type='wiki-unregistered';"
    }
    'Replay' {
        if (@($record.resources | Where-Object {$_.kind -eq 'replay'}).Count -ne 0) { throw 'Replay已登记；先Inspect，不重复入队' }
        $recovered = @($record.resources | Where-Object {$_.kind -eq 'recover'})
        $recoveredIds = ($recovered | ForEach-Object {"'$($_.msgId)'"}) -join ','
        Require-Count "$ownedWhere AND msg_id IN ($recoveredIds) AND status=1 AND broker_type='rocketmq'" 4
        $inserts = foreach ($original in $recovered) {
            $replay = [ordered]@{locale=$original.locale;kind='replay';msgId=[guid]::NewGuid().ToString();eventId=$original.eventId;msgKey="$prefix-$($original.locale)-replay"}
            $record.resources += $replay
            "INSERT INTO sys_mq_message(msg_id,topic,binding_name,msg_key,payload,broker_type,status,retry_count,max_retry,next_retry_time,service_name,producer_trace_id,tenant_id) SELECT '$($replay.msgId)',topic,binding_name,'$($replay.msgKey)',payload,'rocketmq',0,0,3,NULL,service_name,NULL,tenant_id FROM sys_mq_message WHERE tenant_id=$TenantId AND msg_id='$($original.msgId)' AND msg_key='$($original.msgKey)' AND status=1;"
        }
        Save-Receipt
        $sql = "START TRANSACTION;`n$($inserts -join "`n")`nCOMMIT;"
    }
    'Cleanup' {
        if ($record.status -eq 'CLEANED') { throw '已完成Cleanup，不重复执行' }
        Require-Count "$ownedWhere AND status IN (1,4)" $record.resources.Count
        $eventIds = ($record.resources | ForEach-Object {"'$($_.eventId)'"} | Select-Object -Unique) -join ','
        $sql = "START TRANSACTION; DELETE FROM sys_oper_log WHERE tenant_id=$TenantId AND module='$prefix' AND oper_username='wiki_mq_fixture' AND event_id IN ($eventIds); DELETE FROM sys_mq_message WHERE $ownedWhere AND status IN (1,4); COMMIT;"
    }
}
if ($sql) {
    $operation = [ordered]@{action=$Action;startedAt=[DateTime]::UtcNow.ToString('o');status='PREPARED'}
    $record.actions += $operation
    $sqlPath = Join-Path $output ("{0:D2}-{1}.sql" -f $record.actions.Count,$Action.ToLowerInvariant())
    [IO.File]::WriteAllText($sqlPath,$sql,[Text.UTF8Encoding]::new($false))
    $operation.sqlSha256 = (Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Save-Receipt
    try {
        Invoke-TeachingSql $sql | Out-Null
        $operation.status = 'COMMITTED'
        $record.status = $Action.ToUpperInvariant()
    } catch {
        $operation.status = 'FAILED_OR_COMMIT_UNKNOWN'
        throw
    } finally {
        $operation.completedAt = [DateTime]::UtcNow.ToString('o')
        Save-Receipt
    }
}
# 状态快照来自真实数据库；不写回状态、不伪造 producer trace、不等待预估时间就宣称恢复。
$ownedPairs = ($record.resources | ForEach-Object {"(msg_id='$($_.msgId)' AND msg_key='$($_.msgKey)')"}) -join ' OR '
$ownedWhere = "tenant_id=$TenantId AND service_name='omni-base' AND ($ownedPairs)"
$snapshot = @(Invoke-TeachingSql "SELECT JSON_OBJECT('msgId',msg_id,'msgKey',msg_key,'status',status,'retryCount',retry_count,'maxRetry',max_retry,'nextRetryTime',DATE_FORMAT(next_retry_time,'%Y-%m-%d %H:%i:%s'),'updatedAt',DATE_FORMAT(update_time,'%Y-%m-%d %H:%i:%s'),'errorMsg',error_msg,'brokerType',broker_type,'payloadSha256',SHA2(payload,256)) FROM sys_mq_message WHERE $ownedWhere ORDER BY msg_key;" | ForEach-Object {$_ | ConvertFrom-Json})
$eventIds = ($record.resources | ForEach-Object {"'$($_.eventId)'"} | Select-Object -Unique) -join ','
$consumed = @(Invoke-TeachingSql "SELECT JSON_OBJECT('eventId',event_id,'count',COUNT(*)) FROM sys_oper_log WHERE tenant_id=$TenantId AND module='$prefix' AND oper_username='wiki_mq_fixture' AND event_id IN ($eventIds) GROUP BY event_id ORDER BY event_id;" | ForEach-Object {$_ | ConvertFrom-Json})
if ($Action -eq 'Cleanup') {
    if ($snapshot.Count -ne 0 -or $consumed.Count -ne 0) { throw '清理后仍有本批残留' }
    $record.status = 'CLEANED'
}
$record.lastSnapshot = [ordered]@{at=[DateTime]::UtcNow.ToString('o');messages=$snapshot;consumerEvents=$consumed}
Save-Receipt
Write-Output "MQ-TEACHING-FIXTURE-OK action=$Action project=$ProjectName resources=$($record.resources.Count) currentRows=$($snapshot.Count) consumerEvents=$($consumed.Count) status=$($record.status)"
