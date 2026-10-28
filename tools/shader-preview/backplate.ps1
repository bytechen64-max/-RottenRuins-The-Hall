# Crimson Vow backplate offline preview. Run from repo root:
#   .\tools\shader-preview\backplate.ps1
#   .\tools\shader-preview\backplate.ps1 -ShaderDir build\bp-candidates\eclipse
#   .\tools\shader-preview\backplate.ps1 -ShaderDir <dir> -ShaderBase rendertype_crimson_backplate
#
# Opens a hidden GL 3.2 core context and renders the backplate shader with the real
# in-game geometry (48-segment fan billboard, two passes: alpha base + additive glow),
# then composites a third-person player silhouette and writes PNGs.
# No Minecraft launch needed - takes a few seconds.
param(
    [string]$ShaderDir = "src\main\resources\assets\hall\shaders\core",
    [string]$OutDir = "build\backplate-preview",
    [string]$ShaderBase = "rendertype_crimson_backplate",
    # config crimsonBackplateScale (visible radius, blocks). Compare 1.3 vs 1.45.
    [double]$Scale = 1.3,
    # Rig.SHADER_VISIBLE_RADIUS (outermost UV radius the shader draws to).
    [double]$VisibleR = 0.86
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

# Gather LWJGL classpath from the Gradle cache (same set as preview.ps1).
$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.lwjgl"
if (-not (Test-Path $cache)) { throw "LWJGL not found: $cache (run .\gradlew.bat build once)" }
$jars = @()
foreach ($a in 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl') {
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1-natives-windows.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$cp = ($jars | Where-Object { $_ }) -join ';'
if (-not $cp) { throw "LWJGL jars not resolved" }

$classes = "build\bp-preview-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
# javac defaults to GBK on this machine; sources are UTF-8.
javac -encoding UTF-8 -cp $cp -d $classes tools\shader-preview\BackplatePreview.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

java -cp "$cp;$classes" BackplatePreview $ShaderDir $OutDir $ShaderBase $Scale $VisibleR
if ($LASTEXITCODE -ne 0) { throw "BackplatePreview failed" }

Write-Host "`nOutput: $OutDir" -ForegroundColor Green
