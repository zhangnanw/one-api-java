import urllib.request

req = urllib.request.Request("http://localhost:13000/api/virtual-models", method="GET")
with urllib.request.urlopen(req) as resp:
    data = resp.read().decode()
    # Find 'auto' in response
    if '"name":"auto"' in data:
        print("SUCCESS: 'auto' virtual model found!")
        # Extract the auto entry
        import json
        body = json.loads(data)
        for vm in body.get("data", []):
            if vm.get("name") == "auto":
                print(f"  ID: {vm.get('id')}")
                print(f"  Name: {vm.get('name')}")
                print(f"  Match: {vm.get('match')}")
    else:
        print("FAIL: 'auto' not found")
        print(data[:500])
