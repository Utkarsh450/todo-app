<#
.SYNOPSIS
  Deploys the backend to Vercel (project "paper-todo-api") and checks that the live API behaves.

.DESCRIPTION
  1. Preflight: node, pnpm and the Vercel CLI are installed, you are logged in, backend/ is linked to the project.
  2. Checks: pnpm install, Prisma client, typecheck (and the tests with -RunTests).
  3. Asks "deploy to PRODUCTION?" (skip with -Yes), then runs `vercel deploy --prod`.
     Vercel builds remotely: `pnpm prisma generate && pnpm prisma migrate deploy && pnpm build` (see backend/vercel.json),
     so DATABASE_URL, API_KEY and the XAI_* variables must already be set in the Vercel project settings.
  4. Verifies the live API: /health is 200, a request without the key is 401, a request with the key is 200.
     The key is read from the PAPER_API_KEY environment variable and is never printed. Without it the last check is skipped.

.EXAMPLE
  .\scripts\deploy-backend.ps1 -DryRun      # show the plan, change nothing
  .\scripts\deploy-backend.ps1              # checks, ask, deploy, verify
  $env:PAPER_API_KEY = '...'; .\scripts\deploy-backend.ps1 -Yes

.NOTES
  One-time setup:  npm i -g vercel ; vercel login ; cd backend ; vercel link   (choose the project paper-todo-api)
  Rotating the API key means setting a new API_KEY in Vercel, deploying, and building the app with the same value
  (./gradlew :app:assembleR8check -PapiKey=...). Every installed APK with the old key stops working.
#>
[CmdletBinding()]
param(
    [switch]$Yes,
    [switch]$RunTests,
    [switch]$SkipChecks,
    [switch]$DryRun,
    [string]$ApiBase = 'https://paper-todo-api.vercel.app/api'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repo 'backend'

function Step([string]$text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }
function Fail([string]$text) { Write-Host "ERROR: $text" -ForegroundColor Red; exit 1 }

function Need([string]$command, [string]$hint) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { Fail "'$command' was not found. $hint" }
}

# Runs a command (or only says what it would run with -DryRun) and stops on a non-zero exit code.
function Run([string]$description, [scriptblock]$command) {
    if ($DryRun) { Write-Host "[dry-run] $description"; return }
    $global:LASTEXITCODE = 0
    & $command
    if ($LASTEXITCODE -ne 0) { Fail "$description failed (exit code $LASTEXITCODE)." }
}

# The HTTP status code of a GET, 0 when the server could not be reached.
function Probe([string]$url, [hashtable]$headers = @{}) {
    try {
        return [int](Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 90).StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

Step 'Preflight'
Need 'node' 'Install Node 22 or newer.'
Need 'pnpm' 'Install it with: npm i -g pnpm'
Need 'vercel' 'Install it with: npm i -g vercel'
if (-not (Test-Path (Join-Path $backend '.vercel\project.json'))) {
    Fail "backend/ is not linked to a Vercel project. Run once:  cd backend ; vercel link   (choose paper-todo-api)"
}
$global:LASTEXITCODE = 0
$who = (vercel whoami 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $who) { Fail 'You are not logged in to Vercel. Run:  vercel login' }
Write-Host "Vercel account: $who"
$dirty = git -C $repo status --porcelain -- backend
if ($dirty) { Write-Host 'WARNING: backend/ has changes that are not committed. Vercel deploys the files on disk.' -ForegroundColor Yellow }

Push-Location $backend
$setPlaceholderDb = $false
try {
    if (-not $SkipChecks) {
        Step 'Checks'
        Run 'pnpm install --frozen-lockfile' { pnpm install --frozen-lockfile }
        # prisma.config.ts only needs the variable to exist; nothing connects to this address.
        if (-not $env:DATABASE_URL) { $env:DATABASE_URL = 'postgresql://localhost:5432/placeholder'; $setPlaceholderDb = $true }
        Run 'pnpm prisma:generate' { pnpm prisma:generate }
        Run 'pnpm typecheck' { pnpm typecheck }
        if ($RunTests) { Run 'pnpm test (needs the local paper_todo_test database)' { pnpm test } }
    }

    Step 'Deploy'
    if ($DryRun) {
        Write-Host '[dry-run] would ask for confirmation, then run: vercel deploy --prod'
    } else {
        if (-not $Yes) {
            $answer = Read-Host 'Deploy the backend to PRODUCTION on Vercel now? (y/N)'
            if ($answer -notmatch '^(y|yes)$') { Write-Host 'Cancelled. Nothing was deployed.'; exit 0 }
        }
        Run 'vercel deploy --prod' { vercel deploy --prod }
    }
} finally {
    if ($setPlaceholderDb) { Remove-Item Env:DATABASE_URL -ErrorAction SilentlyContinue }
    Pop-Location
}

Step "Verify $ApiBase"
if ($DryRun) {
    Write-Host '[dry-run] would check: /health is 200, no key is 401, with PAPER_API_KEY is 200'
    exit 0
}
$failed = $false
$health = Probe "$ApiBase/health"
Write-Host ("  health                {0}   (expected 200)" -f $health)
if ($health -ne 200) { $failed = $true }

$tasksUrl = "$ApiBase/tasks?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z"
$noKey = Probe $tasksUrl
Write-Host ("  without the key       {0}   (expected 401)" -f $noKey)
if ($noKey -ne 401) { $failed = $true }

if ($env:PAPER_API_KEY) {
    $withKey = Probe $tasksUrl @{ 'X-Api-Key' = $env:PAPER_API_KEY }
    Write-Host ("  with PAPER_API_KEY    {0}   (expected 200)" -f $withKey)
    if ($withKey -ne 200) { $failed = $true }
} else {
    Write-Host '  with the key          skipped (set PAPER_API_KEY to check it)' -ForegroundColor Yellow
}

if ($failed) { Fail 'The live API did not answer as expected. Check the deployment logs:  vercel logs --prod' }
Write-Host ''
Write-Host 'Done: the live API answers as expected.' -ForegroundColor Green
