# -*- coding: utf-8 -*-
"""
رفع المشروع كله على GitHub من الموبايل (Pydroid3) بدون git ولا مكتبات خارجية.

الاستخدام:
  1) افتح GitHub > Settings > Developer settings > Personal access tokens > Fine-grained
     وأنشئ توكن بصلاحية Contents: Read and write على الريبو.
  2) أنشئ ريبو فاضي (مثلاً NasimLauncher).
  3) حط NasimLauncher.zip جنب السكريبت (أو غيّر ZIP_PATH) وشغّله.
"""
import base64, json, os, sys, time, urllib.request, urllib.error, zipfile, getpass

ZIP_PATH = "NasimLauncher.zip"
BRANCH = "main"

owner = input("GitHub username: ").strip()
repo = input("Repo name: ").strip()
token = getpass.getpass("Token (لن يظهر): ").strip()

api = "https://api.github.com/repos/%s/%s/contents/" % (owner, repo)
headers = {
    "Authorization": "Bearer " + token,
    "Accept": "application/vnd.github+json",
    "User-Agent": "push-script",
}

def call(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        if e.code == 404 and method == "GET":
            return None
        raise RuntimeError("%s %s -> %s %s" % (method, url, e.code, e.read().decode()[:300]))

z = zipfile.ZipFile(ZIP_PATH)
names = [n for n in z.namelist() if not n.endswith("/")]
print("عدد الملفات:", len(names))
for i, name in enumerate(names, 1):
    content = z.read(name)
    existing = call("GET", api + name + "?ref=" + BRANCH)
    body = {
        "message": "add " + name,
        "content": base64.b64encode(content).decode(),
        "branch": BRANCH,
    }
    if existing and "sha" in existing:
        body["sha"] = existing["sha"]
    call("PUT", api + name, body)
    print("[%d/%d] %s" % (i, len(names), name))
    time.sleep(0.4)
print("تم! افتح تبويب Actions في الريبو وانتظر البناء، وبعدها نزّل الـ APKs من Artifacts.")
