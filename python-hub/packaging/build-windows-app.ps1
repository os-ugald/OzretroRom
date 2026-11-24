$ErrorActionPreference = 'Stop'
$hubRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pyInstaller = Join-Path $hubRoot '.venv\Scripts\pyinstaller.exe'
if (-not (Test-Path $pyInstaller)) {
  throw "PyInstaller no encontrado en $pyInstaller"
}

$distDir = Join-Path $hubRoot 'dist'
$buildDir = Join-Path $hubRoot 'build'
$bundleDir = Join-Path $distDir 'OzRetroRom'
$zipPath = Join-Path $distDir 'OzRetroRom-Portable-Win64.zip'

if (Test-Path $bundleDir) { Remove-Item -Recurse -Force $bundleDir }
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }

Set-Location $hubRoot
& $pyInstaller --noconfirm --clean (Join-Path $PSScriptRoot 'ozretrorom.spec') --distpath $distDir --workpath $buildDir
if ($LASTEXITCODE -ne 0) {
  throw "PyInstaller falló con código $LASTEXITCODE"
}

Set-Content -Path (Join-Path $bundleDir 'portable.flag') -Value 'portable=1' -Encoding UTF8
Copy-Item -Force (Join-Path $PSScriptRoot 'LEEME-COMPARTIR.txt') (Join-Path $bundleDir 'LEEME-COMPARTIR.txt')
Set-Content -Path (Join-Path $bundleDir 'OzRetroRom-Servidor-LAN.bat') -Value "@echo off`r`ncd /d `"%~dp0`"`r`nOzRetroRom.exe --server-only`r`n" -Encoding ASCII

Compress-Archive -Path $bundleDir -DestinationPath $zipPath -CompressionLevel Optimal -Force
Write-Output "Paquete portable creado: $zipPath ($([Math]::Round((Get-Item $zipPath).Length / 1MB, 1)) MB)"
