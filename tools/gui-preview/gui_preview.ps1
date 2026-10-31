# GUI offline preview harness. Run from repo root (or anywhere):
#   .\tools\gui-preview\gui_preview.ps1                       # smoke scene
#   .\tools\gui-preview\gui_preview.ps1 -Scene tooltip        # thermal tooltip scene
#   .\tools\gui-preview\gui_preview.ps1 -All                  # every scene in one go
#   .\tools\gui-preview\gui_preview.ps1 -Scene tooltip -Time 4.5 -OutDir build\gui-preview\out
#
# What it does: opens a hidden GL 3.2 core context, builds a GUI canvas at the game's
# GUI-scaled resolution (default 640x400 = a 1280x800 window at GUI scale 2), renders the
# requested scene with the project's real core shaders / textures / MC bitmap font, and
# exports PNGs with nearest-neighbour upscaling (default x2 -> 1280x800). No Minecraft
# launch, no Minecraft classes on the classpath.
param(
    [string]$Scene = "smoke",
    [string]$OutDir = "build\gui-preview",
    [float]$Time = 0.0,
    [int]$Width = 640,
    [int]$Height = 400,
    [int]$Scale = 2,
    [switch]$All
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

# ---- LWJGL 3.3.1 from the Gradle cache (same source as tools/shader-preview) ----
$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.lwjgl"
if (-not (Test-Path $cache)) {
    throw "LWJGL not found: $cache (run .\gradlew.bat build once)"
}
$jars = @()
foreach ($a in 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl') {
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1-natives-windows.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$cp = ($jars | Where-Object { $_ }) -join ';'
if (-not $cp) {
    throw "LWJGL 3.3.1 jars not resolved from the Gradle cache"
}

# ---- vanilla bitmap font (extracted from the MC client jar into build/, never committed) ----
$mcAssets = "build\gui-preview\mc-assets"
$asciiFont = Join-Path $mcAssets "assets\minecraft\textures\font\ascii.png"
if (-not (Test-Path $asciiFont)) {
    $mcVersion = "1.20.1"
    $prop = Select-String -Path "gradle.properties" -Pattern '^\s*minecraft_version\s*=\s*(.+)$' |
        Select-Object -First 1
    if ($prop) {
        $mcVersion = $prop.Matches[0].Groups[1].Value.Trim()
    }
    $clientJar = $null
    $candidates = @(
        (Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle\minecraft_repo\versions\$mcVersion\client.jar"),
        (Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle\minecraft_user_repo\mcp\$mcVersion-*\joined\downloadClient\client.jar")
    )
    foreach ($candidate in $candidates) {
        $hit = Get-Item $candidate -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) {
            $clientJar = $hit.FullName
            break
        }
    }
    if (-not $clientJar) {
        $hit = Get-ChildItem (Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle") -Recurse -Filter "client.jar" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($hit) {
            $clientJar = $hit.FullName
        }
    }
    if ($clientJar) {
        New-Item -ItemType Directory -Force $mcAssets | Out-Null
        Push-Location $mcAssets
        try {
            jar xf $clientJar "assets/minecraft/textures/font/ascii.png" "assets/minecraft/font/include/default.json" "assets/minecraft/font/include/space.json"
        } finally {
            Pop-Location
        }
        Write-Host "MC font assets: $clientJar -> $mcAssets" -ForegroundColor DarkGray
    } else {
        Write-Warning "MC client.jar not found; text will fall back to placeholder blocks"
    }
}

$baseSources = @(
    'tools\gui-preview\MiniJson.java',
    'tools\gui-preview\PreviewGl.java',
    'tools\gui-preview\PreviewTexture.java',
    'tools\gui-preview\PreviewShader.java',
    'tools\gui-preview\PreviewFont.java',
    'tools\gui-preview\PreviewCanvas.java',
    'tools\gui-preview\SdfShapes.java',
    'tools\gui-preview\WheelKit.java',
    'tools\gui-preview\GuiPreview.java'
)

function Get-SceneClass([string]$name) {
    $parts = $name -split '[-_]'
    $out = ""
    foreach ($p in $parts) {
        if ($p.Length -gt 0) {
            $out += $p.Substring(0, 1).ToUpper() + $p.Substring(1)
        }
    }
    return $out
}

if ($All) {
    $scenes = @('smoke', 'tooltip', 'collapsar_bar', 'gui_effects', 'tab_divider', 'difficulty',
                'spell_wheel', 'spell_loadout')
} else {
    $scenes = @($Scene)
}

foreach ($s in $scenes) {
    $sceneFile = "tools\gui-preview\Scene" + (Get-SceneClass $s) + ".java"
    if (-not (Test-Path $sceneFile)) {
        Write-Warning "Skip scene '$s': $sceneFile does not exist yet"
        continue
    }
    # 每个场景单独一个 classes 目录：场景文件写坏时不会连带编译失败
    $classes = "build\gui-preview\classes\$s"
    New-Item -ItemType Directory -Force $classes | Out-Null
    Write-Host "== scene: $s ==" -ForegroundColor Cyan
    javac -encoding UTF-8 -cp $cp -d $classes @baseSources $sceneFile
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed for scene '$s'"
    }
    java -cp "$cp;$classes" GuiPreview $s $OutDir $Time $Width $Height $Scale
    if ($LASTEXITCODE -ne 0) {
        throw "GuiPreview failed for scene '$s'"
    }
}

Write-Host "`nOutput: $OutDir" -ForegroundColor Green
