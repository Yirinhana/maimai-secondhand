param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 8081,
    [switch]$PrepareOnly
)

. (Join-Path $PSScriptRoot 'environment.ps1')

function Test-LocalConnection {
    param([string]$Server, [int]$ServerPort)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync($Server, $ServerPort)
        return ($pending.Wait(3000) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

if (-not $PrepareOnly -and (Test-LocalConnection '127.0.0.1' $Port)) {
    throw "Port $Port is already in use. Stop the existing backend in its original terminal before pressing F5. Do not stop MySQL."
}
if ([string]::IsNullOrWhiteSpace($env:MAIMAI_DB_PASSWORD)) {
    throw 'Database password is missing. Configure .local/environment.json or MAIMAI_DB_PASSWORD before launching VS Code. Do not reset the existing database.'
}

$dbServer = if ($env:MAIMAI_DB_HOST) { $env:MAIMAI_DB_HOST } else { '127.0.0.1' }
$dbPort = if ($env:MAIMAI_DB_PORT) { [int]$env:MAIMAI_DB_PORT } else { 3307 }
if (-not $PrepareOnly -and -not (Test-LocalConnection $dbServer $dbPort)) {
    throw "Cannot reach MySQL at ${dbServer}:$dbPort. Start the project database instance first; opening a database GUI alone is not sufficient."
}

function ConvertTo-JavaPropertyValue {
    param([string]$Value)
    $escaped = $Value.Replace('\', '\\').Replace("`r", '\r').Replace("`n", '\n').Replace("`t", '\t').Replace(' ', '\ ')
    # ASCII plus Unicode escapes works with either Java properties file encoding.
    return [regex]::Replace($escaped, '[^\x20-\x7e]', {
        param($match)
        '\u{0:x4}' -f [int][char]$match.Value
    })
}

# Only copy the variables used by this local launch; never export the whole environment.
$variableNames = @(
    'MAIMAI_DB_HOST', 'MAIMAI_DB_PORT', 'MAIMAI_DB_USER', 'MAIMAI_DB_PASSWORD',
    'MAIMAI_UPLOAD_DIR', 'MAIMAI_AVATAR_DIR', 'MAIMAI_MESSAGE_IMAGE_DIR',
    'MAIMAI_AFTERSALE_IMAGE_DIR', 'MAIMAI_MAIL_CAPTURE_DIR',
    'MAIMAI_AMAP_JS_KEY', 'MAIMAI_AMAP_SECURITY_CODE'
)
$lines = @('# Generated for local VS Code launch. Contains credentials; do not share or commit.')
foreach ($name in $variableNames) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ($null -ne $value) {
        $lines += $name + '=' + (ConvertTo-JavaPropertyValue $value)
    }
}
if ($PrepareOnly) {
    $nodeCommand = Get-Command node.exe -ErrorAction Stop
    $mysqlCommand = Get-Command mysqld.exe -ErrorAction SilentlyContinue
    $mysqlExecutable = if ($mysqlCommand) { $mysqlCommand.Source } else { Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.0/bin/mysqld.exe' }
    $launcherProperties = [ordered]@{
        'launcher.node' = $nodeCommand.Source
        'launcher.mysql' = $mysqlExecutable
        'launcher.mysqlData' = Join-Path $ProjectRoot '.local/mysql-data'
        'launcher.mysqlDefaults' = Join-Path $ProjectRoot '.local/private/mysql-root.ini'
    }
    foreach ($entry in $launcherProperties.GetEnumerator()) {
        $lines += $entry.Key + '=' + (ConvertTo-JavaPropertyValue $entry.Value)
    }
}
$privateDirectory = Join-Path $ProjectRoot '.local/private'
[System.IO.Directory]::CreateDirectory($privateDirectory) | Out-Null
$propertiesPath = Join-Path $privateDirectory 'vscode-backend.properties'
[System.IO.File]::WriteAllLines($propertiesPath, [string[]]$lines, [System.Text.Encoding]::ASCII)
Write-Host "Local configuration ready: profile=local, backend=$Port, MySQL=${dbServer}:$dbPort. Credentials were not printed."
