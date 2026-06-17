param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$apkDir = Join-Path $RepoRoot 'apk'
$metadataDir = Join-Path $RepoRoot 'metadata'
$indexPath = Join-Path $RepoRoot 'index.min.json'
$aaptCandidates = @(
    (Join-Path $env:ANDROID_HOME 'build-tools\37.0.0\aapt.exe'),
    (Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\aapt.exe'),
    'aapt.exe'
) | Where-Object { $_ -and (Get-Command $_ -ErrorAction SilentlyContinue) }

if (-not $aaptCandidates) {
    throw 'aapt.exe not found. Set ANDROID_HOME to an Android SDK path that contains build-tools.'
}

$aapt = $aaptCandidates[0]

function Get-MatchValue {
    param(
        [string] $Text,
        [string] $Pattern
    )

    $match = [regex]::Match($Text, $Pattern)
    if ($match.Success) {
        return $match.Groups[1].Value
    }

    return ''
}

function Get-ManifestMetadataValue {
    param(
        [string[]] $Lines,
        [string] $Name
    )

    $escaped = [regex]::Escape($Name)
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        if ($Lines[$i] -notmatch "A: android:name\(0x[0-9a-fA-F]+\)=`"$escaped`"") {
            continue
        }

        for ($j = $i + 1; $j -lt [Math]::Min($i + 5, $Lines.Count); $j++) {
            $quoted = [regex]::Match($Lines[$j], 'A: android:value\(0x[0-9a-fA-F]+\)="([^"]*)"')
            if ($quoted.Success) {
                return $quoted.Groups[1].Value
            }

            $typed = [regex]::Match($Lines[$j], 'A: android:value\(0x[0-9a-fA-F]+\)=\(type 0x[0-9a-fA-F]+\)0x([0-9a-fA-F]+)')
            if ($typed.Success) {
                return [Convert]::ToInt32($typed.Groups[1].Value, 16).ToString()
            }
        }
    }

    return ''
}

function Convert-Source {
    param($Source)

    [ordered]@{
        name = "$($Source.name)"
        lang = "$($Source.lang)"
        id = "$($Source.id)"
        baseUrl = "$($Source.baseUrl)"
    }
}

function Get-MetadataMap {
    param([string] $Path)

    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $map
    }

    Get-ChildItem -LiteralPath $Path -Filter '*.json' -File | ForEach-Object {
        $json = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($item in @($json)) {
            if ($item.pkg) {
                $map["$($item.pkg)"] = $item
            }
        }
    }

    return $map
}

$metadataByPackage = Get-MetadataMap $metadataDir

$entries = @(Get-ChildItem -LiteralPath $apkDir -Filter '*.apk' -File | Sort-Object Name | ForEach-Object {
    $apk = $_
    $badging = & $aapt dump badging $apk.FullName 2>$null
    $xmltree = & $aapt dump xmltree $apk.FullName AndroidManifest.xml 2>$null

    $packageName = Get-MatchValue ($badging -join "`n") "package: name='([^']+)'"
    $versionName = Get-MatchValue ($badging -join "`n") "versionName='([^']+)'"
    $versionCode = Get-MatchValue ($badging -join "`n") "versionCode='([^']+)'"
    $label = Get-MatchValue ($badging -join "`n") "application-label:'([^']+)'"

    if (-not $packageName) {
        throw "Could not parse package name from $($apk.Name)"
    }

    $metadata = $metadataByPackage[$packageName]
    $manifestNsfw = Get-ManifestMetadataValue $xmltree 'tachiyomi.extension.nsfw'
    [array] $sources = if ($metadata -and $metadata.sources) {
        @($metadata.sources) | ForEach-Object { Convert-Source $_ }
    } else {
        @()
    }

    $lang = if ($metadata -and $metadata.lang) {
        "$($metadata.lang)"
    } elseif ($sources.Count -gt 0) {
        "$($sources[0].lang)"
    } else {
        'all'
    }

    $name = if ($metadata -and $metadata.name) {
        "$($metadata.name)"
    } elseif ($label) {
        $label
    } else {
        "Tachiyomi: $packageName"
    }

    $nsfw = if ($metadata -and $null -ne $metadata.nsfw) {
        [int] $metadata.nsfw
    } elseif ($manifestNsfw -eq '1' -or $manifestNsfw -eq 'true') {
        1
    } else {
        0
    }

    [ordered]@{
        name = $name
        pkg = $packageName
        apk = "$($apk.Name)"
        lang = $lang
        code = if ($versionCode) { [int] $versionCode } else { 0 }
        version = $versionName
        nsfw = $nsfw
        sources = @($sources)
    }
})

$json = ConvertTo-Json -InputObject @($entries) -Depth 20 -Compress
if (-not $json) {
    $json = '[]'
}

[System.IO.File]::WriteAllText($indexPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Output "Generated $indexPath with $($entries.Count) entries."
