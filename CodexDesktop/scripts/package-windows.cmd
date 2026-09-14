@echo off
REM Windows wrapper around the jpackage build.
REM
REM   scripts\package-windows.cmd            -> dist\CodexDesktop\CodexDesktop.exe
REM   scripts\package-windows.cmd installer  -> dist\CodexDesktop-0.1.0.exe (needs WiX)
REM
REM Set JAVA_HOME to a JDK 21 install and MVN to your mvn.cmd if they are not on PATH.
setlocal enabledelayedexpansion

set "TYPE=%~1"
if "%TYPE%"=="" set "TYPE=app-image"

set "APP_NAME=CodexDesktop"
set "APP_VERSION=0.1.0"
set "MAIN_CLASS=com.codexdesktop.Launcher"
set "MAIN_JAR=codex-desktop.jar"

pushd "%~dp0.."

if "%MVN%"=="" set "MVN=mvn"
if "%JAVA_HOME%"=="" (
  set "JPACKAGE=jpackage"
) else (
  set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
)

echo ==^> Building jars
call "%MVN%" -B clean package
if errorlevel 1 goto :fail

if not exist "target\app\%MAIN_JAR%" (
  echo !! target\app\%MAIN_JAR% is missing >&2
  goto :fail
)

rem Windows can hold the previous app image's DLLs open for a moment after it exits,
rem so give the delete a few attempts before giving up.
for /l %%A in (1,1,5) do (
  if exist dist rmdir /s /q dist 2>nul
  if not exist "dist\%APP_NAME%" goto :dist_clear
  ping -n 2 127.0.0.1 >nul
)
:dist_clear
if exist "dist\%APP_NAME%" (
  echo !! dist\%APP_NAME% could not be removed. Close the running app and retry. >&2
  goto :fail
)
mkdir dist 2>nul

set "ICON_ARG="
if exist "packaging\codex-desktop.ico" set "ICON_ARG=--icon packaging\codex-desktop.ico"

set "WIN_ARGS="
if not "%TYPE%"=="app-image" set "WIN_ARGS=--win-dir-chooser --win-menu --win-shortcut --win-per-user-install"

echo ==^> Running jpackage (type: %TYPE%)
"%JPACKAGE%" --type %TYPE% --name "%APP_NAME%" --app-version %APP_VERSION% ^
  --vendor CodexDesktop ^
  --description "JavaFX desktop client for the Codex CLI running in WSL" ^
  --input target\app --main-jar "%MAIN_JAR%" --main-class %MAIN_CLASS% ^
  --dest dist %ICON_ARG% %WIN_ARGS% ^
  --add-modules java.base,java.desktop,java.logging,java.naming,java.management,java.prefs,java.scripting,java.xml,jdk.unsupported,jdk.crypto.ec ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "-Dprism.lcdtext=false" ^
  --java-options "-Xmx512m"
if errorlevel 1 goto :fail

echo.
echo ==^> Done
if "%TYPE%"=="app-image" (
  echo     dist\%APP_NAME%\%APP_NAME%.exe
) else (
  dir /b dist
)
popd
exit /b 0

:fail
echo Build failed >&2
popd
exit /b 1
