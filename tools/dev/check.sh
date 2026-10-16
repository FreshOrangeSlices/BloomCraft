#!/bin/bash
# Type-checks the mod against the dev kit: main, then client, then gametest (like Gradle), in a few seconds.
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
KIT="${KIT:-$REPO/.devkit}"
OUT="${OUT:-$KIT/out}"
JAVAC="$KIT/jdk/bin/javac"
cp_of() { grep "^$1 " "$KIT/stubs/classpath.txt" | grep -v ' DIR ' | awk -v d="$KIT/stubs" '{print d"/"$2}' | paste -sd: -; }
rm -rf "$OUT"; mkdir -p "$OUT/main" "$OUT/client" "$OUT/test"
FLAGS=(-proc:none -nowarn -encoding UTF-8 --release 25 -Xmaxerrs 500 -Xlint:none -XDshould-stop.ifError=FLOW)
status=0
run() { # set classpath out sources
  find "$REPO/src/$3/java" -name '*.java' > "$OUT/$3.txt"
  [ -s "$OUT/$3.txt" ] || return 0
  "$JAVAC" "${FLAGS[@]}" -cp "$1" -d "$2" @"$OUT/$3.txt" 2>&1 | grep -v '^Picked up' || true
  [ "${PIPESTATUS[0]}" -ne 0 ] && status=1
}
run "$(cp_of MAIN)" "$OUT/main" main
[ -d "$REPO/src/client/java" ] && run "$(cp_of CLIENT):$OUT/main" "$OUT/client" client
[ -d "$REPO/src/gametest/java" ] && run "$(cp_of TEST):$OUT/main:$OUT/client" "$OUT/test" gametest
python3 "$(dirname "$0")/mixincheck.py" "$REPO" || status=1
[ $status -eq 0 ] && echo "CHECK OK" || echo "CHECK FAILED"
exit $status
