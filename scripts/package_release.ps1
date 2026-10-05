[CmdletBinding()]
param(
    [string]$Version = '0.1.5-beta10.1-public',
    [switch]$IncludeDevelopmentBuilds
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$packageProfile = if ($IncludeDevelopmentBuilds) { 'all' } else { 'public' }
$DistDir = Join-Path $ProjectRoot "dist\$packageProfile-v$Version"

if ($Version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$') {
    throw "Version must use x.y.z or x.y.z-prerelease format: $Version"
}

$gradleFile = Join-Path $ProjectRoot 'app\build.gradle.kts'
$versionLine = Select-String -LiteralPath $gradleFile -Pattern 'versionName\s*=\s*"([^"]+)"' |
    Select-Object -First 1
if (-not $versionLine -or $versionLine.Matches[0].Groups[1].Value -ne $Version) {
    throw "Requested version $Version does not match app/build.gradle.kts"
}

$publicFile = if ($Version.EndsWith('-public')) { "ZhishengWeather-v$Version.apk" } else { "ZhishengWeather-v$Version-public.apk" }
$packages = [ordered]@{
    'app\build\outputs\apk\publicRelease\app-publicRelease.apk' = $publicFile
}

if ($IncludeDevelopmentBuilds) {
    $packages['app\build\outputs\apk\release\app-release.apk'] = "ZhishengWeather-v$Version-full-private.apk"
    $packages['app\build\outputs\apk\performance\app-performance.apk'] = "ZhishengWeather-v$Version-owner-upgrade-private.apk"
    $packages['app\build\outputs\apk\previewPublic\app-previewPublic.apk'] = "ZhishengWeather-v$Version-public-parallel.apk"
}

foreach ($relativeSource in $packages.Keys) {
    $source = Join-Path $ProjectRoot $relativeSource
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Missing APK. Build all release variants first: $source"
    }
}

New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
foreach ($relativeSource in $packages.Keys) {
    Copy-Item -LiteralPath (Join-Path $ProjectRoot $relativeSource) `
        -Destination (Join-Path $DistDir $packages[$relativeSource]) -Force
}

$hashLines = @("ZhishengWeather $Version", '')
$hashes = @{}
foreach ($fileName in ($packages.Values | Sort-Object)) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $DistDir $fileName) -Algorithm SHA256).Hash
    $hashes[$fileName] = $hash
    $hashLines += "$hash  $fileName"
}
$hashLines | Set-Content -LiteralPath (Join-Path $DistDir 'SHA256.txt') -Encoding UTF8

if (-not $IncludeDevelopmentBuilds) {
    $manifestPath = Join-Path $ProjectRoot 'update.json'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.versionName -ne $Version) {
        throw "update.json versionName does not match $Version"
    }
    $versionCodeLine = Select-String -LiteralPath $gradleFile -Pattern 'versionCode\s*=\s*(\d+)' |
        Select-Object -First 1
    if (-not $versionCodeLine) {
        throw 'Cannot read versionCode from app/build.gradle.kts'
    }
    $expectedVersionCode = [long]$versionCodeLine.Matches[0].Groups[1].Value
    if (-not $manifest.versionCode -or [long]$manifest.versionCode -ne $expectedVersionCode) {
        throw "update.json versionCode does not match app/build.gradle.kts ($expectedVersionCode)"
    }
    if (-not $manifest.apkUrl.EndsWith("/$publicFile")) {
        throw "update.json apkUrl does not point to $publicFile"
    }
    if (-not $manifest.sha256.Equals($hashes[$publicFile], [StringComparison]::OrdinalIgnoreCase)) {
        throw 'update.json sha256 does not match the packaged public APK'
    }
}

Write-Host "Packaged ZhishengWeather $Version to $DistDir"
$hashLines | ForEach-Object { Write-Host $_ }
