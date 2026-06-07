param(
    [string]$BaseUrl = "http://10.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "12344321",
    [int]$PollCount = 15,
    [int]$PollIntervalSeconds = 2
)

$ErrorActionPreference = "Stop"

function Convert-HexToBytes([string]$hex) {
    $h = $hex.Trim()
    if (($h.Length % 2) -ne 0) { throw "hex length is odd: $h" }
    $bytes = New-Object byte[] ($h.Length / 2)
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        $bytes[$i] = [Convert]::ToByte($h.Substring($i * 2, 2), 16)
    }
    return $bytes
}

function Convert-BytesToHex([byte[]]$bytes) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) { [void]$sb.Append($b.ToString("x2")) }
    return $sb.ToString()
}

function New-RandomHex([int]$byteLen) {
    $bytes = New-Object byte[] $byteLen
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return Convert-BytesToHex $bytes
}

function Invoke-Sha256([byte[]]$data) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return $sha.ComputeHash($data) } finally { $sha.Dispose() }
}

function Invoke-HmacSha256([byte[]]$key, [byte[]]$data) {
    $h = New-Object System.Security.Cryptography.HMACSHA256($key)
    try { return $h.ComputeHash($data) } finally { $h.Dispose() }
}

function Invoke-Pbkdf2Sha256([string]$password, [byte[]]$salt, [int]$iterations, [int]$keyLen) {
    $pbkdf2 = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($password, $salt, $iterations, [System.Security.Cryptography.HashAlgorithmName]::SHA256)
    try { return $pbkdf2.GetBytes($keyLen) } finally { $pbkdf2.Dispose() }
}

function Invoke-Xor([byte[]]$a, [byte[]]$b) {
    $n = [Math]::Min($a.Length, $b.Length)
    $o = New-Object byte[] $n
    for ($i = 0; $i -lt $n; $i++) { $o[$i] = $a[$i] -bxor $b[$i] }
    return $o
}

function Get-XmlValue([string]$xml, [string]$tag) {
    $open = "<$tag>"
    $close = "</$tag>"
    $i = $xml.IndexOf($open, [System.StringComparison]::OrdinalIgnoreCase)
    if ($i -lt 0) { return "" }
    $i += $open.Length
    $j = $xml.IndexOf($close, $i, [System.StringComparison]::OrdinalIgnoreCase)
    if ($j -lt 0) { return "" }
    return $xml.Substring($i, $j - $i)
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
                $val = ($node.InnerText | Out-String).Trim()
                if ($map.Contains($name)) {
                    $map[$name] = "$($map[$name])|$val"
                } else {
                    $map[$name] = $val
                }
            }
        }
    } catch {
        # ignore parse errors
    }
    return $map
}

function Send-Http([string]$method, [string]$url, [string]$body, [hashtable]$headers) {
    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = $method
    $req.Timeout = 10000
    $req.ReadWriteTimeout = 10000
    $req.Accept = "*/*"
    $req.UserAgent = "Mozilla/5.0"

    if ($null -ne $headers) {
        foreach ($k in $headers.Keys) {
            if ($k -ieq "Content-Type") {
                $req.ContentType = [string]$headers[$k]
            } else {
                $req.Headers[$k] = [string]$headers[$k]
            }
        }
    }

    if (-not [string]::IsNullOrEmpty($body)) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        if ([string]::IsNullOrEmpty($req.ContentType)) { $req.ContentType = "application/xml" }
        $req.ContentLength = $bytes.Length
        $ws = $req.GetRequestStream()
        $ws.Write($bytes, 0, $bytes.Length)
        $ws.Close()
    }

    $resp = $null
    try {
        $resp = [System.Net.HttpWebResponse]$req.GetResponse()
    } catch [System.Net.WebException] {
        if ($_.Exception.Response -ne $null) {
            $resp = [System.Net.HttpWebResponse]$_.Exception.Response
        } else {
            throw
        }
    }

    $hdr = @{}
    foreach ($key in $resp.Headers.AllKeys) { $hdr[$key] = $resp.Headers[$key] }
    $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $text = $sr.ReadToEnd()
    $sr.Close()
    $status = [int]$resp.StatusCode
    $resp.Close()
    return [pscustomobject]@{ Status = $status; Headers = $hdr; Body = $text }
}

