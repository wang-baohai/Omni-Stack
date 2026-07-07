# 修复排他网关 default 分支上的 conditionExpression
# 读取数据库中的 BPMN XML，移除 reject 连线的条件表达式，写回数据库

$connStr = "Server=localhost;Port=3306;Database=omni_workflow;Uid=root;Pwd=root;CharSet=utf8mb4"

# 用 docker exec 获取 XML
$xml = docker exec omni-mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -B omni_workflow -e "SELECT bpmn_xml FROM wf_process_model_version WHERE id = 3" 2>$null
$xml2 = docker exec omni-mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -B omni_workflow -e "SELECT bpmn_xml FROM wf_process_model_version WHERE id = 4" 2>$null

Write-Host "Processing tenant 1 XML (length: $($xml.Length))..."
Write-Host "Processing tenant 2 XML (length: $($xml2.Length))..."

# 用正则移除 reject 连线上的 conditionExpression
# 匹配: <sequenceFlow id="flow-lX-reject" ...>...\n    </sequenceFlow>
# 替换为自闭合: <sequenceFlow id="flow-lX-reject" ... />
$pattern = '(<sequenceFlow id="flow-l\d-reject"[^>]*>)\s*<conditionExpression[^<]*</conditionExpression>\s*</sequenceFlow>'
$replacement = '$1'.Replace('$1','') # placeholder

$fixed = [regex]::Replace($xml, $pattern, { param($m) 
    $openTag = $m.Groups[1].Value -replace '>$', ' />'
    return $openTag
})

$fixed2 = [regex]::Replace($xml2, $pattern, { param($m) 
    $openTag = $m.Groups[1].Value -replace '>$', ' />'
    return $openTag
})

Write-Host "`nTenant 1 - Changes made: $($xml.Length - $fixed.Length) chars removed"
Write-Host "Tenant 2 - Changes made: $($xml2.Length - $fixed2.Length) chars removed"

# 检查是否还有 conditionExpression 在 reject 连线上
$l1check = [regex]::IsMatch($fixed, 'flow-l\d-reject.*conditionExpression')
Write-Host "Still has reject conditions: $l1check"
