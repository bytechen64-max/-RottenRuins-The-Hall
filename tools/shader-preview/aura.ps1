# AuraLab 的驱动脚本：编译 tools/shader-preview/*.java 并跑出三张对比图。
# 注意：本文件必须保持 UTF-8 带 BOM（Windows PowerShell 5.1 会按 GBK 读没有 BOM 的文件）。
param(
    [string]$ShaderDir = "src\main\resources\assets\hall\shaders\core",
    [string]$LabDir = "tools\shader-preview\lab",
    [string]$OutDir = "build\aura-lab",
    [float]$Time = 5.0
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.lwjgl"
if (-not (Test-Path $cache)) { throw "找不到 LWJGL：$cache（先跑一次 .\gradlew.bat build）" }
$jars = @()
foreach ($a in 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl') {
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1.jar" | Select-Object -First 1 -ExpandProperty FullName
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1-natives-windows.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$cp = ($jars | Where-Object { $_ }) -join ';'
if (-not $cp) { throw "Gradle 缓存里没有 LWJGL 的 jar" }

$classes = "build\shader-preview-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null

javac -encoding UTF-8 -cp $cp -d $classes tools\shader-preview\AuraLab.java
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

java -cp "$cp;$classes" AuraLab $ShaderDir $LabDir $OutDir $Time
if ($LASTEXITCODE -ne 0) { throw "AuraLab 失败" }
Write-Host "`n导出：$OutDir\baseline.png / route2.png / route3.png" -ForegroundColor Green
