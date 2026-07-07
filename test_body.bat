@echo off
echo === GET ===
curl.exe -s http://localhost:13000/api/virtual-models | findstr /C:"success"

echo.
echo === POST ===
curl.exe -s -X POST http://localhost:13000/api/virtual-models -H "Content-Type: application/json" -d "{\"name\":\"test-curl\",\"match\":\"{}\"}"
