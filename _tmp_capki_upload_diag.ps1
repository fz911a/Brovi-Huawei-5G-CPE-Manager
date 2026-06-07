param(
    [string]$BaseUrl = "http://10.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "12344321",
    [string]$CertPath = "",
    [int]$TokenTryMax = 12,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

function XV([string]$xml, [string]$tag) {
    $m = [regex]::Match($xml, "<$tag>([^<]*)</$tag>", [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($m.Success) { return $m.Groups[1].Value }
    return ""
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
    } finally {
        $h.Dispose()
    }
}

function Sha256([byte[]]$data) {
    $s = [Security.Cryptography.SHA256]::Create()
    try {
        return $s.ComputeHash($data)
    } finally {
        $s.Dispose()
    }
}

function XorBytes([byte[]]$a, [byte[]]$b) {
    $n = [Math]::Min($a.Length, $b.Length)
    $o = New-Object byte[] $n
    for ($i = 0; $i -lt $n; $i++) { $o[$i] = $a[$i] -bxor $b[$i] }
    return $o
}

function New-RandomHex([int]$byteLen) {
    $bytes = New-Object byte[] $byteLen
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
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
        if (-not [string]::IsNullOrWhiteSpace($v)) { return $v.Trim() }
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
    if ([string]::IsNullOrWhiteSpace($requestToken)) { throw "NO_STABLE_TOKEN" }

    $firstnonce = New-RandomHex 32
    $challengeBody = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><username>$Username</username><firstnonce>$firstnonce</firstnonce><mode>1</mode><loginflag>2</loginflag></request>"
    $challengeResp = Invoke-Api -Url "$BaseUrl/api/user/challenge_login" -Method "POST" -Session $session -Token $requestToken -Body $challengeBody
    $challengeCode = XV $challengeResp.body "code"
    if (-not [string]::IsNullOrWhiteSpace($challengeCode)) { throw "CHALLENGE_FAIL_$challengeCode" }

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

    $authToken = [string]$challengeResp.headers["__RequestVerificationToken"]
    if ([string]::IsNullOrWhiteSpace($authToken)) { $authToken = Get-HeaderToken $challengeResp.headers }
    if ([string]::IsNullOrWhiteSpace($authToken)) { $authToken = $requestToken }
    $authBody = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><clientproof>$clientProof</clientproof><finalnonce>$servernonce</finalnonce><loginflag>2</loginflag></request>"
    $authResp = Invoke-Api -Url "$BaseUrl/api/user/authentication_login" -Method "POST" -Session $session -Token $authToken -Body $authBody
    $authCode = XV $authResp.body "code"
    if (-not [string]::IsNullOrWhiteSpace($authCode)) { throw "AUTH_FAIL_$authCode" }

    return [pscustomobject]@{
        Session = $session
    }
}

function Invoke-CapkiUpload(
    [object]$Session,
    [string]$Token,
    [string]$FilePath,
    [bool]$IncludeCsrfField,
    [bool]$IncludeHeaderToken
) {
    $phase = "init"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        [System.Net.ServicePointManager]::Expect100Continue = $false
        $boundary = "----WebKitFormBoundary" + [Guid]::NewGuid().ToString("N")
        $crlf = "`r`n"
        $enc = [System.Text.Encoding]::UTF8
        $fileName = [System.IO.Path]::GetFileName($FilePath)
        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)

        $ms = New-Object System.IO.MemoryStream
        $writer = New-Object System.IO.BinaryWriter($ms)
        try {
            if ($IncludeCsrfField -and -not [string]::IsNullOrWhiteSpace($Token)) {
                $csrfPart =
                    "--$boundary$crlf" +
                    "Content-Disposition: form-data; name=`"csrf_token`"$crlf$crlf" +
                    "csrf:$Token$crlf"
                $writer.Write($enc.GetBytes($csrfPart))
            }

            $fileHead =
                "--$boundary$crlf" +
                "Content-Disposition: form-data; name=`"pkicert`"; filename=`"$fileName`"$crlf" +
                "Content-Type: text/plain$crlf$crlf"
            $writer.Write($enc.GetBytes($fileHead))
            $writer.Write($fileBytes)
            $writer.Write($enc.GetBytes($crlf))
            $writer.Write($enc.GetBytes("--$boundary--$crlf"))
            $writer.Flush()
            $bodyBytes = $ms.ToArray()
        } finally {
            $writer.Dispose()
            $ms.Dispose()
        }

        $phase = "request"
        $req = [System.Net.HttpWebRequest]::Create("$BaseUrl/api/capki/uploadpkicertification")
        $req.Method = "POST"
        $req.CookieContainer = $Session.Cookies
        $req.ContentType = "multipart/form-data; boundary=$boundary"
        $req.Accept = "*/*"
        $req.Timeout = 25000
        $req.ReadWriteTimeout = 25000
        $req.KeepAlive = $false
        $req.Headers["_ResponseSource"] = "Broswer"
        if ($IncludeHeaderToken -and -not [string]::IsNullOrWhiteSpace($Token)) {
            $req.Headers["__RequestVerificationToken"] = $Token
        }
        $req.ContentLength = $bodyBytes.Length

        $phase = "write"
        $reqStream = $req.GetRequestStream()
        try {
            $reqStream.Write($bodyBytes, 0, $bodyBytes.Length)
        } finally {
            $reqStream.Close()
        }

        $phase = "response"
        $resp = $req.GetResponse()
        $http = 0
        $body = ""
        try {
            $http = [int]([System.Net.HttpWebResponse]$resp).StatusCode
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            try {
                $body = $sr.ReadToEnd()
            } finally {
                $sr.Close()
            }
        } finally {
            $resp.Close()
        }

        $sw.Stop()
        return [pscustomobject]@{
            http = $http
            body = $body
            error = ""
            elapsed_ms = [int]$sw.ElapsedMilliseconds
        }
    } catch {
        $http = 0
        $body = ""
        if ($_.Exception.Response -ne $null) {
            $r = $_.Exception.Response
            $http = [int]$r.StatusCode
            $sr = New-Object System.IO.StreamReader($r.GetResponseStream())
            try {
                $body = $sr.ReadToEnd()
            } finally {
                $sr.Close()
            }
        }
        $sw.Stop()
        $msg = "phase=$phase | " + $_.Exception.Message
        if ($_.Exception.InnerException -ne $null -and -not [string]::IsNullOrWhiteSpace($_.Exception.InnerException.Message)) {
            $msg = $msg + " | inner: " + $_.Exception.InnerException.Message
        }
        return [pscustomobject]@{
            http = $http
            body = $body
            error = $msg
            elapsed_ms = [int]$sw.ElapsedMilliseconds
        }
    }
}

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path (Get-Location) ("tmp_capki_diag_" + (Get-Date).ToString("yyyyMMdd_HHmmss"))
}
if (-not (Test-Path -LiteralPath $OutDir)) {
    New-Item -Path $OutDir -ItemType Directory | Out-Null
}

