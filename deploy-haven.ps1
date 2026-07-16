# deploy-haven.ps1
# Automation helper to compile and deploy the Haven Android App via local DMS Host

$DmsServer = "http://localhost:18800"
$ApkPath = "C:\Users\admin\source\project-haven-android\app\build\outputs\apk\debug\app-debug.apk"

# 1. Compile Haven App
Write-Host "Compiling Haven Android App..." -ForegroundColor Cyan
./gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Error "Haven compilation failed."
    exit $LASTEXITCODE
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "Compiled APK not found at $ApkPath"
    exit 1
}

Write-Host "Build successful. Locating online devices on DMS..." -ForegroundColor Cyan

# 2. Fetch Active Devices from DMS Host
try {
    $devices = Invoke-RestMethod -Uri "$DmsServer/api/devices" -Method Get -TimeoutSec 5
} catch {
    Write-Warning "Failed to connect to DMS Server. Make sure the server is running (dotnet run in C:\Users\admin\android-device-manager-server)."
    exit 1
}

$onlineDevices = $devices | Where-Object { $_.IsOnline -eq $true }

if ($null -eq $onlineDevices -or $onlineDevices.Count -eq 0) {
    Write-Warning "No online devices registered in DMS. Ensure the DMS client service is started on your phone/tablet."
    exit 0
}

# 3. Deploy to all online devices
foreach ($dev in $onlineDevices) {
    Write-Host "Deploying to device: $($dev.Name) ($($dev.Model))..." -ForegroundColor Green
    try {
        $response = Invoke-RestMethod -Uri "$DmsServer/api/devices/$($dev.Id)/upload-apk" `
                                      -Method Post `
                                      -Form @{ file = Get-Item $ApkPath }
        Write-Host "Successfully enqueued install command on $($dev.Name)! DMS Command ID: $($response.CommandId)" -ForegroundColor Green
    } catch {
        Write-Error "Deployment failed for $($dev.Name): $_"
    }
}
