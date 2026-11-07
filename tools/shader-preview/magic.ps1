# 用法：在仓库根目录执行
#   .\tools\shader-preview\magic.ps1                 # 编译 + 渲染一帧，导出 build\magic-preview\magic-gui.png
#   .\tools\shader-preview\magic.ps1 -Time 9         # 换一帧（动画相位不同）
#
# 干什么：开一个隐藏窗口拿到 OpenGL 3.2 core 上下文，编译/链接魔法星图的两支着色器
#         （magic_starfield 深空背景 + magic_constellation 槽位连接光带），
#         按游戏里的混合语义画一遍并导出 PNG。**不需要启动游戏。**
#
# 为什么需要：GLSL 出错时 SplendidingShaders 会把异常吞进日志，表现是
#         "星空没了/连线退回虚线"，而图案好不好看只有渲染出来才知道。
param(
    [string]$ShaderDir = "src\main\resources\assets\rotten_ruins\shaders\core",
    [string]$OutDir = "build\magic-preview",
    [float]$Time = 4.0
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

# ── 从 Gradle 缓存里凑出 LWJGL 的 classpath（和 preview.ps1 同一套）──
$cache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.lwjgl"
if (-not (Test-Path $cache)) { throw "找不到 LWJGL：$cache（先跑一次 .\gradlew.bat build）" }
$jars = @()
foreach ($a in 'lwjgl', 'lwjgl-glfw', 'lwjgl-opengl') {
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1.jar" |
        Select-Object -First 1 -ExpandProperty FullName
    $jars += Get-ChildItem "$cache\$a" -Recurse -Filter "$a-3.3.1-natives-windows.jar" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
$cp = ($jars | Where-Object { $_ }) -join ';'

$classes = "build\shader-preview-classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null

# javac 在这台机器上默认 GBK，源码是 UTF-8，必须显式指定
javac -encoding UTF-8 -cp $cp -d $classes tools\shader-preview\MagicPreview.java
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

java -cp "$cp;$classes" MagicPreview $ShaderDir $OutDir $Time
if ($LASTEXITCODE -ne 0) { throw "MagicPreview 失败（多半是 GLSL 编译/链接错误，见上面的日志）" }

Write-Host "`n导出：$OutDir\magic-gui.png" -ForegroundColor Green
