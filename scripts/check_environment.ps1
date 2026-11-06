param([string]$OutputPath)
# Read-only audit; does not install, start services, enumerate credentials or list device IDs.
$ErrorActionPreference = 'Continue'
$taskCommands = @{}
$taskVersionArgs = @{
  java=@('-version'); javac=@('-version'); adb=@('version'); emulator=@('-version');
  gradle=@('--version'); kotlinc=@('-version'); node=@('--version'); npm=@('--version');
  python=@('--version'); uv=@('--version'); docker=@('--version'); git=@('--version')
}
foreach ($taskName in $taskVersionArgs.Keys) {
  $taskCommand = Get-Command $taskName -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $taskCommand -and $taskName -eq 'adb' -and (Test-Path "$env:USERPROFILE\android-sdk\platform-tools\adb.exe")) {
    $taskCommand = [PSCustomObject]@{ Source = "$env:USERPROFILE\android-sdk\platform-tools\adb.exe" }
  }
  if ($taskCommand) {
    $taskArguments = $taskVersionArgs[$taskName]
    $taskVersion = (& $taskCommand.Source @taskArguments 2>&1 | Out-String).Trim()
    $taskCommands[$taskName] = @{ found=$true; path=$taskCommand.Source; version=$taskVersion }
  } else { $taskCommands[$taskName] = @{ found=$false } }
}
$taskPaths = @(
  "$env:LOCALAPPDATA\Android\Sdk", "$env:USERPROFILE\android-sdk", 'C:\Program Files\Android\Android Studio',
  "$env:LOCALAPPDATA\Programs\Android Studio", "$env:USERPROFILE\.android\avd"
)
$taskPathResults = @($taskPaths | ForEach-Object { @{path=$_; exists=(Test-Path -LiteralPath $_)} })
$taskEnvironment = @{}
foreach ($taskName in @('ANDROID_HOME','ANDROID_SDK_ROOT','JAVA_HOME')) {
  $taskEnvironment[$taskName] = [Environment]::GetEnvironmentVariable($taskName)
}
$taskComputer = Get-CimInstance Win32_ComputerSystem
$taskDockerReachable = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $taskDockerServerVersion = (& docker info --format '{{.ServerVersion}}' 2>$null | Out-String).Trim()
  # Some client versions return zero even when the server pipe is absent.
  $taskDockerReachable = ($taskDockerServerVersion -match '^\d+\.\d+\.\d+')
}
$taskReport = [ordered]@{
  checked_at_utc=[DateTime]::UtcNow.ToString('o'); commands=$taskCommands;
  known_paths=$taskPathResults; environment=$taskEnvironment;
  docker_engine_reachable=$taskDockerReachable;
  ram_gb=[math]::Round($taskComputer.TotalPhysicalMemory/1GB,1);
  hypervisor_present=$taskComputer.HypervisorPresent;
  disk_c_free_gb=[math]::Round((Get-PSDrive C).Free/1GB,1)
}
$taskJson = $taskReport | ConvertTo-Json -Depth 6
if ($OutputPath) { $taskJson | Set-Content -LiteralPath $OutputPath -Encoding utf8 }
$taskJson
