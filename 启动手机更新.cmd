@echo off
cd /d "%~dp0"
python scripts\phone_update.py --launch
if errorlevel 1 pause
