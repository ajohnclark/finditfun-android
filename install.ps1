$ErrorActionPreference = 'Stop'
$ProjectRoot = $PSScriptRoot
& (Join-Path $ProjectRoot 'build.ps1') -Task verify
if ($LASTEXITCODE -ne 0) { throw 'Build verification failed; refusing to install.' }

$AdbCandidates = @()
$AdbOnPath = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($null -ne $AdbOnPath) { $AdbCandidates += $AdbOnPath.Source }
foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
    if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
        $AdbCandidates += Join-Path $sdkRoot 'platform-tools\adb.exe'
    }
}
if ($env:LOCALAPPDATA) {
    $AdbCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
}
$Adb = $AdbCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if ($null -eq $Adb) {
    throw 'Could not find adb. Add it to PATH or set ANDROID_SDK_ROOT.'
}

$Devices = & $Adb devices
$Ready = @($Devices | Select-String -Pattern "\tdevice$")
if ($Ready.Count -ne 1) {
    throw "Expected exactly one authorized Android device. Current adb output:`n$($Devices -join "`n")"
}

& $Adb install -r (Join-Path $ProjectRoot 'artifacts\finditfun-mvp-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'ADB installation failed.' }
& $Adb shell am start -n 'com.finditfun.app.debug/com.finditfun.app.MainActivity'
