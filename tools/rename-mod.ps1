<#
.SYNOPSIS
    把 MultiLoader Mod Template 改名为你自己的模组。

.DESCRIPTION
    就地修改当前目录树里的所有标识：mod id、Java 包名、类名前缀、显示名、资源命名空间。

    会改动：.java .gradle .properties .json .toml .mcmeta .md .txt 等文本文件的内容，
    重建 Java 包目录，并重命名文件名 / 目录名里含模板标识的部分。

    会刻意保护：
      * src/main/templates/  —— ModDevGradle 的元数据模板目录，名字里含 "template"
                                 但绝不能被改（改了 generateModMetadata 就找不到源目录）；
      * SimpleTemplateEngine —— README 里的术语；
      * gradle-wrapper.jar、gradlew、gradlew.bat、build/、.gradle/、.git/。

.PARAMETER NewModId
    新的模组 id（小写字母、数字、下划线，且以字母开头）。

.PARAMETER NewPackageRoot
    新的 Java 主包名，例如 com.yourname.yourmod。
    包目录树会自动重建（com/lyingice/template -> com/yourname/yourmod）。

.PARAMETER NewClassPrefix
    新的类名前缀，例如 MyMod（TemplateForge -> MyModForge）。
    省略时由 NewModId 推导（my_mod -> MyMod）。

.PARAMETER NewDisplayName
    模组的显示名，例如 "My Mod"。省略时使用 NewClassPrefix。

.EXAMPLE
    .\tools\rename-mod.ps1 -NewModId my_mod -NewPackageRoot com.yourname.mymod -NewDisplayName "My Mod"

.NOTES
    运行前请先提交到 git，或备份整个目录。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$NewModId,
    [Parameter(Mandatory = $true)][string]$NewPackageRoot,
    [string]$NewClassPrefix,
    [string]$NewDisplayName
)

$ErrorActionPreference = 'Stop'

# ---- 模板里当前的标识 ----
$OldModId       = 'template'
$OldPackageRoot = 'com.lyingice.template'
$OldClassPrefix = 'Template'

# ---- 参数校验与推导 ----
if ($NewModId -notmatch '^[a-z][a-z0-9_]*$') {
    throw "NewModId '$NewModId' 不合法：必须匹配 [a-z][a-z0-9_]*（加载器要求）。"
}
if ($NewPackageRoot -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$') {
    throw "NewPackageRoot '$NewPackageRoot' 不合法：应当形如 com.yourname.yourmod。"
}
if (-not $NewClassPrefix) {
    $NewClassPrefix = ($NewModId -split '[_\-]' |
        Where-Object { $_ } |
        ForEach-Object { $_.Substring(0, 1).ToUpper() + $_.Substring(1) }) -join ''
}
if (-not $NewDisplayName) { $NewDisplayName = $NewClassPrefix }

$root = (Get-Location).Path
if (-not (Test-Path (Join-Path $root 'settings.gradle'))) {
    throw "当前目录不是模板根目录（找不到 settings.gradle）。请 cd 到模板根目录再运行。"
}

Write-Host ""
Write-Host "模板改名" -ForegroundColor Cyan
Write-Host "  根目录   : $root"
Write-Host "  mod id   : $OldModId -> $NewModId"
Write-Host "  包名     : $OldPackageRoot -> $NewPackageRoot"
Write-Host "  类名前缀 : $OldClassPrefix -> $NewClassPrefix"
Write-Host "  显示名   : $OldClassPrefix -> $NewDisplayName"
Write-Host ""

# 注意：'.mc-src' 必须排除。那是反编译出来的 Minecraft 源码（两万多个文件），
# 里面遍布 StructureTemplate / TemplateFeature 之类的标识，一旦被本脚本改写就直接毁掉。
$skipDirs  = @('.git', '.gradle', 'build', 'run', 'run-data', 'runs', 'out', '.idea', 'repo', 'logs', '.mc-src')
$skipFiles = @('rename-mod.ps1', 'gradlew', 'gradlew.bat', 'gradle-wrapper.jar', 'gradle-wrapper.properties')
$textExt   = @('.java', '.gradle', '.properties', '.json', '.toml', '.mcmeta', '.md', '.txt', '.xml',
               '.yml', '.yaml', '.cfg', '.bat', '.sh', '.ps1', '.gitignore', '.editorconfig')

$sep = [IO.Path]::DirectorySeparatorChar
$oldPkgRel = $OldPackageRoot.Replace('.', $sep)
$newPkgRel = $NewPackageRoot.Replace('.', $sep)
$oldPkgSlash = $OldPackageRoot.Replace('.', '/')
$newPkgSlash = $NewPackageRoot.Replace('.', '/')

