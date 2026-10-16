#!/bin/bash
# Downloads the dev kit (a JDK 25 and signature-only stubs of Minecraft / Fabric API / libraries) from the dev-kit
# branch into .devkit/ (run the "Dev kit (development)" workflow first if the branch is missing or out of date).
set -e
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
KIT="${KIT:-$REPO/.devkit}"
rm -rf "$KIT" && mkdir -p "$KIT"
git -C "$REPO" fetch -q --depth=1 origin dev-kit
git -C "$REPO" archive FETCH_HEAD | tar -x -C "$KIT"
cat "$KIT"/jdk/jdk.tar.xz.part-* | xz -d | tar -x -C "$KIT"
rm -rf "$KIT"/jdk/jdk.tar.xz.part-*
echo "Kit ready in $KIT: $("$KIT/jdk/bin/javac" -version 2>&1 | tail -1)"
