param([int]$Port = 8765, [string]$GameDir = "", [switch]$NoOpen)
& "$PSScriptRoot/start-slash-anchor-editor.ps1" -Port $Port -GameDir $GameDir -NoOpen:$NoOpen
