import re

data = open(r"c:\WorkSpace\QODER\Omni-Stack\scripts\api-raw-bytes.bin", "rb").read()
matches = re.findall(rb'"comment":"(.*?)"', data)
for i, m in enumerate(matches):
    print(f"c{i+1}: hex={m.hex()}")
