[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8090',
    [string]$AdministratorToken = $env:DOCHUB_ADMIN_BEARER_TOKEN,
    [string]$DashScopeApiKey = $env:ALI_BAI_LIAN_API_KEY,
    [string]$DashScopeModel = 'qwen-plus',
    [string]$LocalModel = 'qwen2.5:7b'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Require-Value([string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Name must be supplied by the caller or environment."
    }
}

function Assert-ApiSuccess([object]$Response, [string]$Operation) {
    if ($null -eq $Response -or [string]$Response.code -ne '0') {
        throw "$Operation did not return a successful API response."
    }
}

function Assert-MaskedQueryResponse([object]$Response) {
    $json = $Response | ConvertTo-Json -Depth 16 -Compress
    if ($json -match '"(?:apiKey|encryptedApiKey)"') {
        throw 'The model configuration query exposed a credential field.'
    }
}

function Invoke-AdminPost([string]$Operation, [string]$Uri, [hashtable]$Headers, [string]$Body) {
    try {
        return Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType 'application/json' -Body $Body
    }
    catch {
        # Never emit HTTP error details because provider diagnostics can contain sensitive values.
        throw "$Operation request failed."
    }
}

function Invoke-ConnectionTest([string]$Name, [hashtable]$Candidate, [hashtable]$Headers, [string]$Endpoint) {
    try {
        $response = Invoke-AdminPost "$Name connection test" "$Endpoint/admin/model-config/chat/test" `
            $Headers ($Candidate | ConvertTo-Json -Compress)
        Assert-ApiSuccess $response "$Name connection test"
        if ($response.data.success -ne $true) {
            throw "$Name connection test was rejected."
        }
        Write-Host "$Name connection test passed."
    }
    catch {
        # Do not print server bodies or exception details because they can contain provider diagnostics.
        throw "$Name connection test failed."
    }
}

Require-Value 'DOCHUB_ADMIN_BEARER_TOKEN' $AdministratorToken
Require-Value 'ALI_BAI_LIAN_API_KEY' $DashScopeApiKey

$endpoint = $BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $AdministratorToken" }

$initialQuery = Invoke-AdminPost 'Initial model configuration query' "$endpoint/admin/model-config/query" $headers '{}'
Assert-ApiSuccess $initialQuery 'Initial model configuration query'
Assert-MaskedQueryResponse $initialQuery

Invoke-ConnectionTest 'DashScope-compatible' @{
    deploymentType = 'REMOTE'
    compatibilityPreset = 'DASHSCOPE'
    baseUrl = 'https://dashscope.aliyuncs.com/compatible-mode/'
    requestPath = '/v1/chat/completions'
    modelName = $DashScopeModel
    apiKey = $DashScopeApiKey
    temperature = 0.1
    maxTokens = 64
    timeoutMillis = 30000
    toolCallingSupported = $false
} $headers $endpoint

Invoke-ConnectionTest 'Local Ollama-compatible' @{
    deploymentType = 'LOCAL'
    compatibilityPreset = 'OLLAMA'
    baseUrl = 'http://127.0.0.1:11434/v1'
    requestPath = '/v1/chat/completions'
    modelName = $LocalModel
    apiKey = ''
    temperature = 0.1
    maxTokens = 64
    timeoutMillis = 30000
    toolCallingSupported = $false
} $headers $endpoint

$finalQuery = Invoke-AdminPost 'Final model configuration query' "$endpoint/admin/model-config/query" $headers '{}'
Assert-ApiSuccess $finalQuery 'Final model configuration query'
Assert-MaskedQueryResponse $finalQuery
Write-Host 'Model runtime verification passed; query responses contained no credential fields.'
