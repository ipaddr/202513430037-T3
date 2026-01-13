@echo off
echo ================================================================================
echo   TESTING BUILD AFTER FIX
echo ================================================================================
echo.

cd /d "%~dp0"

echo [1/1] Building debug APK...
call gradlew assembleDebug --warning-mode=none

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================================================
    echo   BUILD SUCCESS! All syntax errors are fixed.
    echo ================================================================================
    echo.
    echo Next step: Run build_and_debug_rental.bat to test
    echo.
) else (
    echo.
    echo ================================================================================
    echo   BUILD FAILED! Check errors above.
    echo ================================================================================
    echo.
)

pause

