param(
    [Parameter(Position = 0)]
    [ValidateSet("init", "up", "down", "restart", "rebuild", "logs", "status", "health", "doctor", "backup", "restore", "config", "help")]
    [string] $Command = "help",

    [Parameter(Position = 1)]
    [string] $Target
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $Root ".env.local"
$ComposeFiles = @("-f", "compose.yaml", "-f", "compose.local.yaml")
$RequiredVars = @("DB_PASSWORD", "JWT_SECRET", "ADMIN_DEFAULT_PASSWORD", "SPRING_PROFILES_ACTIVE")
$FrontendUrl = "http://localhost:3001"
$BackendUrl = "http://localhost:8081"
$HealthUrl = "http://localhost:8081/actuator/health"
$EngineHostDir = Join-Path $Root "tools\classification-engine"
$EngineContainerDir = "/app/tools/classification-engine"
$EngineContainerPython = "/opt/engine-venv/bin/python"

function Write-Ok($Message) { Write-Host "[OK] $Message" -ForegroundColor Green }
function Write-Info($Message) { Write-Host "[..] $Message" -ForegroundColor Cyan }
function Write-Warn($Message) { Write-Host "[!!] $Message" -ForegroundColor Yellow }
function Write-Fail($Message) { throw $Message }

function New-Secret {
    param([int] $Bytes = 48)
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToBase64String($buffer)
}

function Set-EnvValue {
    param([string] $Text, [string] $Name, [string] $Value)
    $escaped = [regex]::Escape($Name)
    return [regex]::Replace($Text, "(?m)^$escaped=.*$", "$Name=$Value")
}

function Read-EnvFile {
    param([string] $Path)
    $result = @{}
    if (-not (Test-Path $Path)) { return $result }
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }
        $parts = $trimmed.Split("=", 2)
        $result[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $result
}

function Get-ComposeArgs {
    param([switch] $AllowMissingEnv)
    $args = @("compose") + $ComposeFiles
    if (Test-Path $EnvFile) {
        $args += @("--env-file", ".env.local")
    } elseif (-not $AllowMissingEnv) {
        Write-Fail ".env.local is missing. Copy .env.local.example to .env.local and fill required values."
    }
    return $args
}

function Invoke-Docker {
    param([string[]] $Arguments)
    Push-Location $Root
    try {
        & docker @Arguments
        if ($LASTEXITCODE -ne 0) { Write-Fail "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Test-Docker {
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) { Write-Fail "Docker Desktop is not running or is not reachable." }
    Write-Ok "Docker is running"
}

function Test-Env {
    if (-not (Test-Path $EnvFile)) {
        Write-Fail ".env.local is missing. Copy .env.local.example to .env.local."
    }
    $env = Read-EnvFile $EnvFile
    foreach ($name in $RequiredVars) {
        if (-not $env.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($env[$name])) {
            Write-Fail "$name is required in .env.local"
        }
    }
    if ($env["SPRING_PROFILES_ACTIVE"] -ne "dev") {
        Write-Fail "Local Docker must use SPRING_PROFILES_ACTIVE=dev"
    }
    Write-Ok ".env.local is present and required variables are set"
}

function Invoke-Init {
    if (Test-Path $EnvFile) {
        Write-Ok ".env.local already exists; leaving it unchanged"
        return
    }
    $example = Join-Path $Root ".env.local.example"
    if (-not (Test-Path $example)) {
        Write-Fail ".env.local.example is missing"
    }
    $content = Get-Content -LiteralPath $example -Raw
    $content = Set-EnvValue $content "JWT_SECRET" (New-Secret 48)
    Set-Content -LiteralPath $EnvFile -Value $content -NoNewline
    Write-Ok "Created .env.local from .env.local.example with a generated JWT_SECRET"
    Write-Warn ".env.local is ignored by Git; do not commit real secrets"
}

function Test-Port {
    param([int] $Port, [string] $Name)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) {
        Write-Ok "$Name port $Port is available"
        return
    }
    $containers = & docker ps --filter "publish=$Port" --format "{{.Names}}" 2>$null
    if ($containers -match "^waad-local-") {
        Write-Ok "$Name port $Port is already owned by WAAD local containers"
        return
    }
    Write-Fail "$Name port $Port is already in use. Stop the process using it, then retry."
}

function Write-PortStatus {
    param([int] $Port, [string] $Name)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) {
        Write-Ok "$Name port $Port available"
        return
    }
    $containers = & docker ps --filter "publish=$Port" --format "{{.Names}}" 2>$null
    if ($containers -match "^waad-local-") {
        Write-Ok "$Name port $Port occupied by WAAD local container"
        return
    }
    if ($Port -eq 5433 -and ($containers -match "^waad-postgres-dev$")) {
        Write-Ok "$Name port $Port occupied by waad-postgres-dev"
        return
    }
    Write-Warn "$Name port $Port occupied by another process"
}

