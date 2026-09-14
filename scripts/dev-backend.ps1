. (Join-Path $PSScriptRoot 'environment.ps1')
Set-Location -LiteralPath $ProjectRoot
Invoke-CheckedCommand $MavenCommand @('-B', '-ntp', "-Drevision=$ProductVersion", '-f', 'backend/pom.xml', 'spring-boot:run', '-Dspring-boot.run.profiles=local', '-Dspring-boot.run.arguments=--server.port=8081')