$certToUse = $CertPath
if ([string]::IsNullOrWhiteSpace($certToUse)) {
    $certToUse = Join-Path $OutDir "dummy_offline_cert.txt"
    @(
        "CAPKI-DIAG-DUMMY",
        "time=" + (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"),
        "host=" + $env:COMPUTERNAME,
        "note=this is a dummy offline cert file for endpoint diagnostics"
    ) | Set-Content -Path $certToUse -Encoding ASCII
}
if (-not (Test-Path -LiteralPath $certToUse)) {
    throw "CERT_FILE_NOT_FOUND: $certToUse"
}

$ctx = $null
$lastErr = ""
for ($i = 1; $i -le 3; $i++) {
    try {
        $ctx = Login-Developer
        break
    } catch {
        $lastErr = $_.Exception.Message
        Start-Sleep -Milliseconds 500
    }
}
if ($null -eq $ctx) { throw "LOGIN_FAILED_AFTER_RETRY: $lastErr" }

$session = $ctx.Session
$rows = New-Object System.Collections.Generic.List[object]

# Baseline checks
$atStatus = Invoke-Api -Url "$BaseUrl/api/developer/atport-status" -Method "GET" -Session $session -Token "" -Body ""
[System.IO.File]::WriteAllText((Join-Path $OutDir "atport_status.xml"), $atStatus.body, [System.Text.Encoding]::UTF8)
$rows.Add([pscustomobject]@{
    step = "baseline_get"
    mode = "atport-status"
    http = $atStatus.http
    code = XV $atStatus.body "code"
    response = XV $atStatus.body "response"
    error = $atStatus.error
    elapsed_ms = 0
    preview = (($atStatus.body -replace "\s+", " ").Substring(0, [Math]::Min(220, ($atStatus.body -replace "\s+", " ").Length)))
})

$modes = @(
    @{ name = "frontend_exact"; includeCsrf = $true; includeHeader = $false },
    @{ name = "csrf_plus_header"; includeCsrf = $true; includeHeader = $true },
    @{ name = "header_only"; includeCsrf = $false; includeHeader = $true },
    @{ name = "csrf_only_refresh"; includeCsrf = $true; includeHeader = $false }
)

foreach ($m in $modes) {
    $token = Get-StableToken -Session $session
    $r = Invoke-CapkiUpload -Session $session -Token $token -FilePath $certToUse -IncludeCsrfField $m.includeCsrf -IncludeHeaderToken $m.includeHeader
    $body = [string]$r.body
    $code = XV $body "code"
    $respVal = XV $body "response"
    $preview = ($body -replace "\s+", " ")
    if ($preview.Length -gt 260) { $preview = $preview.Substring(0, 260) }
    [System.IO.File]::WriteAllText((Join-Path $OutDir ("capki_" + $m.name + "_raw.txt")), $body, [System.Text.Encoding]::UTF8)

    $rows.Add([pscustomobject]@{
        step = "capki_upload"
        mode = [string]$m.name
        http = $r.http
        code = $code
        response = $respVal
        error = $r.error
        elapsed_ms = $r.elapsed_ms
        preview = $preview
    })
}

$csvPath = Join-Path $OutDir "capki_upload_diag.csv"
$rows | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8

$okRows = $rows | Where-Object { $_.response -eq "OK" }
$meta = [ordered]@{
    base_url = $BaseUrl
    cert_path = $certToUse
    cert_size = (Get-Item -LiteralPath $certToUse).Length
    out_dir = $OutDir
    csv = $csvPath
    success_modes = @($okRows | Select-Object -ExpandProperty mode)
    generated_at = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
}
[System.IO.File]::WriteAllText((Join-Path $OutDir "meta.json"), ($meta | ConvertTo-Json -Depth 5), [System.Text.Encoding]::UTF8)

Write-Output ("CERT=" + $certToUse)
Write-Output ("ROWS=" + $rows.Count)
Write-Output ("SUCCESS_MODES=" + (($meta.success_modes -join ",")))
Write-Output ("OUT_DIR=" + $OutDir)
