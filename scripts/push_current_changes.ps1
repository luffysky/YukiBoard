param(
    [string]$Message,
    [string]$Branch,
    [string]$Remote = 'origin',
    [switch]$NoCommit,
    [switch]$NoPush
)

$ErrorActionPreference = 'Stop'

Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $Branch) {
    $Branch = (git branch --show-current).Trim()
}

if (-not $Branch) {
    throw 'Unable to determine current git branch. Pass -Branch explicitly.'
}

if (-not $NoCommit -and [string]::IsNullOrWhiteSpace($Message)) {
    throw 'Commit message is required unless -NoCommit is used.'
}

git add -A

$stagedStatus = git diff --cached --name-only
$hasStagedChanges = -not [string]::IsNullOrWhiteSpace(($stagedStatus | Out-String).Trim())

if (-not $hasStagedChanges) {
    Write-Host 'No staged changes after git add -A.'
    if ($NoPush) {
        exit 0
    }
    Write-Host "Nothing to commit. Pushing current branch '$Branch' to '$Remote'."
    git push $Remote $Branch
    exit 0
}

if (-not $NoCommit) {
    git commit -m $Message
} else {
    Write-Host 'Skipping commit because -NoCommit was provided.'
}

if ($NoPush) {
    Write-Host 'Skipping push because -NoPush was provided.'
    exit 0
}

git push $Remote $Branch
