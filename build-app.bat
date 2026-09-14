@echo off
rem =====================================================================
rem  CodexDesktop - one-click build
rem
rem  Double-click this file. It finds a JDK 21 and Maven by itself, builds
rem  the app with a bundled Java runtime, and opens the output folder.
rem
rem  Optional argument:
rem     build-app.bat installer   -> single setup exe instead (needs WiX)
rem =====================================================================
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
title CodexDesktop build

set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=app-image"

set "PROJECT=%~dp0CodexDesktop"
if not exist "%PROJECT%\pom.xml" (
  echo [X] Could not find CodexDesktop\pom.xml next to this script.
  goto :fail
)

echo.
echo === 1/3  Looking for a JDK 21 =========================================

rem A running instance keeps its bundled runtime DLLs open, which makes the output
rem directory impossible to replace. Say so up front instead of failing in jpackage.
tasklist /fi "imagename eq CodexDesktop.exe" 2>nul | find /i "CodexDesktop.exe" >nul
if not errorlevel 1 (
  echo [X] CodexDesktop is still running.
  echo     Close the app window first, then run this file again.
  goto :fail
)

rem jpackage only ships with a full JDK, so its presence is the real test.
set "JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JDK=%JAVA_HOME%"

if not defined JDK for /d %%D in ("%USERPROFILE%\devtools\jdk-21*") do (
  if exist "%%D\bin\jpackage.exe" set "JDK=%%D"
)
if not defined JDK for /d %%D in (
  "C:\Program Files\Eclipse Adoptium\jdk-21*"
  "C:\Program Files\Java\jdk-21*"
  "C:\Program Files\Microsoft\jdk-21*"
  "C:\Program Files\Amazon Corretto\jdk21*"
  "C:\Program Files\Zulu\zulu-21*"
  "C:\Program Files\BellSoft\LibericaJDK-21*"
) do (
  if exist "%%D\bin\jpackage.exe" set "JDK=%%D"
)

if not defined JDK (
  echo [X] No JDK 21 with jpackage was found.
  echo     Install one, or unzip Temurin 21 to %USERPROFILE%\devtools\
  echo     and run this file again.
  goto :fail
)
echo     JDK: !JDK!
set "JAVA_HOME=!JDK!"

echo.
echo === 2/3  Looking for Maven ============================================

set "MVNCMD="
if defined MVN if exist "%MVN%" set "MVNCMD=%MVN%"
if not defined MVNCMD for /d %%D in ("%USERPROFILE%\devtools\apache-maven-*") do (
  if exist "%%D\bin\mvn.cmd" set "MVNCMD=%%D\bin\mvn.cmd"
)
if not defined MVNCMD for %%I in (mvn.cmd) do (
  if not "%%~$PATH:I"=="" set "MVNCMD=%%~$PATH:I"
)

if not defined MVNCMD (
  echo [X] Maven was not found.
  echo     Unzip Apache Maven to %USERPROFILE%\devtools\ or put mvn on PATH.
  goto :fail
)
echo     Maven: !MVNCMD!
set "MVN=!MVNCMD!"

echo.
echo === 3/3  Building (%TARGET%) ==========================================
echo     This takes a couple of minutes the first time.
echo.

pushd "%PROJECT%"
call "%PROJECT%\scripts\package-windows.cmd" %TARGET%
set "BUILD_RESULT=%ERRORLEVEL%"
popd

if not "%BUILD_RESULT%"=="0" (
  echo.
  echo [X] Build failed. The Maven output above says why.
  if /i "%TARGET%"=="installer" (
    echo     For the installer target the WiX Toolset must be on PATH:
    echo       winget install WiXToolset.WiX
  )
  goto :fail
)

echo.
echo =====================================================================
if /i "%TARGET%"=="app-image" (
  echo  Done:  %PROJECT%\dist\CodexDesktop\CodexDesktop.exe
  echo.
  echo  Copy the whole "dist\CodexDesktop" folder to move the app - the
  echo  bundled Java runtime lives next to the exe, so the exe alone
  echo  will not run.
  if exist "%PROJECT%\dist\CodexDesktop" start "" explorer "%PROJECT%\dist\CodexDesktop"
) else (
  echo  Done. Installer is in:  %PROJECT%\dist
  if exist "%PROJECT%\dist" start "" explorer "%PROJECT%\dist"
)
echo =====================================================================
echo.
call :hold
exit /b 0

:fail
echo.
call :hold
exit /b 1

rem Keeps the window open for a double-click, but not when a script drives this
rem file (set CODEXDESKTOP_NO_PAUSE=1 for that).
:hold
if not "%CODEXDESKTOP_NO_PAUSE%"=="" goto :eof
pause
goto :eof
