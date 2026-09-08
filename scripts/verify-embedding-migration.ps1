[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:9086',
    [string]$AdministratorToken = $env:DOCHUB_ADMIN_BEARER_TOKEN,
    [string]$AdministratorPassword = $env:DOCHUB_ADMIN_PASSWORD,
    [string]$CandidateBaseUrl = $env:DOCHUB_EMBEDDING_BASE_URL,
    [string]$CandidateRequestPath = $env:DOCHUB_EMBEDDING_REQUEST_PATH,
    [string]$CandidateModel = $env:DOCHUB_EMBEDDING_MODEL,
    [string]$CandidateApiKey = $env:DOCHUB_EMBEDDING_API_KEY,
    [ValidateSet('REMOTE', 'LOCAL')][string]$DeploymentType = 'REMOTE',
    [string]$CompatibilityPreset = 'OPENAI_COMPATIBLE',
    [switch]$StartMigration,
    [int]$PollSeconds = 5,
    [int]$TimeoutSeconds = 1800
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ConfirmationPhrase = '我确认更改向量模型'

function Require-Value([string]$Name, [string]$Value) { if ([string]::IsNullOrWhiteSpace($Value)) { throw "$Name must be supplied by the caller or environment." } }
function Assert-ApiSuccess([object]$Response, [string]$Operation) { if ($null -eq $Response -or [string]$Response.code -ne '0') { throw "$Operation did not return a successful API response." } }
function Invoke-AdminPost([string]$Operation, [string]$Path, [hashtable]$Headers, [object]$Body) {
    try { return Invoke-RestMethod -Method Post -Uri ("{0}{1}" -f $endpoint, $Path) -Headers $Headers -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Compress) }
    catch { throw "$Operation request failed." }
}

Require-Value 'DOCHUB_ADMIN_BEARER_TOKEN' $AdministratorToken
Require-Value 'DOCHUB_EMBEDDING_BASE_URL' $CandidateBaseUrl
Require-Value 'DOCHUB_EMBEDDING_MODEL' $CandidateModel
if ($StartMigration) { Require-Value 'DOCHUB_ADMIN_PASSWORD' $AdministratorPassword }

$endpoint = $BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $AdministratorToken" }
$active = Invoke-AdminPost 'Embedding configuration query' '/admin/model-config/embedding/query' $headers @{}
Assert-ApiSuccess $active 'Embedding configuration query'
if (($active | ConvertTo-Json -Depth 10 -Compress) -match '"(?:apiKey|encryptedApiKey)"') { throw 'Embedding query exposed a credential field.' }

$candidate = @{ deploymentType = $DeploymentType; compatibilityPreset = $CompatibilityPreset; baseUrl = $CandidateBaseUrl; requestPath = $(if ($CandidateRequestPath) { $CandidateRequestPath } else { '/v1/embeddings' }); modelName = $CandidateModel; apiKey = $CandidateApiKey; timeoutMillis = 30000 }
$test = Invoke-AdminPost 'Embedding connection test' '/admin/model-config/embedding/test' $headers $candidate
Assert-ApiSuccess $test 'Embedding connection test'
if ($test.data.success -ne $true) { throw 'Embedding connection or dimension test was rejected.' }
Write-Host 'Embedding candidate connection and dimension test passed.'

if (-not $StartMigration) { Write-Host 'Dry run complete. Re-run with -StartMigration only after confirming the candidate model name intentionally requires a rebuild.'; exit 0 }

$candidate.currentPassword = $AdministratorPassword
$candidate.confirmationPhrase = $ConfirmationPhrase
$change = Invoke-AdminPost 'Embedding change' '/admin/model-config/embedding/change' $headers $candidate
Assert-ApiSuccess $change 'Embedding change'
if ($change.data.status -eq 'ACTIVATED') { Write-Host 'Same-model embedding credentials were atomically hot-swapped.'; exit 0 }
if ($change.data.status -ne 'MIGRATION_STARTED') { throw 'Embedding change did not start a migration.' }
$migrationId = $change.data.migrationId
Write-Host 'Blue-green migration started; old vectors remain active until completion.'

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds $PollSeconds
    $status = Invoke-AdminPost 'Embedding migration status' '/admin/model-config/embedding/migration/status' $headers @{ migrationId = $migrationId }
    Assert-ApiSuccess $status 'Embedding migration status'
    Write-Host ("Migration {0}: documents {1}/{2}, memories {3}/{4}" -f $status.data.status, $status.data.documentProcessed, $status.data.documentTotal, $status.data.memoryProcessed, $status.data.memoryTotal)
    if ($status.data.status -eq 'FAILED') { throw 'Migration failed; old runtime remains active. Inspect the protected admin migration status for details.' }
} while ($status.data.status -ne 'COMPLETED' -and [DateTime]::UtcNow -lt $deadline)
if ($status.data.status -ne 'COMPLETED') { throw 'Timed out while waiting for the migration; old runtime remains active.' }
$final = Invoke-AdminPost 'Final embedding configuration query' '/admin/model-config/embedding/query' $headers @{}
Assert-ApiSuccess $final 'Final embedding configuration query'
if ($final.data.configVersion -ne $change.data.configVersion) { throw 'Completed migration did not activate the expected embedding version.' }
Write-Host 'Blue-green migration completed and the target runtime is active.'
