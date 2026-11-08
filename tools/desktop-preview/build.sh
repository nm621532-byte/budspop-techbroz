#!/usr/bin/env bash
# Builds and runs the BudsPop desktop preview renderer (same BudsScene/BudsFight code as the APK).
# usage: build.sh <outDir> <fps> <secondsPerTheme>
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../../app/src/main/java/com/techbroz/budspop"
out="$(mktemp -d /tmp/budspop-classes.XXXX)"
javac -d "$out" "$here"/shim/android/graphics/*.java "$src/BudsScene.java" "$src/BudsFight.java" "$here/FrameDump.java"
java -cp "$out" FrameDump "$1" "$2" "$3"
