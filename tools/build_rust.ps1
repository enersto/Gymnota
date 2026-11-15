param(
    [string]$Abis = 'armeabi-v7a,arm64-v8a,x86_64',
    [Parameter(Mandatory=$true)][string]$OutputDir,
    [Parameter(Mandatory=$true)][string]$Ndk
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$cargo = if ($env:GYMNOTA_CARGO) { $env:GYMNOTA_CARGO } else { (Get-Command cargo -ErrorAction Stop).Source }
$targets = @{ 'arm64-v8a' = @('aarch64-linux-android','aarch64-linux-android28-clang.cmd');
    'armeabi-v7a' = @('armv7-linux-androideabi','armv7a-linux-androideabi28-clang.cmd');
    'x86_64' = @('x86_64-linux-android','x86_64-linux-android28-clang.cmd') }
foreach ($abi in $Abis.Split(',')) {
    if (!$targets.ContainsKey($abi)) { throw "Unsupported Rust ABI: $abi" }
    $target = $targets[$abi][0]
    $linker = Join-Path $Ndk ('toolchains/llvm/prebuilt/windows-x86_64/bin/' + $targets[$abi][1])
    if (!(Test-Path -LiteralPath $linker)) { throw "NDK linker not found: $linker" }
    $envName = 'CARGO_TARGET_' + $target.Replace('-','_').ToUpperInvariant() + '_LINKER'
    $ccName = 'CC_' + $target.Replace('-','_')
    $arName = 'AR_' + $target.Replace('-','_')
    [Environment]::SetEnvironmentVariable($ccName, $linker, 'Process')
    [Environment]::SetEnvironmentVariable($arName, (Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-ar.exe'), 'Process')
    $previous = [Environment]::GetEnvironmentVariable($envName, 'Process')
    try {
        [Environment]::SetEnvironmentVariable($envName, $linker, 'Process')
        & $cargo build --manifest-path (Join-Path $projectRoot 'rust/history_core/Cargo.toml') --target-dir (Join-Path $projectRoot "rust/history_core/target") --locked --release --lib --features android-jni --target $target
        if ($LASTEXITCODE -ne 0) { throw "Rust compilation failed: $target" }
        $library = Join-Path $projectRoot "rust/history_core/target/$target/release/libgymnota_history_core.so"
        $destination = Join-Path $OutputDir $abi
        New-Item -ItemType Directory -Force $destination | Out-Null
        Copy-Item -LiteralPath $library -Destination (Join-Path $destination 'libgymnota_history_core.so')
    } finally { [Environment]::SetEnvironmentVariable($envName, $previous, 'Process') }
}
