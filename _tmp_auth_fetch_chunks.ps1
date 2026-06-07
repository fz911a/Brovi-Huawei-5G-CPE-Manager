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
    for ($i = 0; $i -lt $b.Length; $i++) { $b[$i] = [Convert]::ToByte($h.Substring($i * 2, 2), 16) }
    return $b
}
function BytesToHex([byte[]]$bytes) {
    $sb = New-Object Text.StringBuilder
    foreach ($x in $bytes) { [void]$sb.Append($x.ToString("x2")) }
    return $sb.ToString()
}
function HmacSha256([byte[]]$key, [byte[]]$data) {
    $h = New-Object Security.Cryptography.HMACSHA256
    try { $h.Key = $key; return $h.ComputeHash($data) } finally { $h.Dispose() }
}
function Sha256([byte[]]$data) {
    $s = [Security.Cryptography.SHA256]::Create()
    try { return $s.ComputeHash($data) } finally { $s.Dispose() }
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
    }
    finally {
        $rng.Dispose()
    }
    return BytesToHex $bytes
}

$s = $null
Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -SessionVariable s -TimeoutSec 10 | Out-Null
Invoke-WebRequest -Uri "$BaseUrl/api/monitoring/status" -UseBasicParsing -WebSession $s -TimeoutSec 10 | Out-Null

$raw = ""
for ($i = 1; $i -le $TokenTryMax; $i++) {
    $tk = Invoke-WebRequest -Uri "$BaseUrl/api/webserver/token" -UseBasicParsing -WebSession $s -TimeoutSec 10
    $v = XV $tk.Content "token"
    if ([string]::IsNullOrWhiteSpace($v)) { $v = XV $tk.Content "TokInfo" }
    if ($v.Length -eq 64 -and -not $v.Contains("#")) {
        $raw = $v
        break
    }
}
if ([string]::IsNullOrWhiteSpace($raw)) { throw "NO_STABLE_TOKEN" }
$reqTok = $raw.Substring(32)

$firstnonce = New-RandomHex 32
$challengeBody = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><username>$Username</username><firstnonce>$firstnonce</firstnonce><mode>1</mode><loginflag>2</loginflag></request>"
$cr = Invoke-WebRequest -Uri "$BaseUrl/api/user/challenge_login" -Method Post -WebSession $s -Headers @{
    "__RequestVerificationToken" = $reqTok
    "_ResponseSource" = "Broswer"
    "Accept" = "*/*"
} -ContentType "application/x-www-form-urlencoded; charset=UTF-8" -Body $challengeBody -UseBasicParsing -TimeoutSec 10

$saltHex = XV $cr.Content "salt"
$iter = [int](XV $cr.Content "iterations")
$servernonce = XV $cr.Content "servernonce"
if ([string]::IsNullOrWhiteSpace($saltHex) -or [string]::IsNullOrWhiteSpace($servernonce) -or $iter -le 0) { throw "CHALLENGE_FAIL" }

$salt = HexToBytes $saltHex
$pbkdf2 = New-Object Security.Cryptography.Rfc2898DeriveBytes($Password, $salt, $iter, [Security.Cryptography.HashAlgorithmName]::SHA256)
$salted = $pbkdf2.GetBytes(32)
$pbkdf2.Dispose()

$authMessage = "$firstnonce,$servernonce,$servernonce"
$clientKey = HmacSha256 ([Text.Encoding]::UTF8.GetBytes("Client Key")) $salted
$storedKey = Sha256 $clientKey
$clientSig = HmacSha256 ([Text.Encoding]::UTF8.GetBytes($authMessage)) $storedKey
$proof = BytesToHex (XorBytes $clientKey $clientSig)

$authBody = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><request><clientproof>$proof</clientproof><finalnonce>$servernonce</finalnonce><loginflag>2</loginflag></request>"
$tok2 = [string]$cr.Headers["__RequestVerificationToken"]
$ar = Invoke-WebRequest -Uri "$BaseUrl/api/user/authentication_login" -Method Post -WebSession $s -Headers @{
    "__RequestVerificationToken" = $tok2
    "_ResponseSource" = "Broswer"
    "Accept" = "*/*"
} -ContentType "application/x-www-form-urlencoded; charset=UTF-8" -Body $authBody -UseBasicParsing -TimeoutSec 10
$code = XV $ar.Content "code"
if (-not [string]::IsNullOrWhiteSpace($code)) { throw "AUTH_FAIL_$code" }

$chunks = @(
    "/js/logcollect.js?r=1761131473596",
    "/js/apklog.js?r=1761131473596",
    "/js/developermode.js?r=1761131473596",
    "/js/debugPort.js?r=1761131473596"
)
foreach ($c in $chunks) {
    $name = ($c.Split('/')[-1]).Split('?')[0]
    $out = "_tmp_auth_$name"
    $u = "$BaseUrl$c"
    try {
        Invoke-WebRequest -Uri $u -UseBasicParsing -WebSession $s -TimeoutSec 15 -OutFile $out
        $size = (Get-Item -LiteralPath $out).Length
        Write-Output ("FETCH_OK " + $out + " SIZE=" + $size)
    } catch {
        Write-Output ("FETCH_FAIL " + $u + " MSG=" + $_.Exception.Message)
    }
}
