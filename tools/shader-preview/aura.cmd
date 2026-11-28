@echo off
REM Aura lab: render baseline / route2 / route3 comparison images. See README.md.
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0aura.ps1" %*
endlocal
