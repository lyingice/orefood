# 把各版本的反编译 MC 源码从 Gradle 缓存解压到 .mc-src/，供检索与对照阅读。
# 产物目录不进入仓库（见 .gitignore）。幂等：每次重建。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root  = Join-Path $PSScriptRoot '..\.mc-src'
$root  = [System.IO.Path]::GetFullPath($root)
$cache = Join-Path $env:USERPROFILE '.gradle\caches\neoformruntime\intermediate_results'
$mods  = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'

if (Test-Path $root) { Remove-Item $root -Recurse -Force }
New-Item -ItemType Directory -Path $root | Out-Null

function Expand-Src([string]$label, [string]$zipPath, [string]$destName) {
    if (-not (Test-Path $zipPath)) { Write-Output "SKIP $label - missing: $zipPath"; return }
    $dest = Join-Path $root $destName
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Write-Output "== $label -> $dest"
    try {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $dest)
    } catch [System.IO.IOException] {
        # 目录已存在同名条目时逐个覆盖
        $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
        foreach ($e in $zip.Entries) {
            $target = Join-Path $dest $e.FullName
            if ($e.FullName.EndsWith('/')) { New-Item -ItemType Directory -Path $target -Force | Out-Null; continue }
            New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        }
        $zip.Dispose()
    }
    $n = (Get-ChildItem $dest -Recurse -Filter *.java -ErrorAction SilentlyContinue).Count
    Write-Output "   $n java files"
}

function Expand-Jar([string]$label, [string]$jarPath, [string]$destName, [string]$filter) {
    if (-not (Test-Path $jarPath)) { Write-Output "SKIP $label - missing: $jarPath"; return }
    $dest = Join-Path $root $destName
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Write-Output "== $label -> $dest (filter: $filter)"
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    $count = 0
    foreach ($e in $zip.Entries) {
        if ($e.FullName.EndsWith('/')) { continue }
        if ($filter -and ($e.FullName -notlike $filter)) { continue }
        $target = Join-Path $dest $e.FullName
        New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        $count++
    }
    $zip.Dispose()
    Write-Output "   $count entries"
}

# --- 1.21.1：NeoForge 21.1.250 的 sourcesWithNeoForge（MC + NeoForge 同包）---
Expand-Src 'MC+NeoForge 1.21.1 (21.1.250)' `
    (Join-Path $cache 'sourcesWithNeoForge_a019eb41c4601814f13ddc2ab556fa738f431886_output.zip') '1.21.1'

# --- 26.1.2：MC 源码（vineflower 反编译 + 官方名）---
Expand-Src 'MC 26.1.2' `
    (Join-Path $cache 'transformSources_e0db7b97d8cbaa6f648b9ea526c1bde4e74c3e69_output.zip') '26.1.2'

# --- 26.1.2：NeoForge 26.1.2.109 自身源码 ---
$neo2612 = Get-ChildItem (Join-Path $mods 'net.neoforged\neoforge\26.1.2.109') -Recurse -Filter 'neoforge-26.1.2.109-sources.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($neo2612) { Expand-Jar 'NeoForge 26.1.2.109 sources' $neo2612.FullName '26.1.2' 'net/neoforged/*' }
else { Write-Output 'SKIP NeoForge 26.1.2.109 sources jar not found' }

Write-Output ''
Write-Output '=== result ==='
Get-ChildItem $root -Directory | ForEach-Object {
    $n = (Get-ChildItem $_.FullName -Recurse -Filter *.java -ErrorAction SilentlyContinue).Count
    "  $($_.Name): $n java files"
}
