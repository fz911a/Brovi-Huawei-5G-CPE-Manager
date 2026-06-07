param(
    [string]$BaseUrl = "http://10.0.0.1",
    [int]$PollCount = 20,
    [int]$PollIntervalSeconds = 2
)

$ErrorActionPreference = "Stop"

function Get-XmlValue([string]$xml, [string]$tag) {
    $m = [regex]::Match($xml, "<$tag>([^<]*)</$tag>", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
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

function Invoke-Endpoint([string]$url, [string]$method = "GET", [string]$body = "") {
    try {
        if ($method -eq "GET") {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
            return [pscustomobject]@{
                http = [int]$r.StatusCode
                body = [string]$r.Content
                err = ""
            }
        } else {
            $r = Invoke-WebRequest -Uri $url -Method Post -ContentType "application/xml" -Body $body -UseBasicParsing -TimeoutSec 10
            return [pscustomobject]@{
                http = [int]$r.StatusCode
                body = [string]$r.Content
                err = ""
            }
        }
    } catch {
        if ($_.Exception.Response -ne $null) {
            $resp = $_.Exception.Response
            $http = [int]$resp.StatusCode
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $txt = $sr.ReadToEnd()
            $sr.Close()
            return [pscustomobject]@{
                http = $http
                body = $txt
                err = ""
            }
        }
        return [pscustomobject]@{
            http = 0
            body = ""
            err = $_.Exception.Message
        }
    }
}

$outDir = Join-Path (Get-Location) "tmp_api_probe_public"
if (-not (Test-Path -LiteralPath $outDir)) {
    New-Item -Path $outDir -ItemType Directory | Out-Null
}

$statusUrl = "$BaseUrl/api/monitoring/status"
$basicUrl = "$BaseUrl/api/device/basic_information"
$convergedUrl = "$BaseUrl/api/monitoring/converged-status"

# Poll table
$rows = New-Object System.Collections.Generic.List[object]
for ($i = 1; $i -le $PollCount; $i++) {
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $statusResp = Invoke-Endpoint $statusUrl "GET"
    $basicResp = Invoke-Endpoint $basicUrl "GET"
    $convResp = Invoke-Endpoint $convergedUrl "GET"

    $statusMap = Convert-XmlToFlatMap $statusResp.body
    $basicMap = Convert-XmlToFlatMap $basicResp.body
    $convMap = Convert-XmlToFlatMap $convResp.body

    $rows.Add([pscustomobject]@{
        timestamp = $ts
        status_http = $statusResp.http
        status_code = $statusMap["code"]
        status_error = $statusResp.err
        ConnectionStatus = $statusMap["ConnectionStatus"]
        CurrentNetworkTypeEx = $statusMap["CurrentNetworkTypeEx"]
        CurrentServiceDomain = $statusMap["CurrentServiceDomain"]
        WanIPAddress = $statusMap["WanIPAddress"]
        CurrentDownloadRate = $statusMap["CurrentDownloadRate"]
        CurrentUploadRate = $statusMap["CurrentUploadRate"]
        basic_http = $basicResp.http
        basic_code = $basicMap["code"]
        basic_error = $basicResp.err
        DeviceName = $basicMap["DeviceName"]
        device_name_alt = $basicMap["devicename"]
        classify = $basicMap["classify"]
        multimode = $basicMap["multimode"]
        restore_default_status = $basicMap["restore_default_status"]
        sim_save_pin_enable = $basicMap["sim_save_pin_enable"]
        spreadname_en = $basicMap["spreadname_en"]
        spreadname_zh = $basicMap["spreadname_zh"]
        SoftwareVersion = $basicMap["SoftwareVersion"]
        WebUIVersion = $basicMap["WebUIVersion"]
        ProductFamily = $basicMap["ProductFamily"]
        SerialNumber = $basicMap["SerialNumber"]
        Imei = $basicMap["Imei"]
        Msisdn = $basicMap["Msisdn"]
        CountryCode = $basicMap["CountryCode"]
        converged_http = $convResp.http
        converged_code = $convMap["code"]
        converged_error = $convResp.err
        SimState = $convMap["SimState"]
        SimLockEnable = $convMap["SimLockEnable"]
        CurrentLanguage = $convMap["CurrentLanguage"]
        CountryCode_conv = $convMap["CountryCode"]
        CellSignalStrength = $convMap["cell_signal_strength"]
        sim_state = $convMap["sim_state"]
        ppp_status = $convMap["ppp_status"]
        wifi_user = $convMap["wifi_user"]
        converged_keys = (($convMap.Keys | Select-Object -First 12) -join "|")
    })
    if ($i -lt $PollCount) { Start-Sleep -Seconds $PollIntervalSeconds }
}

$pollCsv = Join-Path $outDir "poll_table.csv"
$rows | Export-Csv -Path $pollCsv -NoTypeInformation -Encoding UTF8

# Save last raw XML
$statusLast = Invoke-Endpoint $statusUrl "GET"
$basicLast = Invoke-Endpoint $basicUrl "GET"
$convLast = Invoke-Endpoint $convergedUrl "GET"
[System.IO.File]::WriteAllText((Join-Path $outDir "status_last.xml"), $statusLast.body, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $outDir "basic_information_last.xml"), $basicLast.body, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $outDir "converged_last.xml"), $convLast.body, [System.Text.Encoding]::UTF8)

# Developer / log channel probe
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
    $u = "$BaseUrl$ep"
    $g = Invoke-Endpoint $u "GET"
    $gMap = Convert-XmlToFlatMap $g.body
    $gCode = $gMap["code"]
    $gPreview = ($g.body -replace "\s+", " ")
    if ($gPreview.Length -gt 220) { $gPreview = $gPreview.Substring(0, 220) }
    $devRows.Add([pscustomobject]@{
        endpoint = $ep
        method = "GET"
        http = $g.http
        code = $gCode
        error = $g.err
        preview = $gPreview
    })

    foreach ($p in $payloads) {
        $r = Invoke-Endpoint $u "POST" $p
        $rMap = Convert-XmlToFlatMap $r.body
        $rCode = $rMap["code"]
        $rPreview = ($r.body -replace "\s+", " ")
        if ($rPreview.Length -gt 220) { $rPreview = $rPreview.Substring(0, 220) }
        $devRows.Add([pscustomobject]@{
            endpoint = $ep
            method = "POST"
            http = $r.http
            code = $rCode
            error = $r.err
            preview = $rPreview
        })
    }
}

$devCsv = Join-Path $outDir "developer_log_probe.csv"
$devRows | Export-Csv -Path $devCsv -NoTypeInformation -Encoding UTF8

$meta = [ordered]@{
    poll_count = $PollCount
    poll_interval_seconds = $PollIntervalSeconds
    status_url = $statusUrl
    basic_url = $basicUrl
    converged_url = $convergedUrl
    poll_csv = $pollCsv
    dev_csv = $devCsv
    out_dir = $outDir
}
$metaJson = $meta | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText((Join-Path $outDir "meta.json"), $metaJson, [System.Text.Encoding]::UTF8)

Write-Output "POLL_ROWS=$($rows.Count)"
Write-Output "DEV_ROWS=$($devRows.Count)"
Write-Output "OUT_DIR=$outDir"