# 替换顺序：长串优先。包名的点分与斜杠两种写法都要处理，
# 否则 README 目录树里的 com/lyingice/template 会被 modId 规则打成 com/lyingice/mymod。
# 注意：这里刻意不用哈希表。PowerShell 的哈希表键是**大小写不敏感**的，
# 而本脚本必须区分 "Template"（类名前缀）与 "template"（mod id）——把两者塞进
# 同一个 @{} 会直接抛 "Duplicate keys 'template'" 并在写文件之前中止。
# 因此改用有序的键值对数组，替换顺序依旧是「长串优先」。
$pairs = @(
    [pscustomobject]@{ Old = $OldPackageRoot; New = $NewPackageRoot }   # com.lyingice.template
    [pscustomobject]@{ Old = $oldPkgSlash;    New = $newPkgSlash }      # com/lyingice/template（文档里的路径写法）
    [pscustomobject]@{ Old = $OldClassPrefix; New = $NewClassPrefix }   # Template（类名前缀、显示名）
    [pscustomobject]@{ Old = $OldModId;       New = $NewModId }         # template（mod id、资源命名空间）
)

# 含模板标识但绝不能动的词，先用哨兵顶替，收尾还原。
$protect = [ordered]@{
    'templates'            = '@@TPL_DIR@@'
    'SimpleTemplateEngine' = '@@TPL_ENGINE@@'
}

$files = @(Get-ChildItem -LiteralPath $root -Recurse -File -Force | Where-Object {
    $rel = $_.FullName.Substring($root.Length).TrimStart($sep)
    $parts = $rel -split '[\\/]'
    -not ($parts[0..([Math]::Max(0, $parts.Count - 2))] | Where-Object { $skipDirs -contains $_ })
})

$contentChanged = 0
$fileRenamed = 0
$dirRenamed = 0

# ---- 1) 替换文件内容 ----
foreach ($f in $files) {
    if ($skipFiles -contains $f.Name) { continue }
    if ($f.Extension -ne '' -and $textExt -notcontains $f.Extension) { continue }

    $updated = Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8
    if ($null -eq $updated) { continue }
    $original = $updated

    foreach ($k in $protect.Keys) { $updated = $updated.Replace($k, $protect[$k]) }
    foreach ($pair in $pairs) { $updated = $updated.Replace($pair.Old, $pair.New) }
    foreach ($k in $protect.Keys) { $updated = $updated.Replace($protect[$k], $k) }

    if ($updated -ne $original) {
        Set-Content -LiteralPath $f.FullName -Value $updated -NoNewline -Encoding UTF8
        $contentChanged++
    }
}

# ---- 2) 重命名文件：只认 Template 前缀（Ordinal 比较，不会碰 templates）----
foreach ($f in ($files | Sort-Object { $_.FullName.Length } -Descending)) {
    if (-not $f.Name.StartsWith($OldClassPrefix, [StringComparison]::Ordinal)) { continue }
    Rename-Item -LiteralPath $f.FullName -NewName $f.Name.Replace($OldClassPrefix, $NewClassPrefix)
    $fileRenamed++
}

# ---- 3) 重建 Java 包目录：整条包路径一起搬（com/lyingice/template -> 新包路径）----
$pkgDirs = @(Get-ChildItem -LiteralPath $root -Recurse -Directory -Force | Where-Object {
    $_.FullName.Substring($root.Length).TrimStart($sep).EndsWith($oldPkgRel, [StringComparison]::Ordinal)
} | Sort-Object { $_.FullName.Length } -Descending)

foreach ($pd in $pkgDirs) {
    $rel = $pd.FullName.Substring($root.Length).TrimStart($sep)
    $prefix = $rel.Substring(0, $rel.Length - $oldPkgRel.Length)
    $target = Join-Path $root ($prefix + $newPkgRel)

    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Move-Item -LiteralPath $pd.FullName -Destination $target -Force
    $dirRenamed++

    # 顺带清掉搬空后遗留的空目录（如旧的 lyingice），避免留下空壳包。
    $p = Split-Path $pd.FullName -Parent
    while ($p.Length -gt $root.Length) {
        if ((Get-ChildItem -LiteralPath $p -Force | Measure-Object).Count -gt 0) { break }
        Remove-Item -LiteralPath $p -Force
        $p = Split-Path $p -Parent
    }
}

# ---- 4) 重命名资源目录（assets/template -> assets/<NewModId>）----
$resDirs = @(Get-ChildItem -LiteralPath $root -Recurse -Directory -Force | Where-Object {
    $_.Name -ceq $OldModId
} | Sort-Object { $_.FullName.Length } -Descending)

foreach ($d in $resDirs) {
    Rename-Item -LiteralPath $d.FullName -NewName $NewModId
    $dirRenamed++
}

Write-Host "完成：" -ForegroundColor Green
Write-Host "  内容替换 $contentChanged 个文件"
Write-Host "  重命名   $fileRenamed 个文件"
Write-Host "  重建     $dirRenamed 个目录"
Write-Host ""
Write-Host "后续手动确认：" -ForegroundColor Yellow
Write-Host "  1. 各模块 gradle.properties 里的 mod_name / mod_version / mod_authors / mod_license"
Write-Host "  2. 仓库根 settings.gradle 的 rootProject.name"
Write-Host "  3. LICENSE 与各元数据里的许可声明"
Write-Host "  4. 示例物品 example_item 的模型 / 贴图 / 语言键（想留作范例可不动）"
Write-Host "  5. 跑一次 .\gradlew build，确认 mod_id 在三处完全一致"
