# ForgeGradle 只往缓存里放「已映射的 class」jar，不落源码；这里用与 NeoForm 同一套
# vineflower 参数把它反编译成带官方名的 .java，供检索与三版本对照。
#
# 注意 vineflower 的调用约定：`java -jar vineflower.jar --<opt>=<val>... <source>... <destination>`
# 输出目录是「位置参数」，而 `-e=` 是"添加外部库"，不是输出。
$ErrorActionPreference = 'Stop'

$vineflower = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.vineflower\vineflower\1.11.2\f02c1f554d5bd9cc0fd74d4977efba364d85e739\vineflower-1.11.2.jar'
$inJar      = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.5_mapped_official_1.20.1\forge-1.20.1-47.4.5_mapped_official_1.20.1.jar'
$outDir     = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.mc-src\1.20.1'))

if (-not (Test-Path $vineflower)) { throw "vineflower jar missing: $vineflower" }
if (-not (Test-Path $inJar))      { throw "forge mapped jar missing: $inJar" }
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

Write-Output "decompiling $([int]((Get-Item $inJar).Length/1MB))MB jar -> $outDir"
& java -Xmx5G -jar $vineflower `
    --decompile-inner --remove-bridge --decompile-generics --ascii-strings `
    --remove-synthetic --ignore-invalid-bytecode --decompile-assert `
    --indent-string='    ' --log-level=WARN `
    $inJar $outDir

$n = (Get-ChildItem $outDir -Recurse -Filter *.java -ErrorAction SilentlyContinue).Count
Write-Output "done: $n java files"
