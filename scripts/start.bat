@echo off
if not "%1"=="max" start /max cmd.exe /c "%~f0 max" & exit /b
title V2ray Server Tester
cd /d "%~dp0"

set "JAR=%~dp0v2ray-tester-1.0.0.jar"

rem --- Check that Java is installed ---
where java >nul 2>nul
if errorlevel 1 goto nojava

rem --- Check that the jar is next to this script ---
if not exist "%JAR%" goto nojar

rem --- Start the application ---
java -jar "%JAR%"
echo.
echo Application finished (exit code %errorlevel%).
echo.
pause
exit /b 0

:nojava
echo [ERROR] Java was not found on this computer.
echo.
echo This tool requires a Java 8 or newer runtime (JRE).
echo You can download a free JRE here:
echo.
echo     https://adoptium.net/temurin/releases/
echo.
echo After installing Java, run this script again.
echo.
pause
exit /b 1

:nojar
echo [ERROR] The application jar was not found in this folder:
echo     %JAR%
echo.
echo Please keep the .jar file and this .bat file in the same folder.
echo.
pause
exit /b 1
