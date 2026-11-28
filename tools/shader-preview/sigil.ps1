# Crimson Vow sigil offline preview. Run from repo root:
#   .\tools\shader-preview\sigil.ps1
#   .\tools\shader-preview\sigil.ps1 -ShaderDir build\bp-candidates\sigil -OutDir build\bp-candidates\sigil\out
#
# Renders rendertype_crimson_sigil.fsh uMode=0 (charge sigil) and uMode=1 (field sigil)
# with the real in-game disc geometry (6 rings x 96 segments, POSITION_COLOR_NORMAL),
# camera views (top / inside / outside) and additive blending. No Minecraft launch.
param(
    [string]$ShaderDir = "src\main\resources\assets\hall\shaders\core",
    [string]$OutDir = "build\sigil-preview",
    [string]$ShaderBase = "rendertype_crimson_sigil"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.lwjgl"
if (-not (Test-Path $cache)) { throw "LWJGL not found: $cache" }
$jars = @()
foreach ($a in 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl') {
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1-natives-windows.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$cp = ($jars | Where-Object { $_ }) -join ';'
if (-not $cp) { throw "LWJGL jars not resolved" }

$classes = "build\sigil-preview-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
javac -encoding UTF-8 -cp $cp -d $classes tools\shader-preview\SigilPreview.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

java -cp "$cp;$classes" SigilPreview $ShaderDir $OutDir $ShaderBase
if ($LASTEXITCODE -ne 0) { throw "SigilPreview failed" }

Write-Host "`nOutput: $OutDir" -ForegroundColor Green
