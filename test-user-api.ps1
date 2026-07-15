
# 请在此处填入您的 API Key
$apiKey = "YOUR_API_KEY_HERE"

$headers = @{
    "Authorization" = "Bearer $apiKey"
}

Write-Host "--- Testing /v1/user/info ---"
try {
    $response = Invoke-RestMethod -Uri "https://api.siliconflow.cn/v1/user/info" -Headers $headers -Method Get
    $response | ConvertTo-Json -Depth 10
} catch {
    Write-Host "Error: $_"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        $reader.ReadToEnd()
    }
}
