param(
    [ValidateRange(5, 120)]
    [int]$Seconds = 40
)

$ErrorActionPreference = 'Stop'
$scanner = Join-Path $PSScriptRoot 'scan_ble.py'

& uv run --with bleak $scanner --seconds $Seconds
if ($LASTEXITCODE -ne 0) {
    throw "BLE scan failed with exit code $LASTEXITCODE"
}
