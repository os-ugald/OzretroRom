@echo off
setlocal
cd /d "%~dp0"
set "WRAPPER=%CD%\gradle\wrapper\gradle-wrapper.jar"
set "TMP=%WRAPPER%.tmp"
set "URL=https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
set "EXPECTED=2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"

if exist "%WRAPPER%" (
  echo [OK] gradle-wrapper.jar ya existe.
  goto :done
)

echo Descargando el Gradle Wrapper oficial 8.11.1...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%TMP%'; $h=(Get-FileHash '%TMP%' -Algorithm SHA256).Hash.ToLower(); if($h -ne '%EXPECTED%'){Remove-Item -Force '%TMP%' -ErrorAction SilentlyContinue; throw ('Checksum incorrecto: ' + $h)}; Move-Item -Force '%TMP%' '%WRAPPER%'"

if errorlevel 1 (
  echo.
  echo [ERROR] No se pudo descargar o verificar gradle-wrapper.jar.
  echo Revisa Internet, antivirus, proxy o firewall y vuelve a ejecutar este archivo.
  pause
  exit /b 1
)

:done
echo.
echo [LISTO] Ahora abre esta carpeta completa en Android Studio.
echo No abras solamente la carpeta app.
pause
endlocal
