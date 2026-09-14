. (Join-Path $PSScriptRoot 'environment.ps1')
Set-Location -LiteralPath $ProjectRoot
Invoke-CheckedCommand $MavenCommand @('-B', '-ntp', "-Drevision=$ProductVersion", '-f', 'backend/pom.xml', 'verify')
Push-Location -LiteralPath (Join-Path $ProjectRoot 'frontend')
try {
    Invoke-CheckedCommand 'npm.cmd' @('ci')
    Invoke-CheckedCommand 'npm.cmd' @('run', 'typecheck')
    Invoke-CheckedCommand 'npm.cmd' @('run', 'build')
} finally { Pop-Location }
$releasePath = Join-Path $ProjectRoot ('.local/releases/' + $ProductVersion + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $releasePath -ErrorAction Stop | Out-Null
$jar = Join-Path $ProjectRoot 'backend/target/maimai-backend.jar'
Copy-Item -LiteralPath $jar -Destination (Join-Path $releasePath 'backend.jar')
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'frontend/dist') -Destination (Join-Path $releasePath 'frontend') -Recurse
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'VERSION') -Destination $releasePath
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'deploy') -Destination $releasePath -Recurse
$releaseFiles = Get-ChildItem -LiteralPath $releasePath -File -Recurse | Sort-Object FullName
$releaseChecksums = foreach ($releaseFile in $releaseFiles) {
    [pscustomobject]@{
        path = $releaseFile.FullName.Substring($releasePath.Length + 1).Replace('\', '/')
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $releaseFile.FullName).Hash
        bytes = $releaseFile.Length
    }
}
$releaseChecksums | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $releasePath 'checksums.json') -Encoding utf8
$archivePath = $releasePath + '.zip'
Compress-Archive -Path (Join-Path $releasePath '*') -DestinationPath $archivePath -ErrorAction Stop
Write-Output "已生成本地候选交付目录：$releasePath（未部署）"
Write-Output "对应传输包：$archivePath"
