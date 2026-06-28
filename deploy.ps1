param([switch]$SkipTests)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

function Log   { Write-Host "[DEPLOY] $args" -ForegroundColor Green }
function Warn  { Write-Host "[WARN]  $args" -ForegroundColor Yellow }
function Err   { Write-Host "[ERROR] $args" -ForegroundColor Red }

# ======== Step 1: Detect JDK 17 ========
Log "Step 1/5: Detect JDK 17..."

$javaExe = $null
$javaHome = $null

$jdkRoots = @(
    "$env:ProgramFiles\Eclipse Adoptium",
    "$env:ProgramFiles\Java",
    "$env:ProgramFiles\OpenJDK",
    "$env:ProgramFiles\Microsoft",
    "$env:ProgramFiles\Zulu"
)
foreach ($root in $jdkRoots) {
    if (Test-Path $root) {
        $d = Get-ChildItem "$root\jdk-17*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
        if ($d) {
            $exe = "$($d.FullName)\bin\java.exe"
            if (Test-Path $exe) { $javaExe = $exe; $javaHome = $d.FullName; break }
        }
    }
}

if (-not $javaExe) {
    $jdkHome = "$env:USERPROFILE\.one-api\jdk17"
    $exe = "$jdkHome\bin\java.exe"
    if (Test-Path $exe) { $javaExe = $exe; $javaHome = $jdkHome }
}

if (-not $javaExe) {
    $jdkHome = "$env:USERPROFILE\.one-api\jdk17"
    $jdkZip  = "$jdkHome\jdk17.zip"

    $jdkUrls = @(
        @{Url="https://repo.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip"; Label="Huawei Cloud (~178MB)"},
        @{Url="https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"; Label="Adoptium (~180MB)"}
    )

    Warn "JDK 17 not found, downloading..."
    New-Item -ItemType Directory -Force -Path $jdkHome | Out-Null

    $downloaded = $false
    foreach ($src in $jdkUrls) {
        Log "Try: $($src.Label)"
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $wc = New-Object System.Net.WebClient
            $wc.DownloadFile($src.Url, $jdkZip)
            if ((Get-Item $jdkZip).Length -gt 1048576) {
                $downloaded = $true
                break
            }
        } catch {
            Warn "Download failed, trying next..."
        }
    }

    if (-not $downloaded) {
        Err "All download sources failed. Install JDK 17 manually: https://adoptium.net/download/"
        exit 1
    }

    Log "Extracting JDK 17..."
    Expand-Archive -Path $jdkZip -DestinationPath $jdkHome -Force
    Remove-Item $jdkZip

    $extracted = Get-ChildItem $jdkHome -Directory | Where-Object { $_.Name -like "jdk-17*" -or $_.Name -like "openjdk-17*" } | Select-Object -First 1
    if ($extracted) {
        Get-ChildItem $extracted.FullName | Move-Item -Destination $jdkHome -Force
        Remove-Item $extracted.FullName -Recurse -Force
    }

    $exe = "$jdkHome\bin\java.exe"
    if (Test-Path $exe) { $javaExe = $exe; $javaHome = $jdkHome } else {
        Err "java.exe not found after extraction"
        exit 1
    }
}

$verOutput = & $javaExe -version 2>&1 | Select-Object -First 1
Log $verOutput
Log "JAVA_HOME: $javaHome"

# ======== Step 2: Detect Maven ========
Log "Step 2/5: Detect Maven..."

$mavenHome = $null
$mavenDirs = @(
    "C:\BASIC_ENV\apache-maven-3.9.5",
    "C:\BASIC_ENV\apache-maven-3.9.2",
    "$env:ProgramFiles\apache-maven-3.9.5"
)
foreach ($d in $mavenDirs) {
    if ((Test-Path $d) -and (Test-Path "$d\bin\mvn.cmd")) {
        $mavenHome = $d
        break
    }
}
if (-not $mavenHome) {
    $mvnCmd = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
    if ($mvnCmd) { $mavenHome = Split-Path (Split-Path $mvnCmd) -Parent }
}
if (-not $mavenHome) {
    Err "Maven not found. Install to C:\BASIC_ENV\apache-maven-3.9.5"
    exit 1
}
Log "MAVEN_HOME: $mavenHome"

# ======== Step 3: Compile ========
Log "Step 3/5: Compile project..."

$testOpts = if ($SkipTests) { @("-DskipTests") } else { @("test") }
$classworldsJar = Get-ChildItem "$mavenHome\boot\plexus-classworlds-*.jar" | Select-Object -First 1

