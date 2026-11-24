param(
  [switch]$Foreground
)
$ErrorActionPreference = 'Stop'
$hubRoot = $PSScriptRoot
$logsDir = Join-Path $hubRoot 'logs'
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

# Stop any process currently listening on port 17894
$conns = Get-NetTCPConnection -LocalPort 17894 -State Listen -ErrorAction SilentlyContinue
foreach ($c in $conns) {
  $procId = $c.OwningProcess
  if ($procId -and $procId -ne 0) {
    Write-Output "Deteniendo proceso PID $procId en puerto 17894..."
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
  }
}
Start-Sleep -Milliseconds 500

$pyExe = Join-Path $hubRoot '.venv\Scripts\python.exe'
if (-not (Test-Path $pyExe)) {
  throw "No existe el entorno virtual en $pyExe"
}

$env:OZ_HOST = '0.0.0.0'
$env:OZ_PORT = '17894'
$env:OZ_ROMS_DIR = 'F:\Games\RetroArch\roms'
$env:OZ_JAVA_COMPANION_URL = 'http://192.168.100.173:17893'

if ($Foreground) {
  Set-Location $hubRoot
  & $pyExe -m uvicorn app.main:app --host $env:OZ_HOST --port $env:OZ_PORT
  exit $LASTEXITCODE
}

$outLog = Join-Path $logsDir 'hub-17894.out.log'
$errLog = Join-Path $logsDir 'hub-17894.err.log'
$proc = Start-Process -FilePath $pyExe `
  -ArgumentList @('-m', 'uvicorn', 'app.main:app', '--host', $env:OZ_HOST, '--port', $env:OZ_PORT) `
  -WorkingDirectory $hubRoot `
  -RedirectStandardOutput $outLog `
  -RedirectStandardError $errLog `
  -WindowStyle Hidden `
  -PassThru

Write-Output "Oz Python Hub lanzado PID=$($proc.Id) en http://192.168.100.173:$($env:OZ_PORT)/ (y http://127.0.0.1:$($env:OZ_PORT)/)"
