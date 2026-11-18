$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
New-Item -ItemType Directory -Path (Join-Path $projectRoot 'build') -Force | Out-Null
$runtimePath = Join-Path $projectRoot 'build/phone-update-runtime.json'
$running = $false
if (Test-Path -LiteralPath $runtimePath) {
    $state = Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json
    try { $reply = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Uri ('http://127.0.0.1:'+$state.port+'/'+$state.token+'/health'); $running = $reply.Content -eq 'QwenVision-update' } catch {}
}
if (-not $running) {
    $addresses = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and $_.InterfaceAlias -notmatch 'VMware|vEthernet|Virtual|Meta|Loopback' }
    $wifi = $addresses | Where-Object { $_.InterfaceAlias -match 'WLAN|Wi-Fi|Wireless|无线' } | Select-Object -First 1
    if (-not $wifi) { $wifi = $addresses | Select-Object -First 1 }
    if (-not $wifi) { throw 'No LAN address. Connect this PC and the phone to the same Wi-Fi.' }
    $pythonPath = (Get-Command python).Source
    Start-Process -FilePath $pythonPath -ArgumentList @('scripts/phone_update.py','--ip',$wifi.IPAddress) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot 'build/phone-update.log') -RedirectStandardError (Join-Path $projectRoot 'build/phone-update-error.log')
    for ($i=0; $i -lt 30; $i++) { Start-Sleep -Milliseconds 200; if (Test-Path -LiteralPath $runtimePath) { $state = Get-Content -LiteralPath $runtimePath -Raw | ConvertFrom-Json; try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 -Uri ('http://127.0.0.1:'+$state.port+'/'+$state.token+'/health') | Out-Null; break } catch {} } }
}
if (-not $state.url) { throw 'Update server did not start. See build/phone-update-error.log.' }
Start-Process $state.url
