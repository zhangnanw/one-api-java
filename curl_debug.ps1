$body = '{"name":"test","match":"{}"}'
$headers = @{
    "Content-Type" = "application/json"
}

# Write body to temp file to avoid quoting issues
$body | Out-File -FilePath "$env:TEMP\post_body.json" -NoNewline -Encoding UTF8

# Use curl.exe with verbose output
$output = & curl.exe -v -X POST "http://localhost:13000/api/virtual-models" `
    -H "Content-Type: application/json" `
    -d "@$env:TEMP\post_body.json" `
    2>&1

Write-Host "=== Curl Output ==="
$output | ForEach-Object { Write-Host $_ }