function Ensure-DevDatabase {
    $exists = & docker inspect waad-postgres-dev --format "{{.Name}}" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($exists)) {
        Write-Fail "Required local database container waad-postgres-dev was not found. This script will not create a replacement database."
    }
    $state = & docker inspect waad-postgres-dev --format "{{.State.Status}}"
    if ($state -ne "running") {
        Write-Info "Starting waad-postgres-dev"
        Invoke-Docker -Arguments @("start", "waad-postgres-dev")
    }
    for ($i = 1; $i -le 30; $i++) {
        & docker exec waad-postgres-dev pg_isready -U postgres -d tba_waad_system *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "waad-postgres-dev is accepting connections"
            return
        }
        Start-Sleep -Seconds 2
    }
    Write-Fail "waad-postgres-dev did not become healthy in time"
}

function Wait-Http {
    param([string] $Name, [string] $Url, [int] $Tries = 60)
    Write-Info "Waiting for $Name at $Url"
    for ($i = 1; $i -le $Tries; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Ok "$Name is reachable"
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    Write-Fail "$Name did not become reachable: $Url"
}

function Test-EngineHostFiles {
    if (Test-Path $EngineHostDir) { Write-Ok "classification engine host folder present: tools\classification-engine" } else { Write-Fail "classification engine host folder missing: tools\classification-engine" }
    foreach ($file in @("classify_json.py", "tpa_service_mapper.py", "ingest.py", "medical_synonyms.json", "odoo_knowledge.json", "requirements.txt")) {
        $path = Join-Path $EngineHostDir $file
        if (Test-Path $path) { Write-Ok "classification engine file present: $file" } else { Write-Fail "classification engine file missing: $file" }
    }
}

function Test-EngineContainer {
    $backend = & docker ps --filter "name=waad-local-backend" --format "{{.Names}}" 2>$null
    if ($backend -notmatch "^waad-local-backend$") {
        Write-Warn "waad-local-backend is not running; skipping in-container engine checks"
        return
    }
    & docker exec waad-local-backend test -f "$EngineContainerDir/classify_json.py" *> $null
    if ($LASTEXITCODE -eq 0) { Write-Ok "backend container can see $EngineContainerDir/classify_json.py" } else { Write-Fail "backend container cannot see $EngineContainerDir/classify_json.py" }

    $pythonVersion = & docker exec waad-local-backend $EngineContainerPython --version 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Ok "backend container Python ready: $pythonVersion" } else { Write-Fail "backend container Python is not executable at $EngineContainerPython" }

    & docker exec waad-local-backend $EngineContainerPython "$EngineContainerDir/classify_json.py" --help *> $null
    if ($LASTEXITCODE -eq 0) { Write-Ok "classify_json.py --help executes inside backend container" } else { Write-Fail "classify_json.py --help failed inside backend container" }
}

