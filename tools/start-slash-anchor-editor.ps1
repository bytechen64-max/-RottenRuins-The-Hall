param([int]$Port = 8765, [string]$GameDir = "", [switch]$NoOpen)
$editorArgs = @("$PSScriptRoot/slash-anchor-editor/server.py", "--port", $Port)
if ($GameDir) { $editorArgs += @("--game-dir", $GameDir) }
if ($NoOpen) { $editorArgs += "--no-open" }
python @editorArgs
