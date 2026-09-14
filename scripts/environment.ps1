# 只设置当前PowerShell进程；本地凭据不输出、不复制到交付物。
$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
$script:ProductVersion = (Get-Content -LiteralPath (Join-Path $ProjectRoot 'VERSION') -Raw).Trim()
if ($ProductVersion -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$') { throw 'VERSION 格式无效' }
$script:MavenCommand = 'mvn'
$localConfigPath = Join-Path $ProjectRoot '.local/environment.json'
if (Test-Path -LiteralPath $localConfigPath) {
    $localConfig = Get-Content -LiteralPath $localConfigPath -Raw | ConvertFrom-Json
    $env:JAVA_HOME = $localConfig.javaHome
    $script:MavenCommand = Join-Path $localConfig.mavenHome 'bin/mvn.cmd'
    $env:MAIMAI_DB_HOST = $localConfig.database.host
    $env:MAIMAI_DB_PORT = [string]$localConfig.database.port
    $env:MAIMAI_DB_USER = $localConfig.database.username
    $env:MAIMAI_DB_PASSWORD = $localConfig.database.password
}
$amapConfigPath = Join-Path $ProjectRoot '.local/private/amap.json'
if (Test-Path -LiteralPath $amapConfigPath) {
    $amapConfig = Get-Content -LiteralPath $amapConfigPath -Raw | ConvertFrom-Json
    if ($amapConfig.platform -eq 'web-jsapi') {
        $env:MAIMAI_AMAP_JS_KEY = $amapConfig.key
        $env:MAIMAI_AMAP_SECURITY_CODE = $amapConfig.securityJsCode
    }
}
$env:MAIMAI_UPLOAD_DIR = Join-Path $ProjectRoot '.local/uploads'
$env:MAIMAI_AVATAR_DIR = Join-Path $ProjectRoot '.local/avatars'
$env:MAIMAI_MESSAGE_IMAGE_DIR = Join-Path $ProjectRoot '.local/private-message-images'
$env:MAIMAI_AFTERSALE_IMAGE_DIR = Join-Path $ProjectRoot '.local/private-aftersale-images'
$env:MAIMAI_MAIL_CAPTURE_DIR = Join-Path $ProjectRoot '.local/mail-capture'
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8'

function Invoke-CheckedCommand {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "命令执行失败，退出码 $LASTEXITCODE" }
}