function Update-SessionIdFromSetCookie([string]$setCookie, [string]$currentSession) {
    if ([string]::IsNullOrWhiteSpace($setCookie)) { return $currentSession }
    $m = [regex]::Match($setCookie, "SessionID=([^;]+)")
    if ($m.Success) { return $m.Groups[1].Value }
    return $currentSession
}

function Format-SessionCookie([string]$sessionText) {
    if ([string]::IsNullOrWhiteSpace($sessionText)) { return "" }
    $s = $sessionText.Trim()
    if ($s.Contains("=")) { return $s }
    return "SessionID=$s"
}

function Extract-UsableToken([hashtable]$headers, [string]$fallback) {
    $t = ""
    if ($headers.ContainsKey("__RequestVerificationToken")) { $t = [string]$headers["__RequestVerificationToken"] }
    if ([string]::IsNullOrWhiteSpace($t) -and $headers.ContainsKey("__RequestVerificationTokenone")) { $t = [string]$headers["__RequestVerificationTokenone"] }
    if ([string]::IsNullOrWhiteSpace($t)) { $t = $fallback }
    if ($t.Contains("#")) { $t = $t.Split("#")[0] }
    if ($t.Length -gt 32) { $t = $t.Substring(32) }
    return $t
}

$outputDir = Join-Path (Get-Location) "tmp_api_probe"
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -Path $outputDir -ItemType Directory | Out-Null
}

# 1) SesTokInfo
$r1 = Send-Http "GET" "$BaseUrl/api/webserver/SesTokInfo" $null $null
$tok = Get-XmlValue $r1.Body "TokInfo"
$ses = Get-XmlValue $r1.Body "SesInfo"
if ([string]::IsNullOrWhiteSpace($tok) -or [string]::IsNullOrWhiteSpace($ses)) {
    throw "SesTokInfo failed: $($r1.Body)"
}

# 2) challenge_login (loginflag=2)
$firstNonce = New-RandomHex 32
$challengeXml = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><username>$Username</username><firstnonce>$firstNonce</firstnonce><mode>1</mode><loginflag>2</loginflag></request>"
$h2 = @{
    "__RequestVerificationToken" = $tok
    "Cookie" = (Format-SessionCookie $ses)
}
$r2 = Send-Http "POST" "$BaseUrl/api/user/challenge_login" $challengeXml $h2
$ses = Update-SessionIdFromSetCookie $r2.Headers["Set-Cookie"] $ses
$tok2 = if ($r2.Headers.ContainsKey("__RequestVerificationToken")) { $r2.Headers["__RequestVerificationToken"] } else { $tok }

if ($r2.Body -match "<error>") {
    throw "challenge_login failed: $($r2.Body)"
}

$saltHex = Get-XmlValue $r2.Body "salt"
$itersText = Get-XmlValue $r2.Body "iterations"
$serverNonce = Get-XmlValue $r2.Body "servernonce"
$iters = 0
[void][int]::TryParse($itersText, [ref]$iters)
if ([string]::IsNullOrWhiteSpace($saltHex) -or $iters -le 0 -or [string]::IsNullOrWhiteSpace($serverNonce)) {
    throw "invalid challenge response: $($r2.Body)"
}

# 3) authentication_login (loginflag=2)
$salt = Convert-HexToBytes $saltHex
$saltedPassword = Invoke-Pbkdf2Sha256 $Password $salt $iters 32
$authMessage = "$firstNonce,$serverNonce,$serverNonce"
$clientKey = Invoke-HmacSha256 ([System.Text.Encoding]::UTF8.GetBytes("Client Key")) $saltedPassword
$storedKey = Invoke-Sha256 $clientKey
$clientSignature = Invoke-HmacSha256 ([System.Text.Encoding]::UTF8.GetBytes($authMessage)) $storedKey
$clientProof = Invoke-Xor $clientKey $clientSignature
$clientProofHex = Convert-BytesToHex $clientProof

