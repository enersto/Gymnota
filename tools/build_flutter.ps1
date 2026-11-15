param(
    [Parameter(Mandatory = $true)][string]$FlutterSdk,
    [string]$Jdk = $env:JAVA_HOME,
    [switch]$ChinaMirror,
    [switch]$WithoutRust,
    [string]$Proxy,
    [ValidateSet("preview", "debug")][string]$BuildType = "preview",
    [ValidateSet("armeabi-v7a", "arm64-v8a", "x86_64")][string]$Abi
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$flutter = Join-Path $FlutterSdk 'bin/flutter.bat'
if (!(Test-Path -LiteralPath $flutter)) { throw "Flutter SDK not found: $FlutterSdk" }
$flutter = (Resolve-Path -LiteralPath $flutter).Path
$previousJava = $env:JAVA_HOME
$previousStorage = $env:FLUTTER_STORAGE_BASE_URL
$previousPub = $env:PUB_HOSTED_URL
try {
    if ($Jdk) { $env:JAVA_HOME = $Jdk }
    if ($ChinaMirror) {
        $env:FLUTTER_STORAGE_BASE_URL = 'https://storage.flutter-io.cn'
        $env:PUB_HOSTED_URL = 'https://pub.flutter-io.cn'
    }
    Push-Location (Join-Path $projectRoot 'flutter_library')
    try {
        & $flutter pub get
        if ($LASTEXITCODE -ne 0) { throw 'Flutter dependency resolution failed' }
        & $flutter analyze --no-pub
        if ($LASTEXITCODE -ne 0) { throw 'Flutter analysis failed' }
        & $flutter test --no-pub
        if ($LASTEXITCODE -ne 0) { throw 'Flutter tests failed' }
    } finally { Pop-Location }
    $task = if ($BuildType -eq 'preview') { ':app:assemblePreview' } else { ':app:assembleDebug' }
    $gradleArgs = @('-p', $projectRoot, $task, '--console=plain')
    if ($WithoutRust) { $gradleArgs += '-PrustHistory=false' }
    if ($Abi) {
        $target = @{ 'armeabi-v7a' = 'android-arm'; 'arm64-v8a' = 'android-arm64'; 'x86_64' = 'android-x64' }[$Abi]
        $gradleArgs += "-PpreviewAbi=$Abi", "-Ptarget-platform=$target"
    }
    if ($Proxy) {
        $proxyUri = [Uri]$Proxy
        if (!$proxyUri.IsAbsoluteUri -or $proxyUri.Port -le 0) { throw 'Use an absolute HTTP proxy URI' }
        $gradleArgs += "-Dhttps.proxyHost=$($proxyUri.Host)", "-Dhttps.proxyPort=$($proxyUri.Port)",
            "-Dhttp.proxyHost=$($proxyUri.Host)", "-Dhttp.proxyPort=$($proxyUri.Port)"
    }
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:FLUTTER_STORAGE_BASE_URL = $previousStorage
    $env:PUB_HOSTED_URL = $previousPub
}
