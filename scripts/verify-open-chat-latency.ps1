param(
    [string]$BaseUrl = 'http://127.0.0.1:9086',
    [ValidateRange(1, 200)]
    [int]$Samples = 20,
    [ValidateRange(1, 120000)]
    [int]$P95CeilingMs = 5000,
    [string]$BearerToken = $env:DOCHUB_CHAT_BEARER_TOKEN
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Net.Http

$handler = [System.Net.Http.HttpClientHandler]::new()
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromMinutes(5)
if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
    $client.DefaultRequestHeaders.Authorization =
        [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $BearerToken)
}

function New-JsonContent {
    param([Parameter(Mandatory)] [object]$Body)
    $json = $Body | ConvertTo-Json -Compress
    return [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
}

function Get-ExchangeTrace {
    param(
        [Parameter(Mandatory)] [string]$ConversationId,
        [Parameter(Mandatory)] [long]$ExchangeId
    )
    $content = New-JsonContent -Body @{ conversationId = $ConversationId; exchangeId = [string]$ExchangeId }
    try {
        $response = $client.PostAsync(
            "$($BaseUrl.TrimEnd('/'))/api/chat/exchange/detail", $content).GetAwaiter().GetResult()
        try {
            $response.EnsureSuccessStatusCode() | Out-Null
            $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            return $payload.data.exchange.debugTrace
        }
        finally {
            $response.Dispose()
        }
    }
    finally {
        $content.Dispose()
    }
}

function Invoke-DirectChatSample {
    param([Parameter(Mandatory)] [int]$Index)

    $conversationId = "latency-$([Guid]::NewGuid().ToString('N'))"
    $content = New-JsonContent -Body @{
        question = "用一句话解释 Java record（延迟样本 $Index）"
        conversationId = $conversationId
        chatMode = 'OPEN_CHAT'
    }
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$($BaseUrl.TrimEnd('/'))/api/chat/stream")
    $request.Headers.Accept.Add(
        [System.Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new('text/event-stream'))
    $request.Content = $content
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $response = $null
    $firstTextMs = $null
    $exchangeId = 0L
    try {
        $response = $client.SendAsync(
            $request,
            [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        $response.EnsureSuccessStatusCode() | Out-Null
        $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8)
        try {
            while (-not $reader.EndOfStream) {
                $line = $reader.ReadLineAsync().GetAwaiter().GetResult()
                if ([string]::IsNullOrWhiteSpace($line)) { continue }
                $raw = if ($line.StartsWith('data:')) { $line.Substring(5).Trim() } else { $line.Trim() }
                if (-not $raw.StartsWith('{')) { continue }
                try { $event = $raw | ConvertFrom-Json } catch { continue }
                if ($event.exchangeId) { $exchangeId = [long]$event.exchangeId }
                if ($null -eq $firstTextMs -and $event.type -eq 'text' -and
                    -not [string]::IsNullOrWhiteSpace([string]$event.content)) {
                    $firstTextMs = $timer.ElapsedMilliseconds
                }
            }
        }
        finally {
            $reader.Dispose()
            $stream.Dispose()
        }
    }
    finally {
        $timer.Stop()
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }

    if ($null -eq $firstTextMs) {
        throw "样本 $Index 未收到 text SSE 事件"
    }
    if ($exchangeId -le 0) {
        throw "样本 $Index 未返回 exchangeId"
    }

    $trace = Get-ExchangeTrace -ConversationId $conversationId -ExchangeId $exchangeId
    if ($trace.executionMode -ne 'DIRECT_CHAT') {
        throw "样本 $Index 路由异常：期望 DIRECT_CHAT，实际 $($trace.executionMode)"
    }
    if ($trace.latencyTrace.modelCallCount -ne 1) {
        throw "样本 $Index 模型调用次数异常：$($trace.latencyTrace.modelCallCount)"
    }
    if ($trace.latencyTrace.toolCallCount -ne 0) {
        throw "样本 $Index 发生了工具调用：$($trace.latencyTrace.toolCallCount)"
    }
    return [pscustomobject]@{
        Index = $Index
        TimeToFirstTextMs = [long]$firstTextMs
        TotalMs = [long]$timer.ElapsedMilliseconds
    }
}

try {
    $results = for ($index = 1; $index -le $Samples; $index++) {
        $sample = Invoke-DirectChatSample -Index $index
        Write-Host "[$index/$Samples] TTFT=$($sample.TimeToFirstTextMs)ms total=$($sample.TotalMs)ms"
        $sample
    }
    $sorted = @($results.TimeToFirstTextMs | Sort-Object)
    $p50Index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.50) - 1)
    $p95Index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.95) - 1)
    $p50 = $sorted[$p50Index]
    $p95 = $sorted[$p95Index]
    Write-Host "DIRECT_CHAT samples=$Samples p50=${p50}ms p95=${p95}ms ceiling=${P95CeilingMs}ms"
    if ($p95 -gt $P95CeilingMs) {
        throw "DIRECT_CHAT p95 TTFT ${p95}ms 超过验收阈值 ${P95CeilingMs}ms"
    }
}
finally {
    $client.Dispose()
    $handler.Dispose()
}
