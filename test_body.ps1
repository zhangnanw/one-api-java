$body = '{"name":"test","match":"{}"}'
$headers = @{
    "Content-Type" = "application/json"
    "Content-Length" = [string]$body.Length
}
Write-Host "Body length: $($body.Length)"
Write-Host "Body: $body"
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:13000/api/virtual-models" -Method POST -Headers $headers -Body $body -TimeoutSec 5
    Write-Host "Status: $($resp.StatusCode)"
    Write-Host "Content: $($resp.Content)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    Write-Host "Response: $($_.Exception.Response)"
}
