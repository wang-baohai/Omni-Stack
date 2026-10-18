#requires -Version 7.4
<#
.SYNOPSIS
将已核实的独立教学 MySQL 逻辑备份恢复到全新、无宿主端口的 Docker 环境。
.DESCRIPTION
源库只读。SQL/口令/迁移日志保存在仓库外，不输出其内容。
正式迁移器在恢复副本上重放；比较八个稳定数据库的数据摘要与九库迁移历史。
XXL-JOB 运行数据仍在备份内，但不以两个时点的运行日志摘要相等作为断言。
成功后仅清理本次新建容器、网络和卷；失败保留目标与回执供核对，禁止盲目重跑。
这不是应用、Redis、Broker 或生产 RPO/RTO 的完整灾备验收。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SourceProject,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9][a-z0-9-]{2,23}$')][string]$RunId,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$MigratorImage
)
$ErrorActionPreference='Stop'
if($SourceProject -notmatch '^omni-(e2e|wiki-acceptance)-[a-z0-9][a-z0-9-]+$'){throw '仅支持独立教学项目'}
$repository=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if(-not [IO.Path]::IsPathRooted($OutputDirectory)){throw '输出必须为仓库外绝对路径'}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if($output.Equals($repository,[StringComparison]::OrdinalIgnoreCase) -or $output.StartsWith($repository+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw '数据库导出禁止写入仓库'}
if(Test-Path -LiteralPath $output){throw '输出目录已存在；先核对既有回执，禁止重复恢复'}
$endpoint=& docker context inspect --format '{{.Endpoints.docker.Host}}'
if($LASTEXITCODE -ne 0 -or $endpoint -notmatch '^npipe:////\./pipe/(dockerDesktopLinuxEngine|docker_engine)$'){throw '要求本机 Windows Docker Linux 引擎'}
function Read-OwnedContainer([string]$Service){
    $ids=@(& docker ps -aq --filter "label=com.docker.compose.project=$SourceProject" --filter "label=com.docker.compose.service=$Service")
    if($LASTEXITCODE -ne 0 -or $ids.Count -ne 1){throw "需要唯一源服务 $Service"}
    $item=@(& docker inspect $ids[0] | ConvertFrom-Json)[0]
    if($LASTEXITCODE -ne 0 -or $item.Config.Labels.'com.docker.compose.project' -ne $SourceProject){throw '源容器归属错误'}
    if(@($item.NetworkSettings.Ports.PSObject.Properties | Where-Object {$null -ne $_.Value}).Count){throw '源容器不得映射宿主端口'}
    foreach($net in $item.NetworkSettings.Networks.PSObject.Properties.Name){if(-not $net.StartsWith($SourceProject+'_',[StringComparison]::Ordinal)){throw '源容器连接其他项目网络'}}
    return $item
}
$mysql=Read-OwnedContainer 'mysql'
if($mysql.State.Status -ne 'running' -or $mysql.State.Health.Status -ne 'healthy'){throw '源 MySQL 未就绪'}
foreach($mount in $mysql.Mounts | Where-Object {$_.Type -eq 'volume'}){if(-not $mount.Name.StartsWith($SourceProject+'_',[StringComparison]::Ordinal)){throw '源 MySQL 使用其他项目卷'}}
$migrator=Read-OwnedContainer 'omni-db-migrator'
if($migrator.State.Status -ne 'exited' -or $migrator.State.ExitCode -ne 0){throw '源正式迁移器必须成功退出'}
$migrationImage=$migrator.Image
if($MigratorImage){
    $image=@(& docker image inspect $MigratorImage | ConvertFrom-Json)[0]
    if($LASTEXITCODE -ne 0 -or -not $image.Id){throw '指定的本机迁移镜像不可用'}
    $migrationImage=$image.Id
}
$project="omni-e2e-restore-$RunId"
$target="$project-mysql"
$runner="$project-migrator"
$network="${project}_default"
$volume="${project}_mysql-data"
foreach($resource in @(@('container',$target),@('container',$runner),@('network',$network),@('volume',$volume))){
    & docker $resource[0] inspect $resource[1] *> $null
    if($LASTEXITCODE -eq 0){throw '恢复目标已存在，禁止覆盖'}
}
New-Item -ItemType Directory -Path $output | Out-Null
$receiptPath=Join-Path $output 'backup-rehearsal.json'
$record=[ordered]@{schema=1;source_project=$SourceProject;restore_project=$project;started_at=[DateTime]::UtcNow.ToString('o');status='PREPARING';endpoint=$endpoint;source_mysql_id=$mysql.Id;source_mysql_image=$mysql.Image;source_migrator_image=$migrator.Image;migrator_image=$migrationImage;host_ports=@();scope='Nine-database logical restore, eight stable database data digests and formal migration replay; no application/Redis/Broker recovery claim';steps=@()}
function Save-Receipt {[IO.File]::WriteAllText($receiptPath,($record | ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))}
function Add-Step([string]$Name,[object]$Details){$record.steps+=@([ordered]@{name=$Name;at=[DateTime]::UtcNow.ToString('o');details=$Details});Save-Receipt;Write-Output "BACKUP_REHEARSAL $Name"}
function Require-Native([string]$Name){if($LASTEXITCODE -ne 0){throw "$Name 失败；检查私有日志和实际副作用，不自动重放"}}
function Run-Sql([string]$Container,[string]$Sql){
    $answer=@($Sql | & docker exec -i $Container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names --default-character-set=utf8mb4')
    Require-Native 'SQL';return $answer
}
$databases=@('nacos_config','omni_asset','omni_auth','omni_base','omni_crm','omni_procurement','omni_srm','omni_workflow','xxl_job')
$stable=$databases | Where-Object {$_ -ne 'xxl_job'}
$databaseFilter=($databases | ForEach-Object {"'$_'"}) -join ','
$inventorySql="SELECT TABLE_SCHEMA,TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA IN ($databaseFilter) AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_SCHEMA,TABLE_NAME;"
$historySql=($databases | ForEach-Object {"SELECT '$_',ID,AUTHOR,FILENAME,COALESCE(MD5SUM,''),EXECTYPE FROM ``$_``.DATABASECHANGELOG ORDER BY ORDEREXECUTED;"}) -join "`n"
function Dump-Data([string]$Container,[string]$Destination){
    $command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --set-gtid-purged=OFF --no-tablespaces --no-create-info --skip-comments --skip-triggers --skip-add-locks --skip-disable-keys --skip-extended-insert --complete-insert --order-by-primary --databases '+($stable -join ' ')
    & docker exec $Container sh -c $command > $Destination
    Require-Native '稳定数据库快照'
    return (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash.ToLowerInvariant()
}
Save-Receipt
try {
    $sourceDatabases=@(Run-Sql $mysql.Id "SHOW DATABASES;")
    if(@($databases | Where-Object {$_ -notin $sourceDatabases}).Count){throw '源库不具备完整九库前提'}
    $tables=Run-Sql $mysql.Id $inventorySql
    $history=Run-Sql $mysql.Id $historySql
    $sourceDigest=Dump-Data $mysql.Id (Join-Path $output 'stable-source.sql')
    $dump=Join-Path $output 'backup.sql'
    $dumpCommand='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --set-gtid-purged=OFF --no-tablespaces --routines --events --triggers --hex-blob --databases '+($databases -join ' ')
    & docker exec $mysql.Id sh -c $dumpCommand > $dump
    Require-Native '完整逻辑备份'
    $record.backup_sha256=(Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash.ToLowerInvariant()
    $record.backup_bytes=(Get-Item -LiteralPath $dump).Length
    $record.database_count=$databases.Count
    $record.table_count=$tables.Count
    $record.stable_source_sha256=$sourceDigest
    Add-Step 'BACKUP_CREATED' ([ordered]@{databases=9;tables=$tables.Count;bytes=$record.backup_bytes;sha256=$record.backup_sha256})
    $password=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
    $mysqlEnv=Join-Path $output 'mysql.env'
    [IO.File]::WriteAllText($mysqlEnv,"MYSQL_ROOT_PASSWORD=$password`nTZ=Asia/Shanghai`n",[Text.UTF8Encoding]::new($false))
    $migrationEnv=@($migrator.Config.Env | Where-Object {$_ -match '^(DB_MIGRATOR_|NACOS_USERNAME=|NACOS_PASSWORD=|XXL_JOB_ADMIN_USERNAME=|XXL_JOB_ADMIN_PASSWORD=|OMNI_ENVIRONMENT=|TZ=)'})
    $migrationEnv=@($migrationEnv | Where-Object {$_ -notmatch '^DB_MIGRATOR_ADMIN_(URL|PASSWORD)='})
    $migrationEnv+=@('DB_MIGRATOR_ADMIN_URL=jdbc:mysql://mysql:3306/?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',"DB_MIGRATOR_ADMIN_PASSWORD=$password",'MIGRATION_PUSHGATEWAY_ENABLED=false')
    $migrationEnvPath=Join-Path $output 'migrator.env'
    [IO.File]::WriteAllText($migrationEnvPath,(($migrationEnv -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    & docker network create --internal --label "com.docker.compose.project=$project" $network *> (Join-Path $output 'network-create.log')
    Require-Native '新建隔离网络'
    & docker volume create --label "com.docker.compose.project=$project" $volume *> (Join-Path $output 'volume-create.log')
    Require-Native '新建独立卷'
    & docker run --detach --name $target --network $network --network-alias mysql --label "com.docker.compose.project=$project" --label 'com.docker.compose.service=mysql' --env-file $mysqlEnv --mount "type=volume,source=$volume,target=/var/lib/mysql" $mysql.Image --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci *> (Join-Path $output 'mysql-create.log')
    Require-Native '新建恢复 MySQL'
    $record.status='TARGET_CREATED';Save-Receipt
    $deadline=[DateTime]::UtcNow.AddSeconds(180)
    do {
        & docker exec $target sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin -uroot ping --silent' *> $null
        if($LASTEXITCODE -eq 0){break}
        if([DateTime]::UtcNow -gt $deadline){throw '恢复 MySQL 启动超时；保留目标供检查'}
        Start-Sleep -Seconds 2
    } while($true)
    $targetInfo=@(& docker inspect $target | ConvertFrom-Json)[0]
    Require-Native '恢复目标身份'
    if($targetInfo.Config.Labels.'com.docker.compose.project' -ne $project -or @($targetInfo.NetworkSettings.Ports.PSObject.Properties | Where-Object {$null -ne $_.Value}).Count){throw '恢复目标归属/端口不符'}
    $record.restore_mysql_id=$targetInfo.Id
    & docker cp $dump "${target}:/tmp/omni-restore.sql" *> (Join-Path $output 'restore-copy.log')
    Require-Native '复制私有备份'
    & docker exec $target sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 < /tmp/omni-restore.sql' *> (Join-Path $output 'restore-import.log')
    Require-Native '恢复导入'
    Add-Step 'RESTORE_IMPORTED' ([ordered]@{same_source_image=($targetInfo.Image -eq $mysql.Image);host_ports=0})
    $restoredTables=Run-Sql $target $inventorySql
    $restoredHistory=Run-Sql $target $historySql
    if(($restoredTables -join "`n") -ne ($tables -join "`n")){throw '九库表清单不一致'}
    if(($restoredHistory -join "`n") -ne ($history -join "`n")){throw '九库迁移历史不一致'}
    $restoreDigest=Dump-Data $target (Join-Path $output 'stable-restored.sql')
    $record.stable_restored_sha256=$restoreDigest
    if($restoreDigest -ne $sourceDigest){throw '八库稳定数据不一致；检查并发写入和原始文件，不放宽断言'}
    Add-Step 'RESTORED_DATA_AND_HISTORY_MATCH' ([ordered]@{stable_databases=8;table_inventory=$tables.Count;changelog_rows=$history.Count;sha256=$restoreDigest;xxl_runtime_digest_excluded=$true})
    & docker run --name $runner --network $network --label "com.docker.compose.project=$project" --label 'com.docker.compose.service=omni-db-migrator' --env-file $migrationEnvPath $migrationImage *> (Join-Path $output 'migrator-replay.log')
    Require-Native '正式迁移重放'
    $afterHistory=Run-Sql $target $historySql
    if(($afterHistory -join "`n") -ne ($history -join "`n")){throw '迁移重放改变已有历史'}
    $afterDigest=Dump-Data $target (Join-Path $output 'stable-after-migrate.sql')
    $record.stable_after_migrate_sha256=$afterDigest
    if($afterDigest -ne $sourceDigest){throw '迁移重放改变稳定数据；保留实际差异'}
    Add-Step 'FORMAL_MIGRATOR_REPLAY_PASS' ([ordered]@{exit_code=0;history_unchanged=$true;stable_data_unchanged=$true})
    $record.status='PASS';$record.completed_at=[DateTime]::UtcNow.ToString('o');Save-Receipt
    foreach($name in @($runner,$target)){
        $owned=@(& docker inspect $name | ConvertFrom-Json)[0];Require-Native '清理前身份确认'
        if($owned.Config.Labels.'com.docker.compose.project' -ne $project){throw '禁止清理非本次容器'}
        & docker rm -f $name *> (Join-Path $output "$name-remove.log");Require-Native '清理新建容器'
    }
    & docker volume rm $volume *> (Join-Path $output 'volume-remove.log');Require-Native '清理本次恢复卷'
    & docker network rm $network *> (Join-Path $output 'network-remove.log');Require-Native '清理本次恢复网络'
    $record.cleanup='Only new restore containers, volume and network removed; private backup retained; source untouched';Save-Receipt
    Write-Output "BACKUP_REHEARSAL_PASS databases=9 stable=8 tables=$($tables.Count) receipt=$receiptPath"
} catch {
    $record.status='FAILED_OR_NEEDS_RECONCILIATION';$record.failure_category=$_.Exception.Message;$record.failed_at=[DateTime]::UtcNow.ToString('o');Save-Receipt
    throw
}
