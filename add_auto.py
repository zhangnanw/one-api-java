import urllib.request
import json

url = "http://localhost:13000/api/virtual-models"
data = {
    "name": "auto",
    "match": "{\"models\":[\"kimi-k2.6\",\"mimo-v2.5-pro\",\"minimax-m3\",\"doubao-seed-2.0-pro-260215\",\"deepseek-v4-pro\"]}"
}

req = urllib.request.Request(url,
    data=json.dumps(data).encode(),
    headers={"Content-Type": "application/json"},
    method="POST")

try:
    with urllib.request.urlopen(req) as resp:
        print(f"Status: {resp.status}")
        print(f"Body: {resp.read().decode()}")
except urllib.error.HTTPError as e:
    print(f"Error: {e.code} {e.reason}")
    print(f"Body: {e.read().decode()}")
