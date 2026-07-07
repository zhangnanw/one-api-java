$body = @"
{"name":"auto","match":"{\"models\":[\"kimi-k2.6\",\"mimo-v2.5-pro\",\"minimax-m3\",\"doubao-seed-2.0-pro-260215\",\"deepseek-v4-pro\"]}"}
"@
$body | Out-File -FilePath "$env:TEMP\post_body.json" -NoNewline -Encoding UTF8

$output = & curl.exe -s -X POST "http://localhost:13000/api/virtual-models" `
    -H "Content-Type: application/json" `
    -d "@$env:TEMP\post_body.json" `
    2>&1

Write-Host $output
