param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string] $RawBaseUrl = 'https://raw.githubusercontent.com/gudada1/nhentai-mihon-extension-repo/main'
)

$ErrorActionPreference = 'Stop'

$apkDir = Join-Path $RepoRoot 'apk'
$metadataDir = Join-Path $RepoRoot 'metadata'
$repoPath = Join-Path $RepoRoot 'repo.json'
$indexPath = Join-Path $RepoRoot 'index.min.json'
$indexV2Path = Join-Path $RepoRoot 'index-v2.min.json'
$cacheBustedIndexDirNames = @('v2', 'v3')
$latestCacheBustedIndexDirName = $cacheBustedIndexDirNames[-1]
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

function Copy-SourceWithLanguage {
    param(
        $Source,
        [string] $Language
    )

    [ordered]@{
        name = "$($Source.name)"
        lang = $Language
        id = "$($Source.id)"
        baseUrl = "$($Source.baseUrl)"
    }
}

function Normalize-SourceList {
    param($Sources)

    $normalized = @($Sources)
    if ($normalized.Count -eq 0) {
        return $normalized
    }

    if (-not ($normalized | Where-Object { $_.lang -eq 'all' } | Select-Object -First 1)) {
        $fallback = ($normalized | Where-Object { $_.lang -eq 'zh' } | Select-Object -First 1)
        if (-not $fallback) {
            $fallback = $normalized[0]
        }
        $normalized = @((Copy-SourceWithLanguage $fallback 'all')) + $normalized
    }

    if (-not ($normalized | Where-Object { $_.lang -eq 'zh' } | Select-Object -First 1)) {
        $zhFallback = ($normalized | Where-Object { "$($_.lang)".StartsWith('zh') } | Select-Object -First 1)
        if ($zhFallback) {
            $normalized = @((Copy-SourceWithLanguage $zhFallback 'zh')) + $normalized
        }
    }

    return $normalized
}

function Convert-V2Source {
    param(
        $Source,
        [int] $Nsfw
    )

    [ordered]@{
        id = [long] "$($Source.id)"
        name = "$($Source.name)"
        language = "$($Source.lang)"
        homeUrl = "$($Source.baseUrl)"
        mirrorUrls = @()
        contentRating = if ($Nsfw -eq 1) { 'PORNOGRAPHIC' } else { 'SAFE' }
    }
}

function Get-ExtensionLibVersion {
    param([string] $VersionName)

    $lastDot = $VersionName.LastIndexOf('.')
    if ($lastDot -gt 0) {
        return $VersionName.Substring(0, $lastDot)
    }

    return $VersionName
}

function Convert-V2Extension {
    param(
        $Entry,
        [string] $RawBaseUrl
    )

    $extensionName = "$($Entry.name)"
    if ($extensionName.StartsWith('Tachiyomi: ')) {
        $extensionName = $extensionName.Substring('Tachiyomi: '.Length)
    }

    $sources = if ($Entry.sources -and @($Entry.sources).Count -gt 0) {
        @($Entry.sources) | ForEach-Object { Convert-V2Source $_ ([int] $Entry.nsfw) }
    } else {
        @(
            [ordered]@{
                id = 0
                name = $extensionName
                language = "$($Entry.lang)"
                homeUrl = ''
                mirrorUrls = @()
                contentRating = if ([int] $Entry.nsfw -eq 1) { 'PORNOGRAPHIC' } else { 'SAFE' }
            }
        )
    }

    [ordered]@{
        name = $extensionName
        packageName = "$($Entry.pkg)"
        resources = [ordered]@{
            apkUrl = "$RawBaseUrl/apk/$($Entry.apk)"
            iconUrl = "$RawBaseUrl/icon/$($Entry.pkg).png"
        }
        extensionLib = Get-ExtensionLibVersion "$($Entry.version)"
        versionCode = [long] $Entry.code
        versionName = "$($Entry.version)"
        sources = @($sources)
    }
}

function New-RepoMetadata {
    param(
        $Repo,
        [string] $IndexV2Url
    )

    [ordered]@{
        index_v2 = $IndexV2Url
        meta = [ordered]@{
            name = "$($Repo.meta.name)"
            shortName = "$($Repo.meta.shortName)"
            website = "$($Repo.meta.website)"
            signingKeyFingerprint = "$($Repo.meta.signingKeyFingerprint)"
        }
    }
}

function New-V2Store {
    param(
        $Repo,
        $Entries,
        [string] $RawBaseUrl
    )

    [ordered]@{
        name = "$($Repo.meta.name)"
        badgeLabel = if ($Repo.meta.shortName) { "$($Repo.meta.shortName)" } else { "$($Repo.meta.name)" }
        signingKey = "$($Repo.meta.signingKeyFingerprint)"
        contact = [ordered]@{
            website = "$($Repo.meta.website)"
            discord = $null
        }
        extensions = @($Entries | ForEach-Object { Convert-V2Extension $_ $RawBaseUrl })
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
    $sources = @(Normalize-SourceList $sources)

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

$repo = Get-Content -LiteralPath $repoPath -Raw -Encoding UTF8 | ConvertFrom-Json
$indexV2Url = "$RawBaseUrl/$latestCacheBustedIndexDirName/index.min.json"
$repoMetadataJson = ConvertTo-Json -InputObject (New-RepoMetadata $repo $indexV2Url) -Depth 20 -Compress
$v2Json = ConvertTo-Json -InputObject (New-V2Store $repo @($entries) $RawBaseUrl) -Depth 30 -Compress

[System.IO.File]::WriteAllText($indexPath, $json, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($repoPath, $repoMetadataJson, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($indexV2Path, $v2Json, [System.Text.UTF8Encoding]::new($false))
foreach ($cacheBustedIndexDirName in $cacheBustedIndexDirNames) {
    $cacheBustedIndexDir = Join-Path $RepoRoot $cacheBustedIndexDirName
    $cacheBustedIndexPath = Join-Path $cacheBustedIndexDir 'index.min.json'
    $cacheBustedRepoPath = Join-Path $cacheBustedIndexDir 'repo.json'
    New-Item -ItemType Directory -Force -Path $cacheBustedIndexDir | Out-Null
    [System.IO.File]::WriteAllText($cacheBustedIndexPath, $v2Json, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($cacheBustedRepoPath, $repoMetadataJson, [System.Text.UTF8Encoding]::new($false))
    Write-Output "Generated $cacheBustedIndexPath with $($entries.Count) v2 entries."
    Write-Output "Generated $cacheBustedRepoPath."
}
Write-Output "Generated $indexPath with $($entries.Count) entries."
Write-Output "Generated $repoPath."
Write-Output "Generated $indexV2Path with $($entries.Count) v2 entries."
