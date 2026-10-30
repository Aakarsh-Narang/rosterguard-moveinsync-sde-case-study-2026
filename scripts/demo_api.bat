@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0demo_api.ps1" %*
set SCRIPT_EXIT_CODE=%ERRORLEVEL%
if %SCRIPT_EXIT_CODE% NEQ 0 (
    echo [ERROR] Demonstration terminated with failure exit code: %SCRIPT_EXIT_CODE%
    exit /b %SCRIPT_EXIT_CODE%
)
exit /b 0
