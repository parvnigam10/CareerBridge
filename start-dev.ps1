$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw "Install a JDK (17 or newer) and reopen PowerShell." }
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw "Install Node.js 24 LTS and reopen PowerShell." }
if (-not (Test-Path (Join-Path $projectRoot "frontend/node_modules"))) {
    Push-Location (Join-Path $projectRoot "frontend")
    try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw "npm ci failed." } } finally { Pop-Location }
}
$backendPath = (Join-Path $projectRoot "backend").Replace("'", "''")
$frontendPath = (Join-Path $projectRoot "frontend").Replace("'", "''")
Start-Process powershell.exe -ArgumentList @("-NoExit", "-Command", "Set-Location '$backendPath'; .\mvnw.cmd spring-boot:run")
Start-Process powershell.exe -ArgumentList @("-NoExit", "-Command", "Set-Location '$frontendPath'; npm.cmd run dev")
Write-Host "Two terminals opened. When both servers are ready, open http://localhost:5173"
Write-Host "Demo password: Demo@12345. Stop either server with Ctrl+C in its terminal."
