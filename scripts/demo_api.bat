@echo off
setlocal
echo =======================================================================
echo          ROSTERGUARD LIVE API DEMONSTRATION (WINDOWS)
echo =======================================================================
powershell -ExecutionPolicy Bypass -File "%~dp0demo_api.ps1" %*