$authXml = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><clientproof>$clientProofHex</clientproof><finalnonce>$serverNonce</finalnonce><loginflag>2</loginflag></request>"
$h3 = @{
    "__RequestVerificationToken" = [string]$tok2
    "Cookie" = (Format-SessionCookie $ses)
}
$r3 = Send-Http "POST" "$BaseUrl/api/user/authentication_login" $authXml $h3
$ses = Update-SessionIdFromSetCookie $r3.Headers["Set-Cookie"] $ses
if ($r3.Body -match "<error>") {
    throw "authentication_login failed: $($r3.Body)"
}
$postToken = Extract-UsableToken $r3.Headers ([string]$tok2)
if ([string]::IsNullOrWhiteSpace($postToken)) {
    $postToken = [string]$tok
}

$sessionHeader = @{
    "Cookie" = (Format-SessionCookie $ses)
    "__RequestVerificationToken" = $postToken
}

# Probe converged-status endpoint variants once.
$convergedCandidates = @(
    "/api/monitoring/converged-status",
    "/api/monitoring/converged_status",
    "/api/device/converged-status",
    "/api/device/converged_status"
)
$convergedPick = $null
$convergedProbe = @()
foreach ($ep in $convergedCandidates) {
    $resp = Send-Http "GET" "$BaseUrl$ep" $null $sessionHeader
    $map = Convert-XmlToFlatMap $resp.Body
    $code = if ($map.Contains("code")) { $map["code"] } else { "" }
    $ok = ($resp.Status -eq 200 -and [string]::IsNullOrWhiteSpace($code) -and $resp.Body -notmatch "<error>")
    $convergedProbe += [pscustomobject]@{
        endpoint = $ep
        http = $resp.Status
        code = $code
        bodyPreview = ($resp.Body -replace "\s+", " ").Substring(0, [Math]::Min(220, ($resp.Body -replace "\s+", " ").Length))
        ok = $ok
    }
    if ($ok -and $null -eq $convergedPick) { $convergedPick = $ep }
}

if ($null -eq $convergedPick) {
    # If all failed, still use the most likely one for polling record.
    $convergedPick = "/api/monitoring/converged-status"
}

# 4) Poll status/basic/converged
$pollRows = New-Object System.Collections.Generic.List[object]
for ($i = 1; $i -le $PollCount; $i++) {
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

    $statusResp = Send-Http "GET" "$BaseUrl/api/monitoring/status" $null $sessionHeader
    $basicResp = Send-Http "GET" "$BaseUrl/api/device/basic_information" $null $sessionHeader
    $convResp = Send-Http "GET" "$BaseUrl$convergedPick" $null $sessionHeader

    $statusMap = Convert-XmlToFlatMap $statusResp.Body
    $basicMap = Convert-XmlToFlatMap $basicResp.Body
    $convMap = Convert-XmlToFlatMap $convResp.Body

    $pollRows.Add([pscustomobject]@{
        timestamp = $ts
        status_http = $statusResp.Status
        status_code = $statusMap["code"]
        ConnectionStatus = $statusMap["ConnectionStatus"]
        CurrentNetworkTypeEx = $statusMap["CurrentNetworkTypeEx"]
        SignalIcon = $statusMap["SignalIcon"]
        WanIPAddress = $statusMap["WanIPAddress"]
        PrimaryDns = $statusMap["PrimaryDns"]
        SecondaryDns = $statusMap["SecondaryDns"]
        CurrentDownloadRate = $statusMap["CurrentDownloadRate"]
        CurrentUploadRate = $statusMap["CurrentUploadRate"]
        basic_http = $basicResp.Status
        basic_code = $basicMap["code"]
        DeviceName = $basicMap["DeviceName"]
        device_name_alt = $basicMap["devicename"]
        classify = $basicMap["classify"]
        softwareversion = $basicMap["SoftwareVersion"]
        webui_version = $basicMap["WebUIVersion"]
        SerialNumber = $basicMap["SerialNumber"]
        Imei = $basicMap["Imei"]
        Imsi = $basicMap["Imsi"]
        Msisdn = $basicMap["Msisdn"]
        CountryCode = $basicMap["CountryCode"]
        converged_endpoint = $convergedPick
        converged_http = $convResp.Status
        converged_code = $convMap["code"]
        converged_keys = (($convMap.Keys | Select-Object -First 12) -join "|")
    })

    if ($i -lt $PollCount) { Start-Sleep -Seconds $PollIntervalSeconds }
}

