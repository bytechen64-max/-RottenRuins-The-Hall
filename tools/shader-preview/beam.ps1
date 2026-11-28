# Crimson Vow finale beam (uMode=3) offline preview. Run from repo root:
#   .\tools\shader-preview\beam.ps1                                  # current shader, "new" layout (halo/body/core + flare)
#   .\tools\shader-preview\beam.ps1 -Mode old                        # reproduce the in-game look of the OLD code
#   .\tools\shader-preview\beam.ps1 -ShaderDir build\bp-candidates\beam -OutDir build\bp-candidates\beam\out
#
# Renders the finale bombardment column with the real in-game geometry
# (emitCylinder: 56 segments, world-Y in Normal.x AS 3 SIGNED BYTES - the encoding bug
# is reproduced faithfully), the three layers, tip cones and the additive blending.
param(
    [string]$ShaderDir = "src\main\resources\assets\hall\shaders\core",
    [string]$OutDir = "build\beam-preview",
    [string]$ShaderBase = "rendertype_crimson_sigil",
    [string]$Mode = "new"
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

$classes = "build\beam-preview-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
javac -encoding UTF-8 -cp $cp -d $classes tools\shader-preview\BeamPreview2.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

java -cp "$cp;$classes" BeamPreview2 $ShaderDir $OutDir $Mode $ShaderBase
if ($LASTEXITCODE -ne 0) { throw "BeamPreview2 failed" }

Write-Host "`nOutput: $OutDir" -ForegroundColor Green
