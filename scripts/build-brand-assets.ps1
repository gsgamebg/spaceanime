$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$directory = Join-Path $root 'assets/branding'
$source = [Drawing.Image]::FromFile("$directory/spaceanime-neon.png")
$bitmap = New-Object Drawing.Bitmap 256, 256
$graphics = [Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.Clear([Drawing.Color]::Transparent)
    $scale = [Math]::Min(256 / $source.Width, 256 / $source.Height)
    $width = [int]($source.Width * $scale)
    $height = [int]($source.Height * $scale)
    $graphics.DrawImage($source, [int]((256-$width)/2), [int]((256-$height)/2), $width, $height)
    $bitmap.Save("$directory/spaceanime.png", [Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $bitmap.Dispose(); $source.Dispose() }
$png = [IO.File]::ReadAllBytes("$directory/spaceanime.png")
$writer = [IO.BinaryWriter]::new([IO.File]::Create("$directory/spaceanime.ico"))
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]1)
    $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0)
    $writer.Write([uint16]1); $writer.Write([uint16]32); $writer.Write([uint32]$png.Length); $writer.Write([uint32]22)
    $writer.Write($png)
} finally { $writer.Dispose() }
Copy-Item -LiteralPath "$directory/spaceanime.png" -Destination "$root/composeApp/src/desktopMain/resources/spaceanime.png" -Force
Copy-Item -LiteralPath "$directory/spaceanime.png" -Destination "$root/composeApp/src/androidMain/res/drawable/spaceanime_brand.png" -Force
Write-Output 'Icones gerados a partir da logo fornecida.'
