@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   one-api-java Init
echo   Downloads JDK 21 to .\jdk\ (self-contained)
echo ========================================

set "JDK_DIR=%~dp0jdk"
set "JDK_TGZ=jdk-21_windows-x64_bin.zip"
set "JDK_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6+7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.6_7.zip"

:: --- Step 1: Download JDK ---
if exist "%JDK_DIR%\bin\java.exe" (
    echo [1/3] JDK already exists at %JDK_DIR%
) else (
    echo [1/3] Downloading JDK 21 (Eclipse Temurin)...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%JDK_TGZ%' -UseBasicParsing}"
    if errorlevel 1 (
        echo ERROR: Download failed. Check network or JDK_URL.
        exit /b 1
    )

    echo [2/3] Extracting...
    mkdir "%JDK_DIR%" 2>nul
    powershell -Command "& {Expand-Archive -Path '%JDK_TGZ%' -DestinationPath '%JDK_DIR%' -Force}"
    if errorlevel 1 exit /b 1

    :: Move contents up one level (zip extracts to jdk-21.0.6+7/)
    for /d %%d in ("%JDK_DIR%\jdk-*") do (
        xcopy /E /Y "%%d\*" "%JDK_DIR%\" >nul
        rmdir /S /Q "%%d" >nul 2>&1
    )
    del "%JDK_TGZ%" 2>nul
)

:: --- Step 3: Verify & set environment ---
echo [3/3] Verifying JDK...
"%JDK_DIR%\bin\java" -version 2>&1 | findstr /C:"openjdk"
if errorlevel 1 (
    echo ERROR: JDK verification failed.
    exit /b 1
)

echo.
echo ========================================
echo   JDK ready: %JDK_DIR%
echo.
echo   To build & run one-api-java:
echo     deploy-java.bat
echo.
echo   Or manually:
echo     set JAVA_HOME=%JDK_DIR%
echo     set PATH=%%JAVA_HOME%%\bin;%%PATH%%
echo ========================================
