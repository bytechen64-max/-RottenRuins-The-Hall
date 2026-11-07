@echo off
REM Outline shader offscreen preview / compile check -- see README.md in this folder.
REM Goes through cmd instead of a bare .ps1 because this machine blocks PowerShell
REM scripts by execution policy; -ExecutionPolicy Bypass only affects this process.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0preview.ps1" %*
endlocal
