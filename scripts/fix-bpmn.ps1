# Step 1: Fix the BPMN XML file
$file = "C:\WorkSpace\QODER\Omni-Stack\scripts\bpmn\procurement-approval.bpmn20.xml"
$content = Get-Content $file -Raw -Encoding UTF8

# Replace collection from plain variable to UEL expression
$content = $content.Replace('flowable:collection="candidateUserIds"', 'flowable:collection="${candidateResolver.resolve(execution)}"')

# Remove executionListener lines (they are no longer needed)
$content = $content -replace '(?m)^\s*<flowable:executionListener event="start" delegateExpression="\$\{scopedRoleAssignmentListener\}" />\r?\n', ''

[System.IO.File]::WriteAllText($file, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "BPMN file updated successfully"

# Verify
$verify = Get-Content $file -Raw -Encoding UTF8
$matches = [regex]::Matches($verify, 'candidateResolver\.resolve')
Write-Host "Found $($matches.Count) occurrences of candidateResolver.resolve (expected 12)"
$execListeners = [regex]::Matches($verify, 'executionListener')
Write-Host "Found $($execListeners.Count) executionListener references (expected 0)"
