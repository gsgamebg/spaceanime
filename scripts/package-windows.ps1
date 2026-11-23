$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $vlc = Join-Path $root '.tools/vlc/vlc-3.0.24'
    if (-not (Test-Path -LiteralPath "$vlc/.verified")) { throw 'Execute scripts/install-player.ps1 primeiro.' }
    & ./gradlew.bat :composeApp:desktopTest :composeApp:createDistributable --console=plain --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw "Build falhou: $LASTEXITCODE" }
    $image = Join-Path $root 'composeApp/build/compose/binaries/main/app/SpaceAnime'
    if (-not (Test-Path -LiteralPath "$image/SpaceAnime.exe")) { throw 'Executavel nao encontrado.' }
    $output = Join-Path $root ('dist/SpaceAnime-Windows-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $output | Out-Null
    Copy-Item -LiteralPath $image -Destination "$output/SpaceAnime" -Recurse
    Copy-Item -LiteralPath $vlc -Destination "$output/SpaceAnime/vlc" -Recurse
    Copy-Item -LiteralPath "$root/docs/WINDOWS-PORTATIL.md" -Destination "$output/LEIA-ME.md"
    Copy-Item -LiteralPath "$root/composeApp/src/commonMain/composeResources/files/licenses" -Destination "$output/SpaceAnime/licenses" -Recurse
    $savedVlc = $env:ANIM_VLC_HOME
    $savedPlugins = $env:VLC_PLUGIN_PATH
    $savedPath = $env:PATH
    try {
        $env:ANIM_VLC_HOME = $null
        $env:VLC_PLUGIN_PATH = $null
        $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
        $check = Start-Process -FilePath "$output/SpaceAnime/SpaceAnime.exe" -ArgumentList '--check-runtime' -WindowStyle Hidden -PassThru
        if (-not $check.WaitForExit(60000)) { $check.Kill(); throw 'Verificacao do runtime excedeu 60 segundos.' }
        if ($check.ExitCode -ne 0) { throw "Verificacao do runtime portatil falhou: $($check.ExitCode)" }
        Write-Output 'Runtime portatil OK: executavel, Java, VLC e plugins.'
    } finally {
        $env:ANIM_VLC_HOME = $savedVlc
        $env:VLC_PLUGIN_PATH = $savedPlugins
        $env:PATH = $savedPath
    }
    Compress-Archive -LiteralPath "$output/SpaceAnime", "$output/LEIA-ME.md" -DestinationPath "$output.zip"
    $hash = (Get-FileHash -LiteralPath "$output.zip" -Algorithm SHA256).Hash
    Set-Content -LiteralPath "$output.zip.sha256" -Value "$hash  $([IO.Path]::GetFileName($output)).zip" -Encoding ascii
    Write-Output "Pacote: $output.zip"
    Write-Output "Abrir: $output/SpaceAnime/SpaceAnime.exe"
} finally { Pop-Location }

