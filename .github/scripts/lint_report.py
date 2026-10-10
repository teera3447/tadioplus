import sys, xml.etree.ElementTree as ET
p = sys.argv[1]
try:
    root = ET.parse(p).getroot()
except Exception as e:
    print("::warning::lint report missing: %s" % e); sys.exit(0)
n = 0
for i in root.iter("issue"):
    iid = i.get("id"); sev = i.get("severity")
    if iid in ("NewApi", "InlinedApi", "Override", "UnsafeNewApiCall", "MissingPermission") or sev in ("Error", "Fatal"):
        loc = i.find("location")
        f = (loc.get("file") if loc is not None else "") or ""
        ln = (loc.get("line") if loc is not None else "") or "1"
        msg = (i.get("message") or "").replace("\n", " ")
        print("::error file=%s,line=%s::[%s/%s] %s" % (f.split("PlayTV/", 1)[-1] and ("PlayTV/" + f.split("PlayTV/", 1)[-1]), ln, iid, sev, msg))
        n += 1
print("lint: %d relevant issues" % n)
