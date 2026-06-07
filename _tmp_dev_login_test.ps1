param(
    [string]$BaseUrl = "http://10.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "12344321",
    [int]$TokenTryMax = 12
)

$ErrorActionPreference = "Stop"

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

$s = $null
Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -SessionVariable s -TimeoutSec 10 | Out-Null
Invoke-WebRequest -Uri "$BaseUrl/api/monitoring/status" -UseBasicParsing -WebSession $s -TimeoutSec 10 | Out-Null

# This firmware's /api/webserver/token may return mixed token forms. Retry until 64-char token without '#'.
$rawToken = ""
for ($i = 1; $i -le $TokenTryMax; $i++) {
    $tk = Invoke-WebRequest -Uri "$BaseUrl/api/webserver/token" -UseBasicParsing -WebSession $s -TimeoutSec 10
    $v = XV $tk.Content "token"
    if ([string]::IsNullOrWhiteSpace($v)) { $v = XV $tk.Content "TokInfo" }
    Write-Output ("TOKEN_TRY=" + $i + " LEN=" + $v.Length + " HASH=" + $v.Contains("#"))
    if ($v.Length -eq 64 -and -not $v.Contains("#")) {
        $rawToken = $v
        break
    }
}
if ([string]::IsNullOrWhiteSpace($rawToken)) {
    throw "NO_STABLE_TOKEN"
}

$requestToken = $rawToken.Substring(32)
$firstnonce = New-RandomHex 32

$challengeBody =
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?>" +
    "<request><username>$Username</username><firstnonce>$firstnonce</firstnonce><mode>1</mode><loginflag>2</loginflag></request>"

$challengeResp = Invoke-WebRequest -Uri "$BaseUrl/api/user/challenge_login" -Method Post -WebSession $s `
    -Headers @{
        "__RequestVerificationToken" = $requestToken
        "_ResponseSource" = "Broswer"
        "Accept" = "*/*"
    } `
    -ContentType "application/x-www-form-urlencoded; charset=UTF-8" `
    -Body $challengeBody -UseBasicParsing -TimeoutSec 10

$challengeXml = $challengeResp.Content
$challengeCode = XV $challengeXml "code"
if (-not [string]::IsNullOrWhiteSpace($challengeCode)) {
    throw "CHALLENGE_FAIL_$challengeCode"
}

$saltHex = XV $challengeXml "salt"
$iterations = [int](XV $challengeXml "iterations")
$servernonce = XV $challengeXml "servernonce"
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

$authToken = [string]$challengeResp.Headers["__RequestVerificationToken"]
$authBody =
    "<?xml version=`"1.0`" encoding=`"UTF-8`"?>" +
    "<request><clientproof>$clientProof</clientproof><finalnonce>$servernonce</finalnonce><loginflag>2</loginflag></request>"

$authResp = Invoke-WebRequest -Uri "$BaseUrl/api/user/authentication_login" -Method Post -WebSession $s `
    -Headers @{
        "__RequestVerificationToken" = $authToken
        "_ResponseSource" = "Broswer"
        "Accept" = "*/*"
    } `
    -ContentType "application/x-www-form-urlencoded; charset=UTF-8" `
    -Body $authBody -UseBasicParsing -TimeoutSec 10

$authXml = $authResp.Content
$authCode = XV $authXml "code"
if ([string]::IsNullOrWhiteSpace($authCode)) { $authCode = "(none)" }

$statusResp = Invoke-WebRequest -Uri "$BaseUrl/api/monitoring/status" -UseBasicParsing -WebSession $s -TimeoutSec 10
$statusCode = XV $statusResp.Content "code"
if ([string]::IsNullOrWhiteSpace($statusCode)) { $statusCode = "(none)" }

$devResp = Invoke-WebRequest -Uri "$BaseUrl/api/developer/log-status" -UseBasicParsing -WebSession $s -TimeoutSec 10
$devCode = XV $devResp.Content "code"
if ([string]::IsNullOrWhiteSpace($devCode)) { $devCode = "(none)" }

Write-Output ("AUTH_CODE=" + $authCode)
Write-Output ("STATUS_CODE=" + $statusCode)
Write-Output ("DEV_LOG_STATUS_CODE=" + $devCode)
