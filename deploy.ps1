param(
    [switch]$SkipTests,
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

function Log  { Write-Host "[DEPLOY] $args" -ForegroundColor Green }
function Warn { Write-Host "[WARN]  $args" -ForegroundColor Yellow }
function Err  { Write-Host "[ERROR] $args" -ForegroundColor Red; exit 1 }

$DeployDir  = "$env:USERPROFILE\.one-api"
$JarName    = "one-api-java.jar"
$JarSrc     = "$ScriptDir\target\one-api-java-1.0.0.jar"
$JarDst     = "$DeployDir\$JarName"
$LogFile    = "$DeployDir\server.log"
$HealthUrl  = "http://localhost:13000/api/status"
$Port       = 13000

# ======== Step 1: Build ========
if (-not $NoBuild) {
    Log "Step 1/4: Build & package..."

    $testFlag = if ($SkipTests) { "-DskipTests" } else { "" }
    $mvnArgs = @("clean", "package", $testFlag, "--batch-mode") | Where-Object { $_ }

    & "$ScriptDir\mvnw.cmd" $mvnArgs
    if ($LASTEXITCODE -ne 0) { Err "Build failed" }

    if (-not (Test-Path $JarSrc)) { Err "JAR not found: $JarSrc" }
    $jarMB = [math]::Round((Get-Item $JarSrc).Length / 1MB, 1)
    Log "Build OK ($jarMB MB)"
}

# ======== Step 2: Stop old process ========
Log "Step 2/4: Stop old process..."

$stopped = $false

# Method 1: find java process whose working dir is .one-api
$oldProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object {
        $_.CommandLine -and $_.CommandLine -like "*one-api-java.jar*"
    }

if ($oldProcs) {
    foreach ($p in $oldProcs) {
        Log "Stopping PID $($p.ProcessId)..."
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    $stopped = $true
}

# Method 2: check port 13000
if (-not $stopped) {
    $portPid = (netstat -ano 2>$null |
        Select-String ":$Port .*LISTENING" |
        ForEach-Object { ($_ -split '\s+')[-1] } |
        Select-Object -First 1)
    if ($portPid) {
        Log "Stopping PID $portPid (port $Port)..."
        Stop-Process -Id $portPid -Force -ErrorAction SilentlyContinue
        $stopped = $true
    }
}

if ($stopped) { Start-Sleep -Seconds 3 }
Log "Old process cleared"

# ======== Step 3: Copy JAR ========
Log "Step 3/4: Deploy JAR..."

New-Item -ItemType Directory -Force -Path $DeployDir | Out-Null

# Backup old JAR
if (Test-Path $JarDst) {
    $bak = "$JarDst.bak.$(Get-Date -Format 'yyyyMMddHHmmss')"
    Copy-Item $JarDst $bak -Force
    Log "Backed up old JAR -> $bak"
}

Copy-Item $JarSrc $JarDst -Force
Log "JAR copied -> $JarDst"

# ======== Step 4: Start & health check ========
Log "Step 4/4: Start service..."

$proc = Start-Process -FilePath "java" `
    -ArgumentList "-Dfile.encoding=UTF-8", "-jar", $JarName `
    -WorkingDirectory $DeployDir `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err" `
    -WindowStyle Hidden `
    -PassThru

Log "Started (PID: $($proc.Id))"
Log "Waiting for startup..."

$healthy = $false
for ($i = 1; $i -le 15; $i++) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        Err "Process exited early (code $($proc.ExitCode)). Log:`n$(Get-Content $LogFile -Tail 20 -ErrorAction SilentlyContinue)"
    }
    try {
        $resp = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3
        if ($resp.StatusCode -eq 200) { $healthy = $true; break }
    } catch {
        Write-Host "." -NoNewline
    }
}
Write-Host ""

if ($healthy) {
    Log "Health check OK (HTTP 200)"
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  Deploy Complete" -ForegroundColor Green
    Write-Host "  URL: $HealthUrl" -ForegroundColor Green
    Write-Host "  Log: $LogFile" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
} else {
    Err "Health check failed after 30s. Log:`n$(Get-Content $LogFile -Tail 20 -ErrorAction SilentlyContinue)"
}
