# 用法：在仓库根目录执行
#   .\tools\shader-preview\preview.ps1                      # 火焰预设（ColorMode 4）
#   .\tools\shader-preview\preview.ps1 -ColorMode 1         # 双色滚动
#   .\tools\shader-preview\preview.ps1 -CompileOnly         # 只检查 GLSL 能否编译/链接
#
# 干什么：开一个隐藏窗口拿到 OpenGL 3.2 core 上下文，把描边着色器离屏跑一遍
#         （剪影遮罩 → 环形合成），导出 PNG。**不需要启动游戏。**
# 为什么需要：GLSL 编译失败时 SPLendidingShaders 会把异常吞进日志、整条描边静默消失，
#         而"图案好不好看"更是只有渲染出来才知道。这个脚本把这两件事都提前到编译期之外。
param(
    [string]$ShaderDir = "src\main\resources\assets\hall\shaders\core",
    [string]$OutDir = "build\shader-preview",
    [float]$ColorMode = 4,
    [float]$Time = 5.0,
    [float]$FreqX = 18,
    [float]$Floor = 0,
    # 可选：用一张真实的物品贴图当 Sampler0（看真实剪影，例如法力板的圆角）。
    [string]$ItemTexture = "",
    # 描边宽度（预览画布 320px；默认 10 便于看图案，游戏里通常是 2.5~3）。
    [float]$Width = 10,
    # FireStrength：0 = 不画火焰光环，1 = 开（fire_2 默认 1）。
    [float]$Fire = 1,
    [switch]$CompileOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

# ── 从 Gradle 缓存里凑出 LWJGL 的 classpath ──
$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.lwjgl"
if (-not (Test-Path $cache)) { throw "找不到 LWJGL：$cache（先跑一次 .\gradlew.bat build）" }
$jars = @()
foreach ($a in 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl') {
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1.jar" | Select-Object -First 1 -ExpandProperty FullName
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1-natives-windows.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$cp = ($jars | Where-Object { $_ }) -join ';'

$classes = "build\shader-preview-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null

# javac 在这台机器上默认 GBK，源码是 UTF-8，必须显式指定
javac -encoding UTF-8 -cp $cp -d $classes tools\shader-preview\ShaderPreview.java tools\shader-preview\ShaderCompileTest.java
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

if ($CompileOnly) {
    java -cp "$cp;$classes" ShaderCompileTest `
        "$ShaderDir\outline_mask.vsh" "$ShaderDir\outline_mask.fsh" `
        "$ShaderDir\outline_ring.vsh" "$ShaderDir\outline_ring.fsh" `
        "$ShaderDir\fire_volume.vsh" "$ShaderDir\fire_volume.fsh"
    if ($LASTEXITCODE -ne 0) { throw "着色器没通过编译/链接检查" }
} else {
    # 第 7 个参数是贴图（空串 = 1×1 白贴图），第 8 个是描边宽度
    java -cp "$cp;$classes" ShaderPreview $ShaderDir $OutDir $ColorMode $Time $FreqX $Floor $ItemTexture $Width $Fire
    if ($LASTEXITCODE -ne 0) { throw "ShaderPreview 失败" }
    Write-Host "`n导出：$OutDir\mask.png（剪影遮罩）/ $OutDir\ring.png（合成结果）" -ForegroundColor Green
}
