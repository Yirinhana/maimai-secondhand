. (Join-Path $PSScriptRoot 'environment.ps1')
Set-Location -LiteralPath $ProjectRoot
Invoke-CheckedCommand $MavenCommand @('-B', '-ntp', "-Drevision=$ProductVersion", '-f', 'backend/pom.xml', 'verify')
Push-Location -LiteralPath (Join-Path $ProjectRoot 'frontend')
try {
    Invoke-CheckedCommand 'npm.cmd' @('test')
    Invoke-CheckedCommand 'npm.cmd' @('run', 'typecheck')
    Invoke-CheckedCommand 'npm.cmd' @('run', 'build')
} finally { Pop-Location }
