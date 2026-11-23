$projectRoot = Split-Path $PSScriptRoot -Parent
$projectJdk = Get-ChildItem (Join-Path $projectRoot '.tools/jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $projectJdk) { throw 'Execute python scripts/bootstrap.py primeiro.' }
$env:JAVA_HOME = $projectJdk.FullName
$env:ANDROID_HOME = Join-Path $projectRoot '.tools/android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-home'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
$projectVlc = Join-Path $projectRoot '.tools/vlc/vlc-3.0.24'
if (Test-Path (Join-Path $projectVlc 'libvlc.dll')) {
    $env:ANIM_VLC_HOME = $projectVlc
    $env:VLC_PLUGIN_PATH = Join-Path $projectVlc 'plugins'
    $env:PATH = "$projectVlc;$env:PATH"
}
