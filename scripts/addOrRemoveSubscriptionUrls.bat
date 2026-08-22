@echo off
setlocal

REM Change to the folder where this script is located
cd /d "%~dp0"

set "jar_name=v2ray-tester-1.0.0.jar"

:select_operation
echo.
echo Select operation:
echo    add    - Add subscriptions
echo    remove - Remove subscriptions
set "operation="
set /p "operation=Enter 'add' or 'remove': "

if /i "%operation%"=="add" (
    set "action=--add"
    goto operation_done
)
if /i "%operation%"=="remove" (
    set "action=--remove"
    goto operation_done
)

echo Invalid choice. Please type 'add' or 'remove'.
goto select_operation

:operation_done

REM Ask for text file
set "default_file=%~dp0subscription_list.txt"
set "input_file="
set /p "input_file=Enter text file path [%default_file%]: "
if not defined input_file set "input_file=%default_file%"

REM Check if the text file exists
if not exist "%input_file%" (
    echo Error: File "%input_file%" not found.
    exit /b 1
)

REM Check if test.jar exists in the script folder
if not exist "%~dp0%jar_name%" (
    echo Error: %jar_name% not found in script folder.
    exit /b 1
)

echo.
echo Running with action: %action%
echo Using file: "%input_file%"
echo.

REM Iterate over each line in the text file
for /f "usebackq delims= eol=" %%i in ("%input_file%") do (
    echo Processing: %action% "%%i"
    java -jar "%~dp0%jar_name%" %action% "%%i"
)

echo.
echo Done.
endlocal