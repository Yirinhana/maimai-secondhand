# Updates only the five verified demo bindings in this workspace. Never targets a server or real uploaded UUID images.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$demoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$demoUploadRoot = [IO.Path]::GetFullPath((Join-Path $demoRoot '.local/uploads/products'))
$demoSourceRoot = Join-Path $demoRoot 'backend/src/main/resources/demo/products'
$demoBackupRoot = Join-Path $demoRoot ('.local/backups/037-demo-images/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$demoBindings = @(
  @{id=1; seller=4; title='iPhone 12 128GB 蓝色 自用一手'; asset='iphone-blue.jpg'; oldHash='482ed167be6dfc21514b21b730739deb1ff02a1ad8ce6cef6795d0952df6618a'},
  @{id=2; seller=4; title='索尼 WH-1000XM4 降噪耳机'; asset='headphones-charcoal.jpg'; oldHash='4f99cf1059953b6fcae736a3869731d22846c574cc4ffe2cdf4fbc44fb4b74eb'},
  @{id=3; seller=4; title='Java 核心技术 卷I（第11版）'; asset='java-textbook.jpg'; oldHash='7e5917826fd6c4a45388c19cc359f7ae2d58a0ee4f090257c62e01b34e8325bd'},
  @{id=5; seller=5; title='优衣库羊毛混纺大衣 M 码'; asset='wool-coat.jpg'; oldHash='9262c312f5a7d9cacca705272e4f528e805ec93b64df67816bc89b6085bd7cc0'},
  @{id=6; seller=5; title='尤尼克斯羽毛球拍 天斧77'; asset='badminton-racket.jpg'; oldHash='1e02379c644fe40129f4026a3493be4139ef4f0202f29a6c40b00a9e37a6455c'}
)
$demoPlan = foreach ($binding in $demoBindings) {
  $fileName = 'seed-' + $binding.id + '.jpg'
  $target = [IO.Path]::GetFullPath((Join-Path $demoUploadRoot $fileName))
  if ([IO.Path]::GetDirectoryName($target) -ne $demoUploadRoot) { throw 'Demo target escaped the intended folder' }
  $source = Join-Path $demoSourceRoot $binding.asset
  $product = Invoke-RestMethod -Uri ('http://127.0.0.1:8081/api/v1/products/' + $binding.id) -TimeoutSec 15
  $apiPath = '/uploads/products/' + $fileName
  if ($product.id -ne $binding.id -or $product.title -ne $binding.title -or $product.seller.id -ne $binding.seller -or $product.images.Count -ne 1 -or $product.images[0].path -ne $apiPath) { throw "Binding differs from the reviewed demo: $($binding.id)" }
  $oldHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
  $newHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($oldHash -ne $binding.oldHash -and $oldHash -ne $newHash) { throw "Unexpected changed image; review before replacing: $fileName" }
  $decoded = [Drawing.Image]::FromFile($source)
  try { if ($decoded.RawFormat.Guid -ne [Drawing.Imaging.ImageFormat]::Jpeg.Guid -or $decoded.Width -lt 600 -or $decoded.Height -lt 600) { throw "Invalid image: $source" } } finally { $decoded.Dispose() }
  [pscustomobject]@{id=$binding.id; source=$source; target=$target; url=$apiPath; oldSha256=$oldHash; newSha256=$newHash; oldLastWriteUtc=(Get-Item -LiteralPath $target).LastWriteTimeUtc; update=($oldHash -ne $newHash); applied=$false}
}
$demoChanges = @($demoPlan | Where-Object update)
if ($demoChanges.Count -eq 0) { Write-Output 'All five reviewed demo images already match. No files changed.'; return }
# Back up every target before the first replacement. Keep originals and any failed staging file.
New-Item -ItemType Directory -Path $demoBackupRoot -ErrorAction Stop | Out-Null
foreach ($entry in $demoChanges) { Copy-Item -LiteralPath $entry.target -Destination (Join-Path $demoBackupRoot ([IO.Path]::GetFileName($entry.target))) }
$manifestPath = Join-Path $demoBackupRoot 'manifest.json'
$demoPlan | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8
foreach ($entry in $demoChanges) {
  $staged = $entry.target + '.037-staged-' + [guid]::NewGuid().ToString('N')
  Copy-Item -LiteralPath $entry.source -Destination $staged
  if ((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.newSha256) { throw 'Staged image checksum mismatch' }
  [IO.File]::SetLastWriteTimeUtc($staged, [DateTime]::UtcNow)
  $atomicBackup = Join-Path $demoBackupRoot ('atomic-' + [IO.Path]::GetFileName($entry.target))
  [IO.File]::Replace($staged, $entry.target, $atomicBackup)
  $entry.applied = $true
  $demoPlan | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8
}
Write-Output ("Updated {0} demo images. Backup and applied-file manifest: {1}" -f $demoChanges.Count, $demoBackupRoot)
