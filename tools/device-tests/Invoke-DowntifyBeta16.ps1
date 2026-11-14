#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$AdbPath,
    [string]$DeviceSerial,
    [string]$OutputDirectory,
    [switch]$BootstrapAdb,
    [switch]$InstallBeta,
    [switch]$LaunchApp
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $env:LOCALAPPDATA "DowntifyTests\beta16\$runId"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'beta16-artifact.json') -Raw | ConvertFrom-Json
# Fixed release identity, even if a downloaded manifest is accidentally replaced.
$expectedHash = 'ef4d215391168c92851ffdfd69466c963dd67a63318156c17774e1cff3304a99'
$package = 'com.henriquesebastiao.downtify.debug'
if ($manifest.sha256 -ne $expectedHash -or $manifest.package -ne $package -or $manifest.version_code -ne 25) {
    throw 'Unexpected artifact manifest. Do not install.'
}
if ($manifest.download_url -notmatch '^https://partage\.bardreau\.ovh/dh/[A-Za-z0-9]+/[^/]+\.apk$' -or
    $manifest.filename -ne 'downtify-ai-0.4.0-beta16-20261114.apk' -or $manifest.bytes -ne 26522591) {
    throw 'Beta16 publication manifest is incomplete or unexpected. Do not install another release.'
}
if (-not $AdbPath) {
    $found = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($found) { $AdbPath = $found.Source }
}
if (-not $AdbPath) {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
        (Join-Path $env:LOCALAPPDATA 'DowntifyTests\tools\platform-tools\adb.exe')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $AdbPath = $candidate; break }
    }
}
if (-not $AdbPath -and $BootstrapAdb) {
    $toolsRoot = Join-Path $env:LOCALAPPDATA 'DowntifyTests\tools'
    New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
    $archive = Join-Path $toolsRoot 'platform-tools-latest-windows.zip'
    $toolsUrl = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
    Write-Host "PC Windows: Invoke-WebRequest $toolsUrl -> $archive"
    Invoke-WebRequest -UseBasicParsing -Uri $toolsUrl -OutFile $archive -TimeoutSec 120
    (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash |
        Set-Content -LiteralPath (Join-Path $OutputDirectory 'platform-tools-archive-sha256.txt') -Encoding utf8
    Write-Host "PC Windows: Expand-Archive $archive -> $toolsRoot"
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsRoot -Force
    $AdbPath = Join-Path $toolsRoot 'platform-tools\adb.exe'
}
if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw 'ADB missing. Use -AdbPath with an existing adb.exe, or -BootstrapAdb for official Google platform-tools only.'
}
$script:DowntifyAdb = (Resolve-Path -LiteralPath $AdbPath).Path
$script:DowntifySerial = ''
$commandLog = Join-Path $OutputDirectory 'commands.jsonl'
function Invoke-DowntifyAdb {
    param([string[]]$Arguments, [switch]$OnDevice, [switch]$AllowFailure)
    $actual = @($Arguments)
    $display = @($Arguments)
    if ($OnDevice) {
        $actual = @('-s', $script:DowntifySerial) + $actual
        $display = @('-s', '<DEVICE>') + $display
    }
    # Serial identifiers stay out of exported command logs.
    Write-Host ('PC Windows / phone: adb ' + ($display -join ' '))
    $started = [DateTimeOffset]::UtcNow
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 can turn harmless native stderr into ErrorRecords.
        $ErrorActionPreference = 'Continue'
        $lines = @(& $script:DowntifyAdb @actual 2>&1 | ForEach-Object { "$_" })
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $paris = [TimeZoneInfo]::FindSystemTimeZoneById('Romance Standard Time')
    [ordered]@{
        utc = $started.ToString('o')
        europe_paris = [TimeZoneInfo]::ConvertTime($started, $paris).ToString('o')
        command = 'adb ' + ($display -join ' ')
        return_code = $code
    } | ConvertTo-Json -Compress | Add-Content -LiteralPath $commandLog -Encoding utf8
    if ($code -ne 0 -and -not $AllowFailure) {
        $failurePath = Join-Path $OutputDirectory 'adb-last-failure-private.txt'
        $lines | Set-Content -LiteralPath $failurePath -Encoding utf8
        throw "ADB failed (exit $code). Private diagnostic: $failurePath. No retry, uninstall or data clear."
    }
    return $lines
}
$adbVersion = @(Invoke-DowntifyAdb -Arguments @('version'))
$adbVersion | Set-Content -LiteralPath (Join-Path $OutputDirectory 'adb-version.txt') -Encoding utf8
Invoke-DowntifyAdb -Arguments @('start-server') | Out-Null
$listing = @(Invoke-DowntifyAdb -Arguments @('devices', '-l'))
$devices = @()
foreach ($line in $listing) {
    if ($line -match '^\s*(\S+)\s+(device|unauthorized|offline)\b') {
        $devices += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
    }
}
if ($DeviceSerial) {
    $selected = @($devices | Where-Object { $_.Serial -eq $DeviceSerial })
} elseif ($devices.Count -eq 1) { $selected = @($devices[0]) }
else { throw 'Zero or multiple devices. Check USB cable/debugging and authorization, or pass -DeviceSerial explicitly. No network scan or emulator.' }
if ($selected.Count -ne 1 -or $selected[0].State -ne 'device') {
    throw 'Device unavailable/unauthorized. Unlock the phone and accept its USB-debugging dialog, then rerun once.'
}
if ($selected[0].Serial.StartsWith('emulator-')) { throw 'A physical phone is required.' }
$script:DowntifySerial = $selected[0].Serial
$before = @(Invoke-DowntifyAdb -OnDevice -Arguments @('shell', 'dumpsys', 'package', $package))
$versionBefore = @($before | Select-String -Pattern '^\s*version(Code|Name)=')
$installedCode = 0
foreach ($line in $before) {
    if ($line -match '^\s*versionCode=(\d+)\b') { $installedCode = [int]$Matches[1]; break }
}
if ($InstallBeta) {
    if ($installedCode -gt 25) { throw 'A newer version is installed. Refusing downgrade.' }
    $apk = Join-Path $OutputDirectory $manifest.filename
    Write-Host ('PC Windows: download known beta16 APK, then verify SHA-256 before install.')
    Invoke-WebRequest -UseBasicParsing -Uri $manifest.download_url -OutFile $apk -TimeoutSec 120
    if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash -or
        (Get-Item -LiteralPath $apk).Length -ne $manifest.bytes) {
        throw 'APK hash/length mismatch. Do not install and do not retry another APK.'
    }
    # Android checks signature compatibility. Never add -d or uninstall on failure.
    Invoke-DowntifyAdb -OnDevice -Arguments @('install', '-r', $apk) | Out-Null
}
$after = @(Invoke-DowntifyAdb -OnDevice -Arguments @('shell', 'dumpsys', 'package', $package))
$versionAfter = @($after | Select-String -Pattern '^\s*version(Code|Name)=')
if ($InstallBeta -and (-not ($after -match '^\s*versionCode=25\b') -or
    -not ($after -match '^\s*versionName=0\.4\.0-beta16\s*$'))) {
    throw 'Installed version does not match beta16/code25.'
}
if ($LaunchApp) {
    Invoke-DowntifyAdb -OnDevice -Arguments @('shell', 'am', 'start', '-n', "$package/com.henriquesebastiao.downtify.MainActivity") | Out-Null
}
$details = [ordered]@{}
foreach ($property in @('ro.product.manufacturer', 'ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk')) {
    $details[$property] = (@(Invoke-DowntifyAdb -OnDevice -Arguments @('shell', 'getprop', $property)) -join '').Trim()
}
$memory = @(Invoke-DowntifyAdb -OnDevice -AllowFailure -Arguments @('shell', 'dumpsys', 'meminfo', $package))
$memory | Set-Content -LiteralPath (Join-Path $OutputDirectory 'memory-initial.txt') -Encoding utf8
[ordered]@{
    status = 'baseline_collected_NOT_functional_test_results'
    utc = [DateTimeOffset]::UtcNow.ToString('o')
    device = $details
    before = @($versionBefore | ForEach-Object { $_.Line.Trim() })
    after = @($versionAfter | ForEach-Object { $_.Line.Trim() })
    expected_artifact_sha256 = $expectedHash
    downloaded_apk_verified = [bool]$InstallBeta
    apk_signature_check = 'Android enforces existing signing compatibility during install -r; reference certificate is in manifest'
    launch_requested = [bool]$LaunchApp
    functional_tests = 'NOT_RUN'
    android_auto = 'NOT_RUN'
    privacy = 'Keep local. No app data backup, account export, serial, full logcat or public screenshots.'
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'baseline.json') -Encoding utf8
Write-Host "Baseline saved: $OutputDirectory"
Write-Host 'This does not mark any functional scenario PASS. Continue with the mission checklist.'
