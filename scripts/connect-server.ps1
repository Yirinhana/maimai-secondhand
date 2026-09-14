param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RemoteCommand
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskConfig = Join-Path $taskRoot '.local/ssh/maimai-tailscale.conf'
$taskSsh = Join-Path $env:WINDIR 'System32/OpenSSH/ssh.exe'
if (-not (Test-Path -LiteralPath $taskConfig)) {
    throw '缺少项目私密连接配置；请先核实服务器与部署密钥，不会回退到公网或忽略主机指纹。'
}
if (-not (Test-Path -LiteralPath $taskSsh)) {
    throw '未找到 Windows OpenSSH 客户端；不会自动安装或修改系统配置。'
}
if ($RemoteCommand) {
    & $taskSsh -F $taskConfig maimai-tencent @RemoteCommand
} else {
    & $taskSsh -F $taskConfig maimai-tencent
}
exit $LASTEXITCODE
