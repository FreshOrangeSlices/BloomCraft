#!/usr/bin/env python3
"""Checks that every mixin injector's target method is declared in its target class (javap on the dev-kit stubs)."""
import glob, os, re, subprocess, sys
KIT = os.environ.get("KIT") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".devkit")
cp = ":".join(glob.glob(KIT + "/stubs/*.jar"))
repo = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
cache = {}
def members(cls):
    if cls not in cache:
        out = subprocess.run([KIT + "/jdk/bin/javap", "-p", "-s", "-cp", cp, cls], capture_output=True, text=True).stdout
        cache[cls] = out
    return cache[cls]
bad = 0
for f in glob.glob(repo + "/src/*/java/**/mixin/**/*.java", recursive=True):
    src = open(f).read()
    m = re.search(r"@Mixin\((?:value\s*=\s*)?\{?([\w.$]+)\.class", src)
    if not m:
        continue
    simple = m.group(1)
    imp = re.search(r"import ([\w.]+\." + re.escape(simple.split('.')[0]) + r");", src)
    cls = imp.group(1) + simple[len(simple.split('.')[0]):] if imp else simple
    cls = cls.replace(".", "/")
    # inner classes written Outer.Inner -> Outer$Inner
    parts = cls.split("/")
    pkg = [p for p in parts if p[:1].islower()]
    names = [p for p in parts if p[:1].isupper()]
    cls = ".".join(pkg) + "." + "$".join(names)
    body = members(cls)
    if not body.strip():
        print(f"{f}: target class {cls} not found"); bad += 1; continue
    for mm in re.finditer(r'method\s*=\s*\{?\s*"([^"]+)"', src):
        target = mm.group(1)
        name = target.split("(")[0]
        desc = target[len(name):]
        found = [l for l in body.splitlines() if re.search(r"[ .]" + re.escape(name) + r"\(", l)]
        if not found:
            print(f"{f}: {cls} declares no method '{name}'"); bad += 1
        elif desc:
            descs = [l.strip() for l in body.splitlines() if l.strip().startswith("descriptor:")]
            lines = body.splitlines()
            ok = False
            for i, l in enumerate(lines):
                if re.search(r"[ .]" + re.escape(name) + r"\(", l) and i + 1 < len(lines) and desc in lines[i + 1]:
                    ok = True
            if not ok:
                print(f"{f}: {cls}.{name} has no descriptor {desc}"); bad += 1
    for am in re.finditer(r'@(?:Accessor|Invoker)\("([^"]+)"\)', src):
        name = am.group(1)
        if not re.search(r"[ .]" + re.escape(name) + r"[;(]", body):
            print(f"{f}: {cls} has no member '{name}'"); bad += 1
print("MIXINS OK" if bad == 0 else f"MIXINS: {bad} problem(s)")
sys.exit(1 if bad else 0)
