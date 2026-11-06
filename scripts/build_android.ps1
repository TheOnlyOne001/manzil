param([switch]$Install, [string]$Serial = 'RZGL40EETBZ')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Users\shaya\scoop\apps\openjdk17\current'
$env:ANDROID_HOME = 'C:\Users\shaya\android-sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Push-Location (Join-Path $projectRoot 'android')
try {
    & .\gradlew.bat --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android verification failed.' }
    if ($Install) {
        & "$env:ANDROID_HOME\platform-tools\adb.exe" -s $Serial install -r app\build\outputs\apk\debug\app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Device install failed.' }
        & "$env:ANDROID_HOME\platform-tools\adb.exe" -s $Serial shell am start -n app.manzil/.offline.OfflineActivity
        if ($LASTEXITCODE -ne 0) { throw 'Activity launch failed.' }
    }
} finally { Pop-Location }
