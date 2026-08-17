import urllib.request
import json
import subprocess

# 1. Login
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

# 2. Get raw API response bytes
pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
req4 = urllib.request.Request(f"http://localhost:8102/api/workflow/process-instance/{pid4}/approval-records", headers=h)
raw = urllib.request.urlopen(req4).read()

# Save raw bytes
with open(r"c:\WorkSpace\QODER\Omni-Stack\scripts\api-raw-bytes.bin", "wb") as f:
    f.write(raw)

# Parse and check comment hex
j = json.loads(raw)
for rec in j["data"]:
    c = rec.get("comment")
    if c:
        print(f"comment: {repr(c)}")
        print(f"hex: {c.encode('utf-8').hex()}")
        print()
