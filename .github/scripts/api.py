#!/usr/bin/env python3
"""API lookup for development: lists classes and prints member signatures (no code) from the compile classpath
(Minecraft, Fabric API and libraries), into api.txt.

    api.py <classpath file> "<class names>" "<search regex>"

A class name ending in ".*" means every class in that package (not sub-packages).
"""
import re
import subprocess
import sys
import zipfile


def all_classes(jars):
    names = []
    for jar in jars:
        if not jar.endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    if name.endswith(".class") and name != "module-info.class":
                        names.append(name[:-6].replace("/", "."))
        except (OSError, zipfile.BadZipFile):
            pass
    return names


def main():
    cp_file, wanted, search = sys.argv[1], sys.argv[2].split(), sys.argv[3].strip()
    with open(cp_file) as f:
        jars = [line[3:].strip() for line in f if line.startswith("CP ")]
    classes = all_classes(jars)
    out = []
    if search:
        pattern = re.compile(search)
        found = sorted({c for c in classes if "$" not in c and pattern.search(c)})
        out.append(f"== classes matching {search} ({len(found)}) ==")
        out.extend(found)
    expanded = []
    for name in wanted:
        if name.endswith(".*"):
            package = name[:-2]
            expanded.extend(sorted(c for c in classes
                                   if c.rsplit(".", 1)[0] == package and "$" not in c))
        else:
            expanded.append(name)
    for i in range(0, len(expanded), 40):
        batch = expanded[i:i + 40]
        result = subprocess.run(["javap", "-private", "-cp", ":".join(jars), *batch],
                                capture_output=True, text=True)
        out.append(result.stdout)
        if result.stderr.strip():
            out.append("javap: " + result.stderr.strip())
    with open("api.txt", "w") as f:
        f.write("\n".join(out))


if __name__ == "__main__":
    main()