$compileArgs = @(
    "-classpath", $classworldsJar.FullName,
    "-Dclassworlds.conf=$mavenHome\bin\m2.conf",
    "-Dmaven.home=$mavenHome",
    "-Dmaven.multiModuleProjectDirectory=$ScriptDir",
    "-Dlibrary.jansi.path=$mavenHome\lib\jansi-native",
    "org.codehaus.plexus.classworlds.launcher.Launcher",
    "-f", "$ScriptDir\pom.xml",
    "-Dmaven.compiler.source=17",
    "-Dmaven.compiler.target=17",
    "-Dmaven.compiler.fork=false",
    "compile"
) + $testOpts + @("-q")

& $javaExe $compileArgs

if ($LASTEXITCODE -ne 0) { Err "Compile failed"; exit 1 }
Log "Compile OK"

# ======== Step 4: Package ========
Log "Step 4/5: Package shaded JAR..."

$packageArgs = @(
    "-classpath", $classworldsJar.FullName,
    "-Dclassworlds.conf=$mavenHome\bin\m2.conf",
    "-Dmaven.home=$mavenHome",
    "-Dmaven.multiModuleProjectDirectory=$ScriptDir",
    "-Dlibrary.jansi.path=$mavenHome\lib\jansi-native",
    "org.codehaus.plexus.classworlds.launcher.Launcher",
    "-f", "$ScriptDir\pom.xml",
    "-Dmaven.compiler.source=17",
    "-Dmaven.compiler.target=17",
    "-Dmaven.compiler.fork=false",
    "package", "shade:shade"
) + $testOpts + @("-q")

& $javaExe $packageArgs

if ($LASTEXITCODE -ne 0) { Err "Package failed"; exit 1 }

$jarSize = [math]::Round((Get-Item "target\one-api-java-1.0.0-shaded.jar").Length / 1MB)
Log ("Package OK (" + $jarSize + " MB)")

# ======== Step 5: Deploy ========
Log "Step 5/5: Deploy to ~\.one-api\..."

$deployDir = "$env:USERPROFILE\.one-api"
New-Item -ItemType Directory -Force -Path $deployDir | Out-Null

$jarPath = "$deployDir\one-api-java.jar"
if (Test-Path $jarPath) {
    $bak = "$deployDir\one-api-java.jar.bak.$(Get-Date -Format 'yyyyMMddHHmmss')"
    Copy-Item $jarPath $bak
    Log "Old JAR backed up: $bak"
}

Copy-Item "target\one-api-java-1.0.0-shaded.jar" $jarPath -Force

$configPath = "$deployDir\config.yaml"
if (-not (Test-Path $configPath)) {
    Warn "config.yaml not found, generating template..."
    @"
port: 13000
database:
  type: postgresql
  host: bj.xiaoceng.space
  port: 5432
  database: oneapi
  user: oneapi
  password: "CHANGE_ME"
models_path: ""
"@ | Out-File -FilePath $configPath -Encoding UTF8
    Warn "Edit $configPath to set database password"
}

# Stop old service
$oldPid = (netstat -ano 2>$null | Select-String ":13000 .*LISTENING" | ForEach-Object { ($_ -split '\s+')[-1] } | Select-Object -First 1)
if ($oldPid) {
    Log "Stopping old service (PID: $oldPid)..."
    Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $javaExe
$psi.Arguments = "-Dfile.encoding=UTF-8 -cp one-api-java.jar com.oneapi.Main"
$psi.WorkingDirectory = $deployDir
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
$proc | Out-Null

Start-Sleep -Seconds 5

if (-not $proc.HasExited) {
    Log ("Service started (PID: " + $proc.Id + ")")
    Log ("Log: " + $deployDir + "\server.log")

    try {
        $code = (Invoke-WebRequest -Uri "http://localhost:13000/v1/models" -TimeoutSec 5 -UseBasicParsing).StatusCode
        if ($code -eq 200) { Log ("Health check OK (HTTP " + $code + ")") }
        else { Warn ("Health check returned " + $code) }
    } catch { Warn ("Health check failed: " + $_.Exception.Message) }
} else {
    Err "Startup failed. Check log: $deployDir\server.log"
    Get-Content "$deployDir\server.log" -Tail 20
    exit 1
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  one-api-java Deploy Complete" -ForegroundColor Green
Write-Host "  URL: http://localhost:13000" -ForegroundColor Green
Write-Host ("  Log: " + $deployDir + "\server.log") -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green