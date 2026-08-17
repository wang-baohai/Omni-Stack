import urllib.request
import json
import subprocess
import re

# Login
cap_url = "http://localhost:8102/api/auth/captcha"
req = urllib.request.Request(cap_url, headers={"X-Tenant-Id": "1"})
cap_resp = json.loads(urllib.request.urlopen(req).read())
cap_key = cap_resp["data"]["captchaKey"]
result = subprocess.run(["docker", "exec", "omni-redis", "redis-cli", "GET", f"captcha:{cap_key}"],
                       capture_output=True, text=True)
cap_code = result.stdout.strip()
login_body = json.dumps({"username": "admin", "password": "admin123", "tenantId": 1,
                         "captchaKey": cap_key, "captchaCode": cap_code}).encode("utf-8")
login_req = urllib.request.Request("http://localhost:8102/api/auth/login", data=login_body,
                                   headers={"Content-Type": "application/json", "X-Tenant-Id": "1"},
                                   method="POST")
login_resp = json.loads(urllib.request.urlopen(login_req).read())
token = login_resp["data"]["accessToken"]
h = {"Authorization": f"Bearer {token}", "X-Tenant-Id": "1"}

# Check all instances with comments
pids = [
    ("Instance 1", "73d6783e-8af2-11f1-b230-d23947eb06c7"),
    ("Instance 3", "73e6783e-8af2-11f1-b230-d23947eb06c7"),
    ("Instance 4", "7416dcfe-8af2-11f1-b230-d23947eb06c7"),
    ("Instance 5", "7426e2b6-8af2-11f1-b230-d23947eb06c7"),
]
for name, pid in pids:
    req = urllib.request.Request(f"http://localhost:8102/api/workflow/process-instance/{pid}/approval-records", headers=h)
    raw = urllib.request.urlopen(req).read()
    matches = re.findall(rb'"comment":"(.*?)"', raw)
    print(f"{name}:")
    for i, m in enumerate(matches):
        print(f"  c{i+1}: hex={m.hex()}")
    if not matches:
        print("  (no comments)")
