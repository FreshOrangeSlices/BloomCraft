#!/usr/bin/env python3
"""API lookup for development: lists classes and prints member signatures from the compile classpath
(Minecraft, Fabric API and libraries), into api.txt.

    api.py <classpath file> "<class names>" "<search regex>" ["<classes with code>"] ["<resource regex>"]

A class name ending in ".*" means every class in that package (not sub-packages).
"Classes with code" are printed with their bytecode (javap -c), to aim mixins at the exact call.
"Resource regex" prints text files (json, mcmeta...) from the jars whose path matches, e.g. assets/minecraft/items/compass.json.
"""
import re
import subprocess
import sys
import zipfile

TEXT = (".json", ".mcmeta", ".txt", ".properties", ".fsh", ".vsh", ".glsl")
MAX_FILES = 60


def jar_list(cp_file):
    with open(cp_file) as f:
        return [line[3:].strip() for line in f if line.startswith("CP ") and line.strip().endswith(".jar")]


def all_classes(jars):
    names = []
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    if name.endswith(".class") and name != "module-info.class":
                        names.append(name[:-6].replace("/", "."))
        except (OSError, zipfile.BadZipFile):
            pass
    return names


def expand(names, classes):
    out = []
    for name in names:
        if name.endswith(".*"):
            package = name[:-2]
            out.extend(sorted(c for c in classes if c.rsplit(".", 1)[0] == package and "$" not in c))
        else:
            out.append(name)
    return out


def javap(jars, names, flags):
    out = []
    for i in range(0, len(names), 40):
        batch = names[i:i + 40]
        result = subprocess.run(["javap", *flags, "-cp", ":".join(jars), *batch], capture_output=True, text=True)
        out.append(result.stdout)
        if result.stderr.strip():
            out.append("javap: " + result.stderr.strip())
    return out


def resources(jars, pattern):
    out = []
    regex = re.compile(pattern)
    shown = 0
    listed = []
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    if not regex.search(name):
                        continue
                    listed.append(f"{name}  ({jar.rsplit('/', 1)[-1]})")
                    if shown < MAX_FILES and name.endswith(TEXT):
                        shown += 1
                        out.append(f"===== {name} =====")
                        out.append(z.read(name).decode("utf-8", errors="replace"))
        except (OSError, zipfile.BadZipFile):
            pass
    return [f"== resources matching {pattern} ({len(listed)}) =="] + listed + out


def main():
    cp_file, wanted, search = sys.argv[1], sys.argv[2].split(), sys.argv[3].strip()
    code = sys.argv[4].split() if len(sys.argv) > 4 else []
    files = sys.argv[5].strip() if len(sys.argv) > 5 else ""
    jars = jar_list(cp_file)
    classes = all_classes(jars)
    out = []
    if search:
        pattern = re.compile(search)
        found = sorted({c for c in classes if "$" not in c and pattern.search(c)})
        out.append(f"== classes matching {search} ({len(found)}) ==")
        out.extend(found)
    names = expand(wanted, classes)
    if names:
        out.extend(javap(jars, names, ["-private"]))
    if code:
        out.append("== bytecode ==")
        out.extend(javap(jars, expand(code, classes), ["-private", "-c"]))
    if files:
        out.extend(resources(jars, files))
    with open("api.txt", "w") as f:
        f.write("\n".join(out))


if __name__ == "__main__":
    main()
