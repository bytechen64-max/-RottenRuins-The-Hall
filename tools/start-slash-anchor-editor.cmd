@echo off
cd /d "%~dp0.."
python tools\slash-anchor-editor\server.py %*
pause
