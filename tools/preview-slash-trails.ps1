param([string]$OutFile = "build/slash-preview/preview.png", [switch]$Tracked, [switch]$Shear)
$ErrorActionPreference = "Stop"
$slashRoot = Split-Path -Parent $PSScriptRoot
Set-Location $slashRoot
$slashCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.lwjgl'
$slashJars = @()
foreach ($slashModule in @('lwjgl','lwjgl-glfw','lwjgl-opengl')) {
    $slashJars += Get-ChildItem -LiteralPath (Join-Path $slashCache $slashModule) -Recurse -Filter "$slashModule-3.3.1.jar" | Select-Object -First 1 -ExpandProperty FullName
    $slashJars += Get-ChildItem -LiteralPath (Join-Path $slashCache $slashModule) -Recurse -Filter "$slashModule-3.3.1-natives-windows.jar" | Select-Object -First 1 -ExpandProperty FullName
}
$slashJoml = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.joml/joml') -Recurse -Filter 'joml-1.10.5.jar' | Select-Object -First 1 -ExpandProperty FullName
$slashClasspath = ($slashJars + @($slashJoml)) -join ';'
$slashClasses = 'build/slash-preview-classes'
New-Item -ItemType Directory -Force -Path $slashClasses | Out-Null
javac -encoding UTF-8 -cp $slashClasspath -d $slashClasses src/main/java/org/bytechen/rottenruins/client/combat/slash/trail/SlashTrailGeometry.java src/main/java/org/bytechen/rottenruins/client/combat/slash/trail/anchors/TrailTransforms.java src/main/java/org/bytechen/rottenruins/core/combat/slash/TrailHistory.java src/main/java/org/bytechen/rottenruins/client/combat/slash/trail/anchors/TrailRibbonGeometry.java tools/shader-preview/SlashTrailPreview.java
if ($LASTEXITCODE -ne 0) { throw 'Slash preview compilation failed' }
$previewArgs = @()
if ($Tracked -or $Shear) { $previewArgs += "tracked" }
if ($Shear) { $previewArgs += "shear" }
java -cp "$slashClasspath;$slashClasses" SlashTrailPreview src/main/resources/assets/rotten_ruins/shaders/core src/main/resources/assets/rotten_ruins/textures/effect/slash_crescent.png $OutFile @previewArgs
if ($LASTEXITCODE -ne 0) { throw 'Slash GPU preview failed' }
