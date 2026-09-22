@echo off
setlocal EnableExtensions
cd /d "%~dp0"

title RotaPro - Gerar APK

echo ================================================
echo        ROTAPRO 0.7 - GERADOR DE APK
echo ================================================
echo.

if not exist "RotaPro\build.gradle.kts" (
  echo ERRO: pasta do projeto nao encontrada.
  pause
  exit /b 1
)

where adb >nul 2>nul
if %errorlevel%==0 set "ADB_OK=1"

if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"

if exist "RotaPro\gradlew.bat" (
  echo Gradle Wrapper encontrado.
  cd RotaPro
  call gradlew.bat assembleDebug
  if errorlevel 1 goto BUILD_FAIL
  cd ..
  goto BUILD_OK
)

where gradle >nul 2>nul
if %errorlevel%==0 (
  echo Gradle encontrado no Windows.
  cd RotaPro
  call gradle assembleDebug
  if errorlevel 1 goto BUILD_FAIL
  cd ..
  goto BUILD_OK
)

echo.
echo O projeto ainda nao possui Gradle Wrapper e o comando gradle nao foi encontrado.
echo.
echo FAZER UMA VEZ:
echo 1. Instale o Android Studio.
echo 2. Abra a pasta RotaPro no Android Studio.
echo 3. Aguarde o Gradle sincronizar o projeto.
echo 4. Feche o Android Studio.
echo 5. Execute este arquivo novamente.
echo.
echo Depois disso, o APK sera gerado automaticamente.
echo.
pause
exit /b 2

:BUILD_OK
set "APK=RotaPro\app\build\outputs\apk\debug\app-debug.apk"
echo.
echo ================================================
echo APK GERADO COM SUCESSO!
echo ================================================
if exist "%APK%" (
  echo.
  echo Arquivo:
  echo %CD%\%APK%
  echo.
  copy /Y "%APK%" "%CD%\RotaPro-0.7.apk" >nul
  echo Copia criada:
  echo %CD%\RotaPro-0.7.apk
  echo.
  start "" explorer.exe /select,"%CD%\RotaPro-0.7.apk"
) else (
  echo O build terminou, mas o APK nao foi encontrado no caminho esperado.
)
pause
exit /b 0

:BUILD_FAIL
echo.
echo ================================================
echo ERRO AO COMPILAR O APK
 echo ================================================
echo Verifique a mensagem acima no Android Studio/Gradle.
pause
exit /b 3
