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
print("Token OK")

# 2. Test Instance 4 (it-high, has 2 levels with CTO)
pid4 = "7416dcfe-8af2-11f1-b230-d23947eb06c7"
req4 = urllib.request.Request(f"http://localhost:8102/api/workflow/process-instance/{pid4}/approval-records", headers=h)
resp4 = json.loads(urllib.request.urlopen(req4).read())
print("\n=== Instance 4 (it-high) ===")
for rec in resp4["data"]:
    print(f"  {rec['assigneeName']} | result={rec['result']} | comment={rec['comment']}")

# 3. Test Instance 1 (office-low)
pid1 = "73d6783e-8af2-11f1-b230-d23947eb06c7"
req1 = urllib.request.Request(f"http://localhost:8102/api/workflow/process-instance/{pid1}/approval-records", headers=h)
resp1 = json.loads(urllib.request.urlopen(req1).read())
print("\n=== Instance 1 (office-low) ===")
for rec in resp1["data"]:
    print(f"  {rec['assigneeName']} | result={rec['result']} | comment={rec['comment']}")

# 4. Test Instance 5 (office-high)
pid5 = "7426e2b6-8af2-11f1-b230-d23947eb06c7"
req5 = urllib.request.Request(f"http://localhost:8102/api/workflow/process-instance/{pid5}/approval-records", headers=h)
resp5 = json.loads(urllib.request.urlopen(req5).read())
print("\n=== Instance 5 (office-high) ===")
for rec in resp5["data"]:
    print(f"  {rec['assigneeName']} | result={rec['result']} | comment={rec['comment']}")

print("\n=== Done ===")
