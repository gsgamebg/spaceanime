$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
$sdkManager = Join-Path $projectRoot '.tools/android-cli/cmdline-tools/bin/sdkmanager.bat'
& $sdkManager "--sdk_root=$env:ANDROID_HOME" --licenses
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível concluir a configuração das licenças Android.' }
& $sdkManager "--sdk_root=$env:ANDROID_HOME" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar os pacotes Android.' }
foreach ($required in @('platforms/android-35/android.jar', 'build-tools/35.0.0/aapt2.exe', 'platform-tools/adb.exe')) {
    if (-not (Test-Path (Join-Path $env:ANDROID_HOME $required))) { throw "SDK incompleto: $required" }
}