# Save raw snapshots
$statusLast = Send-Http "GET" "$BaseUrl/api/monitoring/status" $null $sessionHeader
$basicLast = Send-Http "GET" "$BaseUrl/api/device/basic_information" $null $sessionHeader
$convLast = Send-Http "GET" "$BaseUrl$convergedPick" $null $sessionHeader

[System.IO.File]::WriteAllText((Join-Path $outputDir "status_last.xml"), $statusLast.Body, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $outputDir "basic_information_last.xml"), $basicLast.Body, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $outputDir "converged_last.xml"), $convLast.Body, [System.Text.Encoding]::UTF8)

$pollCsv = Join-Path $outputDir "poll_table.csv"
$pollRows | Export-Csv -Path $pollCsv -NoTypeInformation -Encoding UTF8

# 5) Developer log channel probe
$devGetEndpoints = @(
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

$postPayloads = @(
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><enable>1</enable></request>",
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><switch>1</switch></request>",
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><mode>1</mode></request>",
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><state>1</state></request>"
)

$devRows = New-Object System.Collections.Generic.List[object]
foreach ($ep in $devGetEndpoints) {
    $g = Send-Http "GET" "$BaseUrl$ep" $null $sessionHeader
    $gMap = Convert-XmlToFlatMap $g.Body
    $gCode = if ($gMap.Contains("code")) { $gMap["code"] } else { "" }
    $gPreview = ($g.Body -replace "\s+", " ")
    if ($gPreview.Length -gt 220) { $gPreview = $gPreview.Substring(0, 220) }
    $devRows.Add([pscustomobject]@{
        endpoint = $ep
        method = "GET"
        http = $g.Status
        code = $gCode
        preview = $gPreview
    })

    foreach ($payload in $postPayloads) {
        $p = Send-Http "POST" "$BaseUrl$ep" $payload $sessionHeader
        $pMap = Convert-XmlToFlatMap $p.Body
        $pCode = if ($pMap.Contains("code")) { $pMap["code"] } else { "" }
        $pPreview = ($p.Body -replace "\s+", " ")
        if ($pPreview.Length -gt 220) { $pPreview = $pPreview.Substring(0, 220) }
        $devRows.Add([pscustomobject]@{
            endpoint = $ep
            method = "POST"
            http = $p.Status
            code = $pCode
            preview = $pPreview
        })
    }
}

$devCsv = Join-Path $outputDir "developer_log_probe.csv"
$devRows | Export-Csv -Path $devCsv -NoTypeInformation -Encoding UTF8

$convergedProbeCsv = Join-Path $outputDir "converged_probe.csv"
$convergedProbe | Export-Csv -Path $convergedProbeCsv -NoTypeInformation -Encoding UTF8

$meta = [ordered]@{
    login = "ok"
    session = $ses
    tokenLen = $postToken.Length
    converged_endpoint = $convergedPick
    poll_rows = $pollRows.Count
    dev_probe_rows = $devRows.Count
    output_dir = $outputDir
    poll_csv = $pollCsv
    dev_csv = $devCsv
    status_xml = (Join-Path $outputDir "status_last.xml")
    basic_xml = (Join-Path $outputDir "basic_information_last.xml")
    converged_xml = (Join-Path $outputDir "converged_last.xml")
}
$metaJson = ($meta | ConvertTo-Json -Depth 4)
[System.IO.File]::WriteAllText((Join-Path $outputDir "meta.json"), $metaJson, [System.Text.Encoding]::UTF8)

Write-Output "LOGIN_OK"
Write-Output "CONVERGED_PICK=$convergedPick"
Write-Output "POLL_ROWS=$($pollRows.Count)"
Write-Output "DEV_PROBE_ROWS=$($devRows.Count)"
Write-Output "OUT_DIR=$outputDir"
