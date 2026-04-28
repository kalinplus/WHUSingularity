Param(
    [int]$Index = -1,
    [int]$Port = -1,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Out-Json([hashtable]$obj) { $obj | ConvertTo-Json -Depth 6 -Compress }

function Get-UserIndices {
    $names = docker ps -a --format "{{.Names}}" | Where-Object { $_ -match "^singularity-user-\d+$" }
    $indices = @()
    foreach ($name in $names) { $indices += [int]($name -replace "^singularity-user-", "") }
    return $indices
}

function Get-UsedHostPorts {
    $used = New-Object System.Collections.Generic.HashSet[int]
    $ports = docker ps --format "{{.Ports}}"
    foreach ($line in $ports) {
        $matches = [regex]::Matches($line, "0\.0\.0\.0:(\d+)->")
        foreach ($m in $matches) { [void]$used.Add([int]$m.Groups[1].Value) }
    }
    return $used
}

function Get-NextUserPort {
    $used = Get-UsedHostPorts
    for ($p = 8093; $p -le 8999; $p++) { if (-not $used.Contains($p)) { return $p } }
    throw "No free port found for user in [8093, 8999]."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$indices = Get-UserIndices
$targetIndex = if ($Index -ge 0) { $Index } else { if ($indices.Count -eq 0) { 0 } else { (($indices | Measure-Object -Maximum).Maximum + 1) } }
$containerName = "singularity-user-$targetIndex"
$exists = docker ps -a --format "{{.Names}}" | Where-Object { $_ -eq $containerName }
if ($exists) { throw "Container already exists: $containerName" }
$hostPort = if ($Port -gt 0) { $Port } else { Get-NextUserPort }

docker network inspect deploy_default > $null 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Network 'deploy_default' not found. Start base stack first: docker compose -f deploy/docker-compose.backend.yml up -d"
}

if ($DryRun) {
    Out-Json @{ success = $true; action = "start"; service = "user"; container = $containerName; index = $targetIndex; port = $hostPort; dryRun = $true }
    exit 0
}

docker run -d `
    --name $containerName `
    --restart unless-stopped `
    --network deploy_default `
    -p "${hostPort}:${hostPort}" `
    -e SPRING_CLOUD_NACOS_SERVER_ADDR="nacos:8848" `
    -e SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/singularity_user?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" `
    -e SPRING_DATASOURCE_USERNAME="root" `
    -e SPRING_DATASOURCE_PASSWORD="root" `
    -e SPRING_DATA_REDIS_HOST="redis" `
    -e SPRING_DATA_REDIS_PORT="6379" `
    -e JWT_SECRET="dev-secret-key" `
    -e SERVER_PORT="$hostPort" `
    -v "${repoRoot}:/workspace" `
    -v "deploy_maven_repo:/root/.m2" `
    -w /workspace `
    maven:3.9.9-eclipse-temurin-21 `
    sh -c "java -jar singularity-user/target/singularity-user-1.0-SNAPSHOT.jar" > $null

if ($LASTEXITCODE -ne 0) { throw "Failed to start container $containerName." }
Out-Json @{ success = $true; action = "start"; service = "user"; container = $containerName; index = $targetIndex; port = $hostPort; dryRun = $false }
