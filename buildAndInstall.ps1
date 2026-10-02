# 构建 → 校验 → 装进 PCL 测试实例
#
# 为什么需要这个脚本：`gradlew jar` / `gradlew jarJar` 单独跑**都不够**。
#   - `jar`          → 只产出 hall-<ver>.jar（不含 cloth-config / geckolib）
#   - `jarJar`       → 产出 -all.jar，但**不会重新混淆**
#   - `build`        → jar → reobfJar → jarJar → reobfJarJar，这才是能进游戏的产物
#
# 没经过 reobfJarJar 的 jar 里是 mojmap 名字（getMainRenderTarget 之类），
# 生产环境要的是 SRG 名字（m_12345_）。装错的话游戏会在启动页卡死，
# 而且 latest.log 是 0 字节 —— 连日志都来不及写。
# 所以脚本最后一定会校验 SRG 数量，不合格就直接拒绝安装。

param(
    [string]$Instance = "C:\Users\chy20\Desktop\desktop\pcl\.minecraft\versions\优化底包",
    [string]$ModJarName = "hall-0.3.0-all.jar"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

Write-Host "== 1/4 构建（gradlew build：含 reobfJarJar） ==" -ForegroundColor Cyan
& .\gradlew.bat build --offline --console=plain
if ($LASTEXITCODE -ne 0) { throw "构建失败" }

$built = Join-Path $root "build\libs\$ModJarName"
if (-not (Test-Path $built)) { throw "找不到产物: $built" }

Write-Host "== 2/4 校验是否已混淆（SRG 名字） ==" -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($built)
$entry = $z.Entries | Where-Object { $_.FullName -eq "org/bytechen/hall/client/rend/glint/ItemOutlinePipeline.class" }
if (-not $entry) { $entry = $z.Entries | Where-Object { $_.FullName -like "org/bytechen/hall/*.class" } | Select-Object -First 1 }
$s = $entry.Open(); $ms = New-Object System.IO.MemoryStream; $s.CopyTo($ms); $s.Close(); $z.Dispose()
$text = -join ($ms.ToArray() | ForEach-Object { if ($_ -ge 32 -and $_ -lt 127) { [char]$_ } else { "`0" } })
$srg = ([regex]::Matches($text, "m_\d+_")).Count
$moj = ([regex]::Matches($text, "getMainRenderTarget")).Count
Write-Host ("   SRG={0}  mojmap={1}" -f $srg, $moj)
if ($moj -gt 0 -or $srg -eq 0) {
    throw "产物没有重新混淆（说明没走 reobfJarJar）。装进游戏会卡启动页。"
}

Write-Host "== 3/4 备份旧包 ==" -ForegroundColor Cyan
$dst = Join-Path $Instance "mods\$ModJarName"
if ((Test-Path $dst) -and -not (Test-Path "$dst.bak")) {
    Copy-Item $dst "$dst.bak"
    Write-Host "   已备份到 $dst.bak"
}

Write-Host "== 4/4 安装 ==" -ForegroundColor Cyan
Copy-Item $built $dst -Force
Get-Item $dst | Select-Object LastWriteTime, Length, Name | Format-Table -AutoSize
Write-Host "完成。启动游戏后看 run 目录 ./logs/latest.log 里的 [ItemOutline] 段落。" -ForegroundColor Green
