$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & ./gradlew.bat :composeApp:run
    if ($LASTEXITCODE -ne 0) { throw "Gradle falhou: $LASTEXITCODE" }
} finally { Pop-Location }
