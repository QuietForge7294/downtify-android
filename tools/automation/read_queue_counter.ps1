#requires -Version 5.1
<# Offline OCR of existing captures. Input JSON is an array of absolute image paths.
   Only the top 18% is recognized; output contains numeric counters, never OCR text.
   Run once per batch, using Windows PowerShell (not pwsh). No phone commands. #>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$InputJson)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
$null = [Windows.Media.Ocr.OcrResult, Windows.Foundation, ContentType = WindowsRuntime]
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1 -and
    $_.GetGenericArguments().Count -eq 1
} | Select-Object -First 1
function Await-OcrOperation($Operation, [Type]$ResultType) {
    $task = $asTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    $task.GetAwaiter().GetResult()
}
$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if ($null -eq $engine) { throw 'Windows OCR language unavailable.' }
$paths = ConvertFrom-Json -InputObject (Get-Content -LiteralPath $InputJson -Raw -Encoding UTF8)
$results = @()
for ($index = 0; $index -lt $paths.Count; $index++) {
    $stream = $null
    $bitmap = $null
    try {
        $file = Await-OcrOperation ([Windows.Storage.StorageFile]::GetFileFromPathAsync([string]$paths[$index])) ([Windows.Storage.StorageFile])
        $stream = Await-OcrOperation ($file.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $decoder = Await-OcrOperation ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
        $scale = [Math]::Min(1.0, [Windows.Media.Ocr.OcrEngine]::MaxImageDimension / [Math]::Max($decoder.PixelWidth, $decoder.PixelHeight))
        $transform = New-Object Windows.Graphics.Imaging.BitmapTransform
        $transform.ScaledWidth = [uint32][Math]::Floor($decoder.PixelWidth * $scale)
        $transform.ScaledHeight = [uint32][Math]::Floor($decoder.PixelHeight * $scale)
        $bounds = New-Object Windows.Graphics.Imaging.BitmapBounds
        $bounds.X = 0
        $bounds.Y = 0
        $bounds.Width = $transform.ScaledWidth
        $bounds.Height = [uint32][Math]::Ceiling($transform.ScaledHeight * 0.18)
        $transform.Bounds = $bounds
        $bitmap = Await-OcrOperation ($decoder.GetSoftwareBitmapAsync(
            [Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8,
            [Windows.Graphics.Imaging.BitmapAlphaMode]::Ignore, $transform,
            [Windows.Graphics.Imaging.ExifOrientationMode]::IgnoreExifOrientation,
            [Windows.Graphics.Imaging.ColorManagementMode]::DoNotColorManage
        )) ([Windows.Graphics.Imaging.SoftwareBitmap])
        $recognized = Await-OcrOperation ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
        $counts = @($recognized.Lines | ForEach-Object {
            if ($_.Text -match '^\s*([\d\s\u202f\u00a0]+)\s+[e\u00e9]l[e\u00e9]ments?\s*$') {
                [int]($Matches[1] -replace '\s', '')
            }
        } | Select-Object -Unique)
        if ($counts.Count -eq 1) {
            $results += [ordered]@{ image_index = $index; status = 'observed_ocr'; internal_queue_count = $counts[0] }
        } else {
            $results += [ordered]@{ image_index = $index; status = 'NEEDS_VISUAL'; matches = $counts.Count }
        }
    } catch {
        Write-Verbose $_.Exception.ToString()
        $results += [ordered]@{ image_index = $index; status = 'OCR_ERROR'; error_type = $_.Exception.GetType().Name }
    } finally {
        if ($null -ne $bitmap) { $bitmap.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
}
ConvertTo-Json -InputObject $results -Compress -Depth 4
