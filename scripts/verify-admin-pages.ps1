[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:9086',
    [string]$Username = 'admin',
    [string]$Password = $env:DOCHUB_ADMIN_PASSWORD,
    [string]$AdministratorToken = $env:DOCHUB_ADMIN_BEARER_TOKEN,
    [int]$PageSize = 12,
    [switch]$Mutate
)

# Walks the read endpoints actually loaded by the admin 文档接入 / 知识路由 / 模型配置 pages
# (mirrors the views' loadAll() calls) and reports PASS/FAIL per call. Mutating operations
# (e.g. embedding change) are intentionally left to verify-embedding-migration.ps1.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$failures = @()
$skips = @()

function Get-Endpoint([string]$Path) { return ("{0}{1}" -f $BaseUrl.TrimEnd('/'), $Path) }

function Get-Token {
    if ($AdministratorToken) { return $AdministratorToken }
    if (-not $Password) { throw 'Supply -AdministratorToken or -Password (or DOCHUB_ADMIN_BEARER_TOKEN / DOCHUB_ADMIN_PASSWORD).' }
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri (Get-Endpoint '/admin/auth/login') `
        -ContentType 'application/json' -Body $body
    if ($null -eq $login -or [string]$login.code -ne '0' -or $null -eq $login.data.token) {
        throw 'Admin login failed.'
    }
    return [string]$login.data.token
}

function Invoke-Check {
    param([string]$Name, [string]$Path, [hashtable]$Body, [scriptblock]$Guard = { $true })
    $ok = $false; $detail = ''
    try {
        $resp = Invoke-RestMethod -Method Post -Uri (Get-Endpoint $Path) -Headers $script:headers `
            -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 12 -Compress)
        if ($null -eq $resp -or [string]$resp.code -ne '0') {
            $detail = "code=$($resp.code) msg=$($resp.message)"
        } else {
            $guardOk = & $Guard $resp
            if (-not $guardOk) {
                $detail = 'unexpected empty payload'
            } else {
                $ok = $true
            }
        }
    } catch {
        $detail = "HTTP error: $($_.Exception.Message)"
    }
    if ($ok) { Write-Host ("[PASS] {0}" -f $Name) }
    else {
        $script:failures += $Name
        Write-Host ("[FAIL] {0} -> {1}" -f $Name, $detail)
    }
}

function Get-FirstDocumentId([object]$Response) {
    $data = $Response.data
    $rows = $null
    if ($null -ne $data) {
        if ($data.PSObject.Properties.Name -contains 'records') { $rows = $data.records }
        elseif ($data -is [System.Array]) { $rows = $data }
        elseif ($data.PSObject.Properties.Name -contains 'list') { $rows = $data.list }
    }
    if ($null -eq $rows -or @($rows).Count -eq 0) { return $null }
    $first = @($rows)[0]
    if ($first.PSObject.Properties.Name -contains 'documentId') { return $first.documentId }
    if ($first.PSObject.Properties.Name -contains 'id') { return $first.id }
    return $null
}

$script:headers = @{ Authorization = "Bearer $(Get-Token)" }
Write-Host ("Verifying admin pages against {0}" -f $BaseUrl)

# ---- 模型配置 page (load only) ----
Invoke-Check -Name 'admin/model-config/query' -Path '/admin/model-config/query' -Body @{}
Invoke-Check -Name 'admin/model-config/embedding/query' -Path '/admin/model-config/embedding/query' -Body @{}

# ---- 文档接入 page ----
$page = $null
try {
    $page = Invoke-RestMethod -Method Post -Uri (Get-Endpoint '/manage/document/page/query') -Headers $script:headers `
        -ContentType 'application/json' -Body (@{ pageNo = 1; pageSize = $PageSize; keyword = '' } | ConvertTo-Json -Compress)
} catch { Write-Host ("[FAIL] document page request -> {0}" -f $_.Exception.Message); $script:failures += 'document page' }
if ($null -ne $page -and [string]$page.code -eq '0') {
    Write-Host '[PASS] document/page/query'
} elseif ($null -ne $page) {
    Write-Host ("[FAIL] document/page/query -> code=$($page.code) msg=$($page.message)"); $script:failures += 'document page'
}
$documentId = if ($null -ne $page) { Get-FirstDocumentId $page } else { $null }
if ($null -eq $documentId) {
    Write-Host '[SKIP] document detail endpoints (no documents present)'
    $script:skips += 'document detail endpoints'
} else {
    Write-Host ("Document fixture: {0}" -f $documentId)
    Invoke-Check -Name 'document/detail/query' -Path '/manage/document/detail/query' -Body @{ documentId = $documentId }
    Invoke-Check -Name 'document/strategy/plan/query' -Path '/manage/document/strategy/plan/query' -Body @{ documentId = $documentId }
    Invoke-Check -Name 'document/index/progress/query' -Path '/manage/document/index/progress/query' -Body @{ documentId = $documentId }
    Invoke-Check -Name 'document/chunk/query' -Path '/manage/document/chunk/query' -Body @{ documentId = $documentId; pageNo = 1; pageSize = 20 }
    Invoke-Check -Name 'knowledge/classification/review/list' -Path '/manage/knowledge/classification/review/list' `
        -Body @{ documentId = $documentId; reviewStatus = 'PENDING'; pageNo = 1; pageSize = 20 }
    Invoke-Check -Name 'knowledge/document/profile/detail' -Path '/manage/knowledge/document/profile/detail' `
        -Body @{ documentId = $documentId } `
        -Guard { param($r) $null -ne $r.data }
}

# ---- 知识路由 page ----
Invoke-Check -Name 'knowledge/scope/list' -Path '/manage/knowledge/scope/list' -Body @{}
Invoke-Check -Name 'knowledge/topic/list' -Path '/manage/knowledge/topic/list' -Body @{}
Invoke-Check -Name 'knowledge/topic/document/list' -Path '/manage/knowledge/topic/document/list' -Body @{ topicCode = '' }
Invoke-Check -Name 'knowledge/route/trace/page/query' -Path '/manage/knowledge/route/trace/page/query' -Body @{ pageNo = 1; pageSize = 20 }

if ($Mutate) {
    Write-Host 'Mutating knowledge-scope smoke test (create + delete)...'
    $code = [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $create = @{ scopeCode = "verify-scope-$code"; scopeName = "验证范围-$code"; businessCategory = 'IT' }
    try {
        $saved = Invoke-RestMethod -Method Post -Uri (Get-Endpoint '/manage/knowledge/scope/save') -Headers $script:headers `
            -ContentType 'application/json' -Body ($create | ConvertTo-Json -Compress)
        if ($null -ne $saved -and [string]$saved.code -eq '0') {
            Write-Host '[PASS] knowledge/scope/save (mutating smoke)'
            Invoke-RestMethod -Method Post -Uri (Get-Endpoint '/manage/knowledge/scope/delete') -Headers $script:headers `
                -ContentType 'application/json' -Body (@{ scopeCode = $create.scopeCode } | ConvertTo-Json -Compress) | Out-Null
        } else {
            Write-Host ("[SKIP] knowledge/scope/save rejected -> code=$($saved.code) msg=$($saved.message)"); $script:skips += 'scope save'
        }
    } catch {
        Write-Host ("[SKIP] knowledge/scope/save -> {0}" -f $_.Exception.Message); $script:skips += 'scope save'
    }
}

Write-Host ''
Write-Host ("Summary: {0} failed, {1} skipped" -f @($script:failures).Count, @($script:skips).Count)
if (@($script:failures).Count -gt 0) { exit 1 }
exit 0
