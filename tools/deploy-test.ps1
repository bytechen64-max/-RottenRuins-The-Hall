# Deploy build/libs/rotten_ruins-<ver>-all.jar into the "111" test instance, refusing to run while
# that instance's Minecraft is still open.
#
# WHY this guard exists:
#   Windows lets you overwrite a jar that a running JVM has open, but the overwrite is in-place
#   (truncate + rewrite). Any class the JVM has not loaded yet then fails with
#       java.lang.NoClassDefFoundError: org/bytechen/rottenruins/.../SomeClass
#   even though the jar on disk is complete. That is the real cause of three earlier "missing
#   class" crashes (GroundingStakeBlock$1 / HallInfectionTracker / magic.bases.State):
#   the new jar was copied into mods/ while the game was still running.
#
# Usage:
#   .\tools\deploy-test.ps1              # deploy (game must be closed)
#   .\tools\deploy-test.ps1 -WhatIf      # verify only, copy nothing
#   .\tools\deploy-test.ps1 -Force       # skip the process check (only when the java process
#                                        # found is provably unrelated to this instance)

param(
    [string]$Instance = "C:\Users\Yao\Desktop\.minecraft\versions\111",
    [string]$ModJarName = "rotten_ruins-1.0.0beta2-all.jar",
    [switch]$WhatIf,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$built = Join-Path $root "build\libs\$ModJarName"
$dst = Join-Path $Instance "mods\$ModJarName"

# ---------- 0) is the game running? ----------
# Only count processes belonging to THIS instance: cmdline mentions versions\111 or --gameDir
# inside .minecraft. Never match the bare word "minecraft" - other projects' `gradlew runClient`
# also has it in the cmdline (that false positive already happened once).
$running = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' or Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object {
        $_.CommandLine -and (
            $_.CommandLine -match 'versions[\\/]+111\b' -or
            $_.CommandLine -match '--gameDir\s+"?[^\s"]*\.minecraft'
        )
    }
if ($running -and -not $Force) {
    if ($WhatIf) {
        # -WhatIf 不写文件，所以游戏开着也能检查，只是提醒一句
        Write-Host ("note: game is running (PID {0}) - a real deploy would be refused." -f ($running.ProcessId -join ', ')) -ForegroundColor Yellow
    } else {
        Write-Host ("Game is running (PID {0}) - refusing to overwrite the jar in mods/." -f ($running.ProcessId -join ', ')) -ForegroundColor Red
        Write-Host "Fully quit Minecraft (wait until the java process is gone), then run this script again." -ForegroundColor Red
        exit 1
    }
}

# ---------- 1) artifact present? ----------
if (-not (Test-Path $built)) { throw "artifact not found: $built (run: gradlew build --offline)" }
$fi = Get-Item $built
Write-Host ("artifact: {0}  {1} bytes  {2}" -f $fi.Name, $fi.Length, $fi.LastWriteTime) -ForegroundColor Cyan

# ---------- 2) jar must contain every class in build/classes/java/main ----------
Add-Type -AssemblyName System.IO.Compression.FileSystem
$classesDir = Join-Path $root "build\classes\java\main"
$jarClasses = @{}
$z = [System.IO.Compression.ZipFile]::OpenRead($built)
foreach ($e in $z.Entries) { if ($e.FullName -like '*.class') { $jarClasses[$e.FullName] = $e.Length } }
$z.Dispose()

$missing = @()
if (Test-Path $classesDir) {
    Push-Location $classesDir
    $disk = @(Get-ChildItem -Recurse -Filter *.class | ForEach-Object { $_.FullName.Substring($classesDir.Length + 1).Replace('\', '/') })
    Pop-Location
    $missing = @($disk | Where-Object { -not $jarClasses.ContainsKey($_) })
    Write-Host ("disk classes: {0}   jar classes: {1}   missing: {2}" -f $disk.Count, $jarClasses.Count, $missing.Count)
} else {
    Write-Host "WARN: build\classes\java\main not found - skipping the jar>=classes check (cannot detect a stale incremental build)" -ForegroundColor Yellow
}
if ($missing.Count -gt 0) {
    Write-Host "jar is missing classes (stale incremental build: delete build\classes\java\main and rebuild):" -ForegroundColor Red
    $missing | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

# ---------- 3) backup + install ----------
if ($WhatIf) { Write-Host "[WhatIf] checks passed, nothing copied." -ForegroundColor Yellow; exit 0 }

if (Test-Path $dst) {
    $bak = "$dst.bak"
    Copy-Item $dst $bak -Force
    Write-Host "backed up previous jar -> $bak"
}
Copy-Item $built $dst -Force
Get-Item $dst | Select-Object LastWriteTime, Length, Name | Format-Table -AutoSize
Write-Host "Deployed. You can start the game now." -ForegroundColor Green
