$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend')
if (!(Test-Path -LiteralPath 'node_modules')) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
}
& npm.cmd run dev -- --host 127.0.0.1
exit $LASTEXITCODE
