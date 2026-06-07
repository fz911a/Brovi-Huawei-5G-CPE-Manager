param(
    [string]$Ip = "10.0.0.1",
    [int]$Port = 20249,
    [int]$DurationSeconds = 600,
    [string]$OutFile = "telnet_raw_capture_10min.txt"
)

$ErrorActionPreference = "Stop"

if (Test-Path -LiteralPath $OutFile) {
    Remove-Item -LiteralPath $OutFile -Force
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Ip, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 1000

$netWriter = New-Object System.IO.StreamWriter($stream, [System.Text.Encoding]::ASCII)
$netWriter.NewLine = "`r`n"
$netWriter.AutoFlush = $true

$file = [System.IO.File]::Open($OutFile, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
$fileWriter = New-Object System.IO.StreamWriter($file, [System.Text.Encoding]::ASCII)
$fileWriter.AutoFlush = $true

# Try to trigger modem reply/push.
$netWriter.WriteLine("AT")

$buffer = New-Object byte[] 4096
$sw = [System.Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt $DurationSeconds) {
    try {
        $n = $stream.Read($buffer, 0, $buffer.Length)
        if ($n -gt 0) {
            $text = [System.Text.Encoding]::ASCII.GetString($buffer, 0, $n)
            $fileWriter.Write($text)
        }
    }
    catch [System.IO.IOException] {
        # Read timeout is expected when there is no push in this second.
    }
}
$sw.Stop()

$fileWriter.Flush()
$fileWriter.Close()
$file.Close()
$stream.Close()
$client.Close()

$size = (Get-Item -LiteralPath $OutFile).Length
Write-Output ("CAPTURE_FILE=" + (Resolve-Path -LiteralPath $OutFile).Path)
Write-Output ("CAPTURE_SIZE=" + $size)
