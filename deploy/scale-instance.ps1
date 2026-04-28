Param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("order", "user", "stock")]
    [string]$Service,

    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "remove")]
    [string]$Action,

    [int]$Index = -1,
    [int]$Port = -1,
    [int]$MinInstances = 1,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Out-Json([hashtable]$obj) {
    $obj | ConvertTo-Json -Depth 6 -Compress
}

try {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

    if ($Action -eq "start") {
        $targetScript = Join-Path $scriptDir ("start-next-{0}-instance.ps1" -f $Service)
        if (-not (Test-Path $targetScript)) {
            throw "Target script not found: $targetScript"
        }

        $args = @("-ExecutionPolicy", "Bypass", "-File", $targetScript)
        if ($Index -ge 0) { $args += @("-Index", $Index) }
        if ($Port -gt 0) { $args += @("-Port", $Port) }
        if ($DryRun) { $args += "-DryRun" }

        & powershell @args
        if ($LASTEXITCODE -ne 0) {
            throw "Sub-script failed: $targetScript"
        }
        exit 0
    }

    $targetScript = Join-Path $scriptDir ("remove-last-{0}-instance.ps1" -f $Service)
    if (-not (Test-Path $targetScript)) {
        throw "Target script not found: $targetScript"
    }

    $removeArgs = @("-ExecutionPolicy", "Bypass", "-File", $targetScript)
    if ($Index -ge 0) { $removeArgs += @("-Index", $Index) }
    $removeArgs += @("-MinInstances", $MinInstances)
    if ($DryRun) { $removeArgs += "-DryRun" }

    & powershell @removeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Sub-script failed: $targetScript"
    }
    exit 0
} catch {
    Out-Json @{
        success = $false
        service = $Service
        action = $Action
        error = $_.Exception.Message
    }
    exit 1
}
