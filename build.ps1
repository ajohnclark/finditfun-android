param(
    [ValidateSet('verify', 'test', 'apk', 'clean')]
    [string]$Task = 'verify'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = $PSScriptRoot

function Find-ToolRoot {
    param(
        [string[]]$Candidates,
        [string]$RequiredPath,
        [string]$Description
    )

    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        if (Test-Path -LiteralPath (Join-Path $candidate $RequiredPath)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "Could not find $Description. Set the relevant environment variable or install Android Studio."
}

$JdkCandidates = @($env:JAVA_HOME)
if ($env:ProgramFiles) {
    $JdkCandidates += Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
}
$JavaOnPath = Get-Command java.exe -ErrorAction SilentlyContinue
if ($null -ne $JavaOnPath) {
    $JavaBin = Split-Path -Parent $JavaOnPath.Source
    $JdkCandidates += Split-Path -Parent $JavaBin
}
$JdkRoot = Find-ToolRoot $JdkCandidates 'bin\java.exe' 'a Java 17 JDK'

$SdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
if ($env:LOCALAPPDATA) {
    $SdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$SdkRoot = Find-ToolRoot $SdkCandidates 'platforms\android-35\android.jar' 'the Android 35 SDK'
$ApkSigner = Join-Path $SdkRoot 'build-tools\35.0.0\apksigner.bat'

foreach ($required in @(
    (Join-Path $JdkRoot 'bin\java.exe'),
    (Join-Path $JdkRoot 'bin\keytool.exe'),
    $ApkSigner
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required command-line Android dependency is missing: $required"
    }
}

$env:JAVA_HOME = $JdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:GRADLE_USER_HOME = Join-Path $ProjectRoot '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $ProjectRoot '.android-home'

$LocalDir = Join-Path $ProjectRoot '.local'
$Keystore = Join-Path $LocalDir 'finditfun-debug.keystore'
if (-not (Test-Path -LiteralPath $LocalDir)) {
    New-Item -ItemType Directory -Path $LocalDir | Out-Null
}
if (-not (Test-Path -LiteralPath $Keystore)) {
    & (Join-Path $JdkRoot 'bin\keytool.exe') -genkeypair -v `
        -keystore $Keystore -storepass android -alias androiddebugkey `
        -keypass android -dname 'CN=Find It Fun Debug,O=Local Development,C=US' `
        -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create the project-local debug keystore.' }
}

$Wrapper = Join-Path $ProjectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $Wrapper)) {
    throw 'The project Gradle wrapper is missing.'
}

Push-Location $ProjectRoot
try {
    switch ($Task) {
        'clean' { $GradleArgs = @('--no-daemon', 'clean') }
        'test' { $GradleArgs = @('--no-daemon', 'testDebugUnitTest') }
        'apk' { $GradleArgs = @('--no-daemon', 'assembleDebug') }
        default { $GradleArgs = @('--no-daemon', 'testDebugUnitTest', 'lintDebug', 'assembleDebug') }
    }

    & $Wrapper @GradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle task failed with exit code $LASTEXITCODE." }

    if ($Task -in @('verify', 'apk')) {
        $BuiltApk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
        if (-not (Test-Path -LiteralPath $BuiltApk)) { throw "Expected APK was not produced: $BuiltApk" }

        $Artifacts = Join-Path $ProjectRoot 'artifacts'
        if (-not (Test-Path -LiteralPath $Artifacts)) {
            New-Item -ItemType Directory -Path $Artifacts | Out-Null
        }
        $FinalApk = Join-Path $Artifacts 'finditfun-mvp-debug.apk'
        Copy-Item -LiteralPath $BuiltApk -Destination $FinalApk -Force

        & $ApkSigner verify --verbose --print-certs $FinalApk
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
        Get-FileHash -Algorithm SHA256 -LiteralPath $FinalApk
        Get-Item -LiteralPath $FinalApk | Select-Object FullName, Length, LastWriteTime
    }
} finally {
    Pop-Location
}
