@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JAVA_HOME points to an invalid JDK: %JAVA_HOME%
    exit /b 1
  )
) else (
  set "JAVA_EXE=java.exe"
  where java.exe >nul 2>&1
  if errorlevel 1 (
    echo ERROR: Java was not found. Set JAVA_HOME to a supported JDK.
    exit /b 1
  )
)

if not exist "%WRAPPER%" (
  echo Downloading Gradle wrapper bootstrap...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar'; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile '%WRAPPER%.tmp'; $h=(Get-FileHash '%WRAPPER%.tmp' -Algorithm SHA256).Hash.ToLower(); if($h -ne '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'){throw 'Wrapper checksum mismatch'}; Move-Item -Force '%WRAPPER%.tmp' '%WRAPPER%'"
  if errorlevel 1 exit /b 1
)
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER%" org.gradle.wrapper.GradleWrapperMain %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