function Test-EngineHealthEndpoint {
    if (-not (Test-Path $EnvFile)) {
        Write-Warn ".env.local missing; skipping authenticated engine health endpoint check"
        return
    }
    $env = Read-EnvFile $EnvFile
    if (-not $env.ContainsKey("ADMIN_DEFAULT_PASSWORD") -or [string]::IsNullOrWhiteSpace($env["ADMIN_DEFAULT_PASSWORD"])) {
        Write-Warn "ADMIN_DEFAULT_PASSWORD missing; skipping authenticated engine health endpoint check"
        return
    }

    $loginFile = Join-Path ([System.IO.Path]::GetTempPath()) ("waad-login-" + [guid]::NewGuid().ToString() + ".json")
    try {
        @{ identifier = "superadmin@tba.sa"; password = $env["ADMIN_DEFAULT_PASSWORD"] } |
            ConvertTo-Json -Compress |
            Set-Content -LiteralPath $loginFile -Encoding UTF8

        $loginRaw = & curl.exe -s -H "Content-Type: application/json" -X POST "$BackendUrl/api/v1/auth/login" --data-binary "@$loginFile"
        $login = $loginRaw | ConvertFrom-Json
        $token = $login.data.token
        if ([string]::IsNullOrWhiteSpace($token)) { $token = $login.data.accessToken }
        if ([string]::IsNullOrWhiteSpace($token)) { Write-Fail "Could not authenticate for engine health endpoint" }

        $engineRaw = & curl.exe -s -H "Authorization: Bearer $token" "$BackendUrl/api/v1/classification/imports/engine/health"
        $engine = $engineRaw | ConvertFrom-Json
        if ($engine.status -eq "success") {
            Write-Ok "classification engine health endpoint READY"
        } else {
            $message = if ($engine.message) { $engine.message } else { $engineRaw }
            Write-Fail "classification engine health endpoint is not ready: $message"
        }
    } finally {
        Remove-Item -LiteralPath $loginFile -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Health {
    Wait-Http "Backend health" $HealthUrl
    Wait-Http "Frontend" $FrontendUrl
    Test-EngineHealthEndpoint
}

function Show-Urls {
    Write-Host ""
    Write-Ok "WAAD local Docker is ready"
    Write-Host "Frontend:       $FrontendUrl"
    Write-Host "Backend:        $BackendUrl"
    Write-Host "Backend health: $HealthUrl"
}

function Invoke-Up {
    Test-Docker
    Test-Env
    Test-Port 3001 "Frontend"
    Test-Port 8081 "Backend"
    Ensure-DevDatabase
    $services = Invoke-Docker ((Get-ComposeArgs) + @("config", "--services"))
    Invoke-Docker ((Get-ComposeArgs) + @("up", "-d", "--build", "backend", "frontend"))
    Wait-Http "Backend health" $HealthUrl
    Wait-Http "Frontend" $FrontendUrl 30
    Show-Urls
}

function Invoke-Down {
    Test-Docker
    Invoke-Docker ((Get-ComposeArgs -AllowMissingEnv) + @("down", "--remove-orphans"))
    Write-Ok "Stopped WAAD local app containers. waad-postgres-dev was not stopped and no volumes were removed."
}

function Invoke-Rebuild {
    Test-Docker
    Test-Env
    Ensure-DevDatabase
    $service = if ([string]::IsNullOrWhiteSpace($Target)) { "" } else { $Target.ToLowerInvariant() }
    if ($service -and $service -notin @("backend", "frontend")) {
        Write-Fail "Unknown rebuild target '$Target'. Use backend, frontend, or omit target for all."
    }
    $buildArgs = (Get-ComposeArgs) + @("build")
    if ($service) { $buildArgs += $service }
    Invoke-Docker $buildArgs
    $upArgs = (Get-ComposeArgs) + @("up", "-d")
    if ($service) { $upArgs += $service } else { $upArgs += @("backend", "frontend") }
    Invoke-Docker $upArgs
    Wait-Http "Backend health" $HealthUrl
    Wait-Http "Frontend" $FrontendUrl 30
}

function Invoke-Backup {
    Test-Docker
    Ensure-DevDatabase
    $dir = Join-Path $Root "deployment\backups\files"
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $file = Join-Path $dir "waad-local-$stamp.dump"
    $tmp = "/tmp/waad-local-$stamp.dump"
    Invoke-Docker -Arguments @("exec", "waad-postgres-dev", "pg_dump", "-U", "postgres", "-d", "tba_waad_system", "-Fc", "-f", $tmp)
    Invoke-Docker -Arguments @("cp", "waad-postgres-dev:$tmp", $file)
    Invoke-Docker -Arguments @("exec", "waad-postgres-dev", "rm", "-f", $tmp)
    Write-Ok "Backup created: $file"
}

function Invoke-Restore {
    if ([string]::IsNullOrWhiteSpace($Target)) { Write-Fail "Usage: .\waad.ps1 restore <backup-file>" }
    $path = Resolve-Path $Target
    Write-Warn "Restore will overwrite data in waad-postgres-dev/tba_waad_system."
    $confirm = Read-Host "Type RESTORE to continue"
    if ($confirm -ne "RESTORE") { Write-Fail "Restore cancelled" }
    Test-Docker
    Ensure-DevDatabase
    $tmp = "/tmp/waad-restore.dump"
    Invoke-Docker -Arguments @("cp", $path.Path, "waad-postgres-dev:$tmp")
    Invoke-Docker -Arguments @("exec", "waad-postgres-dev", "pg_restore", "--clean", "--if-exists", "--no-owner", "-U", "postgres", "-d", "tba_waad_system", $tmp)
    Invoke-Docker -Arguments @("exec", "waad-postgres-dev", "rm", "-f", $tmp)
    Write-Ok "Restore completed"
}

function Invoke-Doctor {
    Write-Host "WAAD local Docker doctor"
    try { Test-Docker } catch { Write-Warn $_.Exception.Message }
    $composeVersion = & docker compose version 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Ok $composeVersion } else { Write-Warn "Docker Compose is not available" }
    foreach ($file in @("compose.yaml", "compose.local.yaml", ".env.local", "backend\Dockerfile", "frontend\Dockerfile")) {
        if (Test-Path (Join-Path $Root $file)) { Write-Ok "$file present" } else { Write-Warn "$file missing" }
    }
    try { Test-EngineHostFiles } catch { Write-Warn $_.Exception.Message }
    if (Test-Path $EnvFile) {
        $env = Read-EnvFile $EnvFile
        foreach ($name in $RequiredVars) {
            if ($env.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace($env[$name])) { Write-Ok "$name set" } else { Write-Warn "$name missing" }
        }
        Write-Host "Profile: $($env["SPRING_PROFILES_ACTIVE"])"
    }
    Write-PortStatus 3001 "Frontend"
    Write-PortStatus 8081 "Backend"
    Write-PortStatus 5433 "Database"
    & docker inspect waad-postgres-dev --format "waad-postgres-dev: {{.State.Status}}" 2>$null
    & docker ps --filter "name=waad-local-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>$null
    try { Test-EngineContainer } catch { Write-Warn $_.Exception.Message }
    try { Wait-Http "Backend health" $HealthUrl 2 } catch { Write-Warn "Backend health unavailable" }
    try { Wait-Http "Frontend" $FrontendUrl 2 } catch { Write-Warn "Frontend unavailable" }
    try { Test-EngineHealthEndpoint } catch { Write-Warn $_.Exception.Message }
    $branch = & git branch --show-current 2>$null
    $commit = & git rev-parse --short HEAD 2>$null
    if ($LASTEXITCODE -eq 0) { Write-Host "Git: $branch @ $commit" }
    Write-Host "Remediation: run .\waad.ps1 up, check .env.local, free ports 3001/8081, and ensure waad-postgres-dev exists."
}

switch ($Command) {
    "init" { Invoke-Init }
    "up" { Invoke-Up }
    "down" { Invoke-Down }
    "restart" { Invoke-Down; Invoke-Up }
    "rebuild" { Invoke-Rebuild }
    "logs" { Invoke-Docker ((Get-ComposeArgs -AllowMissingEnv) + @("logs", "-f") + @($Target | Where-Object { $_ })) }
    "status" { Invoke-Docker ((Get-ComposeArgs -AllowMissingEnv) + @("ps")); & docker ps --filter "name=waad-postgres-dev" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" }
    "health" { Invoke-Health }
    "doctor" { Invoke-Doctor }
    "backup" { Invoke-Backup }
    "restore" { Invoke-Restore }
    "config" { Invoke-Docker ((Get-ComposeArgs) + @("config", "-q")); Write-Ok "Local Compose config is valid" }
    default {
        Write-Host "Usage: .\waad.ps1 init|up|down|restart|rebuild [backend|frontend]|logs [service]|status|health|doctor|backup|restore <file>|config"
    }
}
