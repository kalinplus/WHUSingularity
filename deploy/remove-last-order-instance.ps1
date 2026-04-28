Param(
    [int]$Index = -1,
    [int]$MinInstances = 1,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Out-Json([hashtable]$obj) {
    $obj | ConvertTo-Json -Depth 6 -Compress
}

$names = docker ps -a --format "{{.Names}}" | Where-Object { $_ -match "^singularity-order-\d+$" }
$items = @()
foreach ($name in $names) {
    $items += [PSCustomObject]@{ Name = $name; Index = [int]($name -replace "^singularity-order-", "") }
}

if ($items.Count -eq 0) {
    Out-Json @{ success = $true; action = "remove"; service = "order"; removed = $false; reason = "no_instance" }
    exit 0
}

if ($items.Count -le $MinInstances) {
    Out-Json @{ success = $true; action = "remove"; service = "order"; removed = $false; reason = "min_instances_protected"; minInstances = $MinInstances; current = $items.Count }
    exit 0
}

$target = if ($Index -ge 0) { $items | Where-Object { $_.Index -eq $Index } | Select-Object -First 1 } else { $items | Sort-Object Index -Descending | Select-Object -First 1 }
if (-not $target) {
    throw "Target order instance not found for index=$Index"
}

if ($DryRun) {
    Out-Json @{ success = $true; action = "remove"; service = "order"; removed = $true; dryRun = $true; container = $target.Name; index = $target.Index }
    exit 0
}

docker rm -f $target.Name > $null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to remove container $($target.Name)."
}

Out-Json @{ success = $true; action = "remove"; service = "order"; removed = $true; dryRun = $false; container = $target.Name; index = $target.Index }
