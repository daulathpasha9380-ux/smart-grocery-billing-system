param(
    [switch]$Stop,
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [ValidateRange(30, 600)]
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'
$logsDirectory = Join-Path $env:LOCALAPPDATA 'FreshLedger\logs'

function Get-JavaMajorVersion([string]$javaPath) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $versionOutput = & $javaPath -version 2>&1 | Out-String
        $versionExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($versionExitCode -ne 0) {
        throw "Java version check failed with exit code $versionExitCode. Output: $versionOutput"
    }
    if ($versionOutput -notmatch 'version "(\d+)(?:\.(\d+))?') {
        throw "Could not determine the Java version from $javaPath. Output: $versionOutput"
    }

    $major = [int]$Matches[1]
    if ($major -eq 1 -and $Matches[2]) {
        $major = [int]$Matches[2]
    }
    return $major
}

function Get-AppListener {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Test-BillingLogin {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$Port/login" -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -eq 200 -and
            $response.Content.Contains('FreshLedger') -and
            $response.Content.Contains('Sign In')
    } catch {
        return $false
    }
}

function Show-ListenerOwner($listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)" `
        -ErrorAction SilentlyContinue
    if ($owner) {
        Write-Host "Port $Port is owned by PID $($owner.ProcessId): $($owner.Name)"
        Write-Host $owner.CommandLine
    } else {
        Write-Host "Port $Port is owned by PID $($listener.OwningProcess)."
    }
}

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven wrapper was not found at $mavenWrapper"
}

$jdk25Root = 'C:\Program Files\Eclipse Adoptium'
$jdk25 = Get-ChildItem -LiteralPath $jdk25Root -Directory -Filter 'jdk-25*' `
    -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
$javaCandidates = @()
if ($jdk25) {
    $javaCandidates += Join-Path $jdk25.FullName 'bin\java.exe'
}
if ($env:JAVA_HOME) {
    $javaCandidates += Join-Path $env:JAVA_HOME 'bin\java.exe'
}

$javaPath = $javaCandidates |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
    Select-Object -First 1
if (-not $javaPath) {
    throw 'No JDK 21 or newer was found. Install Temurin 25 or set JAVA_HOME to a supported JDK.'
}

$javaHome = Split-Path (Split-Path $javaPath -Parent) -Parent
$javaMajor = Get-JavaMajorVersion $javaPath
if ($javaMajor -lt 21) {
    throw "JDK $javaMajor was selected from $javaHome; this project requires JDK 21 or newer."
}

$env:JAVA_HOME = $javaHome
$env:Path = "$(Join-Path $javaHome 'bin');$env:Path"

New-Item -ItemType Directory -Path $logsDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stdoutLog = Join-Path $logsDirectory "billing-$timestamp.out.log"
$stderrLog = Join-Path $logsDirectory "billing-$timestamp.err.log"

Write-Host "Project: $projectRoot"
Write-Host "Java: $javaPath (JDK $javaMajor)"
$wrapperVersion = & $mavenWrapper -version 2>&1
if ($LASTEXITCODE -ne 0) {
    $wrapperVersion | ForEach-Object { Write-Host $_ }
    throw "Maven wrapper version check failed with exit code $LASTEXITCODE."
}
$wrapperVersion | ForEach-Object { Write-Host $_ }

$listener = Get-AppListener
if ($Stop) {
    if (-not $listener) {
        Write-Host "No application is listening on port $Port."
        exit 0
    }
    if (-not (Test-BillingLogin)) {
        Show-ListenerOwner $listener
        throw 'The port is occupied by a service that does not identify as this billing application; it was not stopped.'
    }

    Write-Host 'Requesting graceful shutdown through the Spring Boot Maven plugin...'
    & $mavenWrapper spring-boot:stop
    if ($LASTEXITCODE -ne 0) {
        Show-ListenerOwner $listener
        throw 'Graceful shutdown was not confirmed. This instance was not started by the managed Spring Boot start goal; stop it from the terminal that launched it.'
    }

    $stopDeadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Seconds 1
        $listener = Get-AppListener
    } while ($listener -and (Get-Date) -lt $stopDeadline)

    if ($listener) {
        Show-ListenerOwner $listener
        throw 'The application did not release its port after the shutdown request.'
    }
    Write-Host 'Application stopped and port released.'
    exit 0
}

if ($listener) {
    if (Test-BillingLogin) {
        Write-Host "The billing application is already responding at http://localhost:$Port. No duplicate was started."
        Show-ListenerOwner $listener
        exit 0
    }
    Show-ListenerOwner $listener
    throw "Port $Port is occupied by another service. No application was started."
}

$arguments = '/d /c call "{0}" -Dspring-boot.run.arguments=--server.port={1} spring-boot:start' `
    -f $mavenWrapper, $Port
$launcher = Start-Process -FilePath $env:ComSpec -ArgumentList $arguments `
    -WorkingDirectory $projectRoot -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog -WindowStyle Hidden -PassThru
Write-Host "Maven startup logs: $stdoutLog"
Write-Host "Maven error log: $stderrLog"

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    if (Test-BillingLogin) {
        $listener = Get-AppListener
        $applicationPid = if ($listener) { $listener.OwningProcess } else { 'unknown' }
        Write-Host "Application is responding at http://localhost:$Port (PID $applicationPid)."
        Write-Host "Logs: $stdoutLog"
        exit 0
    }

    $launcher.Refresh()
    if ($launcher.HasExited -and -not (Get-AppListener)) {
        break
    }
} while ((Get-Date) -lt $deadline)

Write-Host "Application did not become healthy at http://localhost:$Port."
Write-Host "Maven exit code: $($launcher.ExitCode)"
Write-Host "Startup log: $stdoutLog"
Write-Host "Error log: $stderrLog"
if (Test-Path -LiteralPath $stdoutLog) {
    Write-Host '--- Recent startup output ---'
    Get-Content -LiteralPath $stdoutLog -Tail 60
}
if (Test-Path -LiteralPath $stderrLog) {
    Write-Host '--- Recent error output ---'
    Get-Content -LiteralPath $stderrLog -Tail 60
}
exit 1
