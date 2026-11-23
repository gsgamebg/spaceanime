$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $projectRoot '.tools'
$version = '3.0.24'
$archive = Join-Path $toolsDir "vlc-$version-win64.zip"
$destination = Join-Path $toolsDir 'vlc'
$baseUrl = "https://download.videolan.org/pub/videolan/vlc/$version/win64/vlc-$version-win64.zip"
New-Item -ItemType Directory -Force $toolsDir | Out-Null
if (Test-Path (Join-Path $destination "vlc-$version/.verified")) {
    Write-Output "VLC $version já está preparado."
    exit 0
}
$checksumText = (Invoke-WebRequest "$baseUrl.sha256").Content
if ($checksumText -is [byte[]]) { $checksumText = [Text.Encoding]::UTF8.GetString($checksumText) }
$checksum = [regex]::Match($checksumText, '\b[a-fA-F0-9]{64}\b').Value
if (-not $checksum) { throw 'Checksum oficial não encontrado.' }
if (-not (Test-Path $archive) -or (Get-FileHash $archive -Algorithm SHA256).Hash -ne $checksum) {
    Invoke-WebRequest $baseUrl -OutFile $archive
}
if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $checksum) { throw 'Checksum do VLC não confere.' }
Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
Set-Content -LiteralPath (Join-Path $destination "vlc-$version/.verified") -Value $checksum
Write-Output "VLC $version instalado localmente e verificado."
