# Windows PowerShell version of verify.sh
# Usage: .\scripts\verify.ps1 [-Gateway http://localhost:8080]
param([string]$Gateway = "http://localhost:8080")
$pass = 0; $fail = 0

function Check($name, $expStatus, $expPath, $method, $url, $body) {
    try {
        $params = @{ Uri = $url; Method = $method; UseBasicParsing = $true; ErrorAction = 'Stop' }
        if ($body) { $params.Body = $body; $params.ContentType = 'application/json' }
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode; $got = $r.Headers['X-Received-Path']
    } catch {
        $status = [int]$_.Exception.Response.StatusCode; $got = $null
    }
    if ($got -is [array]) { $got = $got[0] }
    if ($status -eq $expStatus -and ($expPath -eq '-' -or $got -eq $expPath)) {
        Write-Host "PASS  $name  (status=$status, backend received=$got)" -ForegroundColor Green; $script:pass++
    } else {
        Write-Host "FAIL  $name  (status=$status expected $expStatus, backend received=$got expected $expPath)" -ForegroundColor Red; $script:fail++
    }
}

$new = '{"holderName":"Neha Kapoor","type":"SAVINGS","openingBalance":5000,"currency":"INR"}'
Check "GET  /accounts -> rewrite"         200 '/api/v1/internal/accounts'         GET  "$Gateway/accounts"
Check "GET  /accounts/ACC1001 -> rewrite" 200 '/api/v1/internal/accounts/ACC1001' GET  "$Gateway/accounts/ACC1001"
Check "POST /accounts -> rewrite"         201 '/api/v1/internal/accounts'         POST "$Gateway/accounts" $new
Check "GET  StripPrefix route"            200 '/api/v1/internal/accounts'         GET  "$Gateway/account-service/api/v1/internal/accounts"
Check "GET  /api/v1/internal -> blocked"  403 '-'                                 GET  "$Gateway/api/v1/internal/accounts"
Write-Host "Passed: $pass  Failed: $fail"
