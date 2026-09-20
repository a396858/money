@echo off
chcp 65001 >nul
title MoneyBook - 记账本
cd /d "%~dp0"

set "PY=C:\Users\72635\.workbuddy\binaries\python\envs\default\Scripts\python.exe"
if not exist "%PY%" set "PY=C:\Users\72635\.workbuddy\binaries\python\versions\3.13.12\python.exe"
if not exist "%PY%" set "PY=python"

"%PY%" "%~dp0tools\start_server.py"
pause
