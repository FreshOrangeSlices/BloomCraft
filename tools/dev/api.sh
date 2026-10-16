#!/bin/bash
# Looks up the game / Fabric API in the dev kit stubs.
#   api.sh find <regex>        class names matching
#   api.sh <class> [class...]  member signatures (javap -p)
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
KIT="${KIT:-$REPO/.devkit}"
CP=$(ls "$KIT"/stubs/*.jar | paste -sd: -)
LIST="$KIT/classes.txt"
if [ ! -s "$LIST" ]; then
  for j in "$KIT"/stubs/*.jar; do unzip -Z1 "$j" 2>/dev/null | grep '\.class$' | sed 's/\.class$//; s#/#.#g'; done | sort -u > "$LIST"
fi
if [ "$1" = "find" ]; then
  grep -E "$2" "$LIST" | grep -v '\$[0-9]' | head -${3:-200}
else
  "$KIT/jdk/bin/javap" -p -cp "$CP" "$@" 2>&1 | grep -v '^Picked up'
fi
