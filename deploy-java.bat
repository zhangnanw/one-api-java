@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

:: Set console to UTF-8 to avoid garbled output
chcp 65001 >nul 2>&1

echo ========================================
echo   one-api-java Atomic Deploy
echo ========================================

:: Check local JDK, auto-install if missing
set "JAVA_EXE=%~dp0jdk\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo [Auto] JDK not found, running init.bat...
    call "%~dp0init.bat"
    if errorlevel 1 (
        echo [ERROR] init.bat failed.
        exit /b 1
    )
)
set "JAVA_HOME=%~dp0jdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Kill existing instance by PID
echo [1/3] Killing existing instance...
if exist .pid (
    set /p OLD_PID=<.pid
    taskkill /F /PID !OLD_PID! >nul 2>&1
    del .pid
    echo        Killed PID !OLD_PID!
) else (
    echo        No previous instance.
)

:: Build
echo [2/3] Building...
if exist "mvnw.cmd" (
    call mvnw.cmd package -DskipTests -q
) else (
    call mvn package -DskipTests -q
)
if errorlevel 1 (
    echo        BUILD FAILED
    exit /b 1
)
echo        Build OK.

:: Deploy & start
echo [3/3] Starting on port 13000...
for /f %%i in ('powershell -Command "$p=Start-Process -FilePath '%JAVA_EXE%' -ArgumentList '-Dfile.encoding=UTF-8','-jar','target\one-api-java-1.0.0.jar' -PassThru -NoNewWindow; $p.Id"') do set PID=%%i
echo !PID!>.pid
echo        PID !PID! saved.

:: Verify port (retry up to 10s)
set RETRY=0
:wait_port
timeout /t 1 >nul 2>&1
curl -sf http://127.0.0.1:13000/api/status >nul 2>&1
if not errorlevel 1 goto port_ok
set /a RETRY+=1
if !RETRY! lss 10 goto wait_port
echo        STARTUP FAILED - port 13000 not responding after 10s
exit /b 1

:port_ok
echo        Ready (verified in !RETRY!s).

echo.
echo ========================================
echo   Done. Verify:
echo     curl http://127.0.0.1:13000/api/status
echo ========================================
