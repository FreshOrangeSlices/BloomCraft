#!/usr/bin/env python3
"""API lookup for development: lists classes and prints member signatures from the compile classpath
(Minecraft, Fabric API and libraries), and writes the result to api.txt for the notice steps.

    api.py <classpath file> "<class names>" "<search regex>"
"""
import re
import subprocess
import sys
import zipfile

def main():
    cp_file, classes, search = sys.argv[1], sys.argv[2].split(), sys.argv[3].strip()
    with open(cp_file) as f:
        jars = [line[3:].strip() for line in f if line.startswith("CP ")]
    out = []
    if search:
        pattern = re.compile(search)
        found = []
        for jar in jars:
            if not jar.endswith(".jar"):
                continue
            try:
                with zipfile.ZipFile(jar) as z:
                    for name in z.namelist():
                        if name.endswith(".class") and "$" not in name:
                            cls = name[:-6].replace("/", ".")
                            if pattern.search(cls):
                                found.append(cls)
            except (OSError, zipfile.BadZipFile):
                pass
        out.append(f"== classes matching {search} ({len(found)}) ==")
        out.extend(sorted(set(found)))
    if classes:
        result = subprocess.run(["javap", "-protected", "-cp", ":".join(jars), *classes],
                                capture_output=True, text=True)
        out.append(result.stdout)
        if result.stderr.strip():
            out.append("javap: " + result.stderr.strip())
    with open("api.txt", "w") as f:
        f.write("\n".join(out))


if __name__ == "__main__":
    main()
