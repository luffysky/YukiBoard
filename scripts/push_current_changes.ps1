param(
    [Parameter(Mandatory = $true)]
    [string]$Message
)

$ErrorActionPreference = 'Stop'

Set-Location (Split-Path -Parent $PSScriptRoot)

git add -A

$status = git status --short
if (-not $status) {
    Write-Host 'No changes to commit.'
    exit 0
}

git commit -m $Message
git push origin main
