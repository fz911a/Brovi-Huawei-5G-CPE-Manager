param(
    [string]$BaseUrl = "http://10.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "12344321",
    [int]$TokenTryMax = 12,
    [int]$PollCount = 20,
    [int]$PollIntervalSeconds = 2
)

$ErrorActionPreference = "Stop"

function XV([string]$xml, [string]$tag) {
    $m = [regex]::Match($xml, "<$tag>([^<]*)</$tag>", [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($m.Success) { return $m.Groups[1].Value }
    return ""
}

function Convert-XmlToFlatMap([string]$xml) {
    $map = [ordered]@{}
    try {
        [xml]$doc = $xml
        $root = $doc.DocumentElement
        if ($null -eq $root) { return $map }
        foreach ($node in $root.ChildNodes) {
            if ($node.NodeType -eq [System.Xml.XmlNodeType]::Element) {
                $name = $node.Name
                $value = ($node.InnerText | Out-String).Trim()
                if ($map.Contains($name)) {
                    $map[$name] = "$($map[$name])|$value"
                } else {
                    $map[$name] = $value
                }
            }
        }
    } catch {
        # ignore
    }
    return $map
}

function HexToBytes([string]$hex) {
    $h = $hex.Trim()
    $b = New-Object byte[] ($h.Length / 2)
    for ($i = 0; $i -lt $b.Length; $i++) {
        $b[$i] = [Convert]::ToByte($h.Substring($i * 2, 2), 16)
    }
    return $b
}

function BytesToHex([byte[]]$bytes) {
    $sb = New-Object Text.StringBuilder
    foreach ($x in $bytes) { [void]$sb.Append($x.ToString("x2")) }
    return $sb.ToString()
}

function HmacSha256([byte[]]$key, [byte[]]$data) {
    $h = New-Object Security.Cryptography.HMACSHA256
    try {
        $h.Key = $key
        return $h.ComputeHash($data)
    }
    finally {
        $h.Dispose()
    }
}

function Sha256([byte[]]$data) {
    $s = [Security.Cryptography.SHA256]::Create()
    try {
        return $s.ComputeHash($data)
    }
    finally {
        $s.Dispose()
    }
}

function XorBytes([byte[]]$a, [byte[]]$b) {
    $n = [Math]::Min($a.Length, $b.Length)
    $o = New-Object byte[] $n
    for ($i = 0; $i -lt $n; $i++) {
        $o[$i] = $a[$i] -bxor $b[$i]
    }
    return $o
}

function New-RandomHex([int]$byteLen) {
    $bytes = New-Object byte[] $byteLen
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }
    return BytesToHex $bytes
}

function Get-HeaderToken([object]$headers) {
    $keys = @(
        "__RequestVerificationTokenone",
        "__RequestVerificationTokentwo",
        "__RequestVerificationToken",
        "__requestverificationtokenone",
        "__requestverificationtokentwo",
        "__requestverificationtoken"
    )
    foreach ($k in $keys) {
        $v = [string]$headers[$k]
        if (-not [string]::IsNullOrWhiteSpace($v)) {
            return $v.Trim()
        }
    }
    return ""
}

function Invoke-Api(
    [string]$Url,
    [string]$Method,
    [object]$Session,
    [string]$Token,
    [string]$Body
) {
    $headers = @{
        "_ResponseSource" = "Broswer"
        "Accept" = "*/*"
    }
    if ($Method -eq "POST" -and -not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["__RequestVerificationToken"] = $Token
    }

    try {
        if ($Method -eq "GET") {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -WebSession $Session -Headers $headers -TimeoutSec 12
        } else {
            $r = Invoke-WebRequest -Uri $Url -Method Post -UseBasicParsing -WebSession $Session -Headers $headers `
                -ContentType "application/x-www-form-urlencoded; charset=UTF-8" -Body $Body -TimeoutSec 12
        }
        return [pscustomobject]@{
            http = [int]$r.StatusCode
            body = [string]$r.Content
            headers = $r.Headers
            error = ""
        }
    } catch {
        if ($_.Exception.Response -ne $null) {
            $resp = $_.Exception.Response
            $sr = New-Object IO.StreamReader($resp.GetResponseStream())
            $txt = $sr.ReadToEnd()
            $sr.Close()
            return [pscustomobject]@{
                http = [int]$resp.StatusCode
                body = $txt
                headers = $resp.Headers
                error = ""
            }
        }
        return [pscustomobject]@{
            http = 0
            body = ""
            headers = @{}
            error = $_.Exception.Message
        }
    }
}

function Get-StableToken([object]$Session) {
    for ($i = 1; $i -le $TokenTryMax; $i++) {
        $r = Invoke-Api -Url "$BaseUrl/api/webserver/token" -Method "GET" -Session $Session -Token "" -Body ""
        $v = XV $r.body "token"
        if ([string]::IsNullOrWhiteSpace($v)) { $v = XV $r.body "TokInfo" }
        if ($v.Length -eq 64 -and -not $v.Contains("#")) {
            return $v.Substring(32)
        }
    }
    return ""
}

function Login-Developer() {
    $session = $null
    Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -SessionVariable session -TimeoutSec 10 | Out-Null
    Invoke-WebRequest -Uri "$BaseUrl/api/monitoring/status" -UseBasicParsing -WebSession $session -TimeoutSec 10 | Out-Null

    $requestToken = Get-StableToken -Session $session
    if ([string]::IsNullOrWhiteSpace($requestToken)) {
        throw "NO_STABLE_TOKEN"
    }

    $firstnonce = New-RandomHex 32
    $challengeBody = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><username>$Username</username><firstnonce>$firstnonce</firstnonce><mode>1</mode><loginflag>2</loginflag></request>"
    $challengeResp = Invoke-Api -Url "$BaseUrl/api/user/challenge_login" -Method "POST" -Session $session -Token $requestToken -Body $challengeBody
    $challengeCode = XV $challengeResp.body "code"
    if (-not [string]::IsNullOrWhiteSpace($challengeCode)) {
        throw "CHALLENGE_FAIL_$challengeCode"
    }

    $saltHex = XV $challengeResp.body "salt"
    $iterations = [int](XV $challengeResp.body "iterations")
    $servernonce = XV $challengeResp.body "servernonce"
    if ([string]::IsNullOrWhiteSpace($saltHex) -or [string]::IsNullOrWhiteSpace($servernonce) -or $iterations -le 0) {
        throw "CHALLENGE_MISSING_FIELDS"
    }

    $salt = HexToBytes $saltHex
    $pbkdf2 = New-Object Security.Cryptography.Rfc2898DeriveBytes($Password, $salt, $iterations, [Security.Cryptography.HashAlgorithmName]::SHA256)
    $saltedPassword = $pbkdf2.GetBytes(32)
    $pbkdf2.Dispose()

    $authMessage = "$firstnonce,$servernonce,$servernonce"
    $clientKey = HmacSha256 ([Text.Encoding]::UTF8.GetBytes("Client Key")) $saltedPassword
    $storedKey = Sha256 $clientKey
    $clientSignature = HmacSha256 ([Text.Encoding]::UTF8.GetBytes($authMessage)) $storedKey
    $clientProof = BytesToHex (XorBytes $clientKey $clientSignature)

    $authToken = Get-HeaderToken $challengeResp.headers
    if ([string]::IsNullOrWhiteSpace($authToken)) { $authToken = $requestToken }
    $authBody = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><clientproof>$clientProof</clientproof><finalnonce>$servernonce</finalnonce><loginflag>2</loginflag></request>"
    $authResp = Invoke-Api -Url "$BaseUrl/api/user/authentication_login" -Method "POST" -Session $session -Token $authToken -Body $authBody
    $authCode = XV $authResp.body "code"
    if (-not [string]::IsNullOrWhiteSpace($authCode)) {
        throw "AUTH_FAIL_$authCode"
    }

    $nextToken = Get-HeaderToken $authResp.headers
    return [pscustomobject]@{
        Session = $session
        Token = $nextToken
    }
}

$ctx = Login-Developer
$session = $ctx.Session
$token = $ctx.Token

$outDir = Join-Path (Get-Location) "tmp_api_probe_auth"
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -Path $outDir -ItemType Directory | Out-Null
}

# Poll after authenticated login
$pollRows = New-Object System.Collections.Generic.List[object]
for ($i = 1; $i -le $PollCount; $i++) {
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $statusResp = Invoke-Api -Url "$BaseUrl/api/monitoring/status" -Method "GET" -Session $session -Token "" -Body ""
    $basicResp = Invoke-Api -Url "$BaseUrl/api/device/basic_information" -Method "GET" -Session $session -Token "" -Body ""
    $convResp = Invoke-Api -Url "$BaseUrl/api/monitoring/converged-status" -Method "GET" -Session $session -Token "" -Body ""

    $statusMap = Convert-XmlToFlatMap $statusResp.body
    $basicMap = Convert-XmlToFlatMap $basicResp.body
    $convMap = Convert-XmlToFlatMap $convResp.body

    $pollRows.Add([pscustomobject]@{
        timestamp = $ts
        status_http = $statusResp.http
        status_code = $statusMap["code"]
        ConnectionStatus = $statusMap["ConnectionStatus"]
        CurrentNetworkTypeEx = $statusMap["CurrentNetworkTypeEx"]
        CurrentServiceDomain = $statusMap["CurrentServiceDomain"]
        SignalIcon = $statusMap["SignalIcon"]
        WanIPAddress = $statusMap["WanIPAddress"]
        CurrentDownloadRate = $statusMap["CurrentDownloadRate"]
        CurrentUploadRate = $statusMap["CurrentUploadRate"]
        basic_http = $basicResp.http
        basic_code = $basicMap["code"]
        devicename = $basicMap["devicename"]
        classify = $basicMap["classify"]
        spreadname_en = $basicMap["spreadname_en"]
        spreadname_zh = $basicMap["spreadname_zh"]
        converged_http = $convResp.http
        converged_code = $convMap["code"]
        SimState = $convMap["SimState"]
        SimLockEnable = $convMap["SimLockEnable"]
        CurrentLanguage = $convMap["CurrentLanguage"]
        CountryCode = $convMap["CountryCode"]
    })

    if ($i -lt $PollCount) { Start-Sleep -Seconds $PollIntervalSeconds }
}

$pollCsv = Join-Path $outDir "poll_table_auth.csv"
$pollRows | Export-Csv -Path $pollCsv -NoTypeInformation -Encoding UTF8

# Save raw snapshots
$statusLast = Invoke-Api -Url "$BaseUrl/api/monitoring/status" -Method "GET" -Session $session -Token "" -Body ""
$basicLast = Invoke-Api -Url "$BaseUrl/api/device/basic_information" -Method "GET" -Session $session -Token "" -Body ""
$convLast = Invoke-Api -Url "$BaseUrl/api/monitoring/converged-status" -Method "GET" -Session $session -Token "" -Body ""
[System.IO.File]::WriteAllText((Join-Path $outDir "status_last.xml"), $statusLast.body, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $outDir "basic_information_last.xml"), $basicLast.body, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $outDir "converged_last.xml"), $convLast.body, [System.Text.Encoding]::UTF8)

# Probe developer log channels (GET + POST candidates)
$devEndpoints = @(
    "/api/developer/developermode-featureswitch",
    "/api/developer/webapp-support-module",
    "/api/developer/ps-slow",
    "/api/developer/log-status",
    "/api/developer/modem-log",
    "/api/developer/webapp-log",
    "/api/developer/export-log",
    "/api/log/loginfo",
    "/api/log/logexport",
    "/api/diagnosis/oversea-log"
)

$payloads = @(
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><enable>1</enable></request>",
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><switch>1</switch></request>",
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><mode>1</mode></request>",
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><state>1</state></request>"
)

$devRows = New-Object System.Collections.Generic.List[object]
foreach ($ep in $devEndpoints) {
    $g = Invoke-Api -Url "$BaseUrl$ep" -Method "GET" -Session $session -Token "" -Body ""
    $gMap = Convert-XmlToFlatMap $g.body
    $gCode = $gMap["code"]
    $gPreview = ($g.body -replace "\s+", " ")
    if ($gPreview.Length -gt 220) { $gPreview = $gPreview.Substring(0, 220) }
    $devRows.Add([pscustomobject]@{
        endpoint = $ep
        method = "GET"
        http = $g.http
        code = $gCode
        error = $g.error
        preview = $gPreview
    })
    $hTok = Get-HeaderToken $g.headers
    if (-not [string]::IsNullOrWhiteSpace($hTok)) { $token = $hTok }

    foreach ($p in $payloads) {
        if ([string]::IsNullOrWhiteSpace($token)) {
            $token = Get-StableToken -Session $session
        }
        $pr = Invoke-Api -Url "$BaseUrl$ep" -Method "POST" -Session $session -Token $token -Body $p
        $prMap = Convert-XmlToFlatMap $pr.body
        $prCode = $prMap["code"]
        $prPreview = ($pr.body -replace "\s+", " ")
        if ($prPreview.Length -gt 220) { $prPreview = $prPreview.Substring(0, 220) }
        $devRows.Add([pscustomobject]@{
            endpoint = $ep
            method = "POST"
            http = $pr.http
            code = $prCode
            error = $pr.error
            preview = $prPreview
        })
        $pt = Get-HeaderToken $pr.headers
        if (-not [string]::IsNullOrWhiteSpace($pt)) { $token = $pt }
    }
}

$devCsv = Join-Path $outDir "developer_log_probe_auth.csv"
$devRows | Export-Csv -Path $devCsv -NoTypeInformation -Encoding UTF8

$meta = [ordered]@{
    poll_count = $PollCount
    poll_interval_seconds = $PollIntervalSeconds
    poll_csv = $pollCsv
    dev_csv = $devCsv
    out_dir = $outDir
}
[System.IO.File]::WriteAllText((Join-Path $outDir "meta.json"), ($meta | ConvertTo-Json -Depth 4), [System.Text.Encoding]::UTF8)

Write-Output ("POLL_ROWS=" + $pollRows.Count)
Write-Output ("DEV_ROWS=" + $devRows.Count)
Write-Output ("OUT_DIR=" + $outDir)
