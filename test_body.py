import urllib.request
import json

# 先测一个 GET，确认服务可达
try:
    req = urllib.request.Request("http://localhost:13000/api/virtual-models", method="GET")
    with urllib.request.urlopen(req) as resp:
        print(f"GET Status: {resp.status}")
        print(f"GET Body: {resp.read().decode()[:200]}")
except Exception as e:
    print(f"GET Error: {e}")

# 再测一个简单 POST
print("\n--- Testing POST with simple body ---")
url = "http://localhost:13000/api/virtual-models"
data = {"name": "test-simple", "match": "{}"}

req = urllib.request.Request(url,
    data=json.dumps(data).encode('utf-8'),
    headers={"Content-Type": "application/json"},
    method="POST")

try:
    with urllib.request.urlopen(req) as resp:
        print(f"POST Status: {resp.status}")
        print(f"POST Body: {resp.read().decode()}")
except urllib.error.HTTPError as e:
    print(f"POST Error: {e.code} {e.reason}")
    print(f"POST Body: {e.read().decode()}")
