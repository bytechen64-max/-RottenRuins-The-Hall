@echo off
REM GUI offline preview harness -- see README.md in this folder.
REM Goes through cmd because this machine blocks PowerShell scripts by execution policy.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0gui_preview.ps1" %*
endlocal
