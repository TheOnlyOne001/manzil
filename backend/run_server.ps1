# Manzil Hazard Ingestion Backend Server Launcher
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $ScriptDir
$env:PYTHONPATH = $ScriptDir

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Starting Manzil Hazard Ingestion API (FastAPI)  " -ForegroundColor Green
Write-Host "  Endpoints: http://127.0.0.1:8000/docs           " -ForegroundColor Yellow
Write-Host "  For Android USB: adb reverse tcp:8000 tcp:8000  " -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

& "$ScriptDir\.venv\Scripts\uvicorn.exe" app.main:app --host 0.0.0.0 --port 8000 --reload
