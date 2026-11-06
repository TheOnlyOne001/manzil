# Manzil Environment Activation Script
# Usage: . .\activate_env.ps1

$env:JAVA_HOME = "C:\Users\shaya\scoop\apps\openjdk17\current"
$env:ANDROID_HOME = "C:\Users\shaya\android-sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\shaya\android-sdk"

$toolPaths = @(
    "C:\Users\shaya\android-sdk\platform-tools",
    "C:\Users\shaya\android-sdk\cmdline-tools\latest\bin",
    "C:\Users\shaya\scrcpy\scrcpy-win64-v5.0.1",
    "C:\Users\shaya\scoop\apps\openjdk17\current\bin"
)

foreach ($p in $toolPaths) {
    if ($env:PATH -notlike "*$p*") {
        $env:PATH = "$p;$env:PATH"
    }
}

Write-Host "=== Manzil Environment Primed ===" -ForegroundColor Green
Write-Host "JAVA_HOME    : $env:JAVA_HOME"
Write-Host "ANDROID_HOME : $env:ANDROID_HOME"
Write-Host "ADB          : $(Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)"
Write-Host "scrcpy       : $(Get-Command scrcpy -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)"
Write-Host "Python (uv)  : $(Get-Command uv -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)"
Write-Host "Connected Device:" -ForegroundColor Cyan
& adb devices -l
