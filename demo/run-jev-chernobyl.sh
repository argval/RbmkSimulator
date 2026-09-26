#!/usr/bin/env bash
# Compile and run the headless Chernobyl AZ-5 before/after.
# Does not build the Swing UI or the thermal network.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/rbmk-jev-demo-classes"
mkdir -p "$OUT"

javac --release 17 -d "$OUT" \
  "$ROOT/src/com/hartrusion/rbmksim/NeutronFluxModel.java" \
  "$ROOT/src/com/hartrusion/rbmksim/DisplacerAccident.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/JevUnavailableException.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/Az5PlantState.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/Az5Decision.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/JevJson.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/JevQuestions.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/LocalJevStandIn.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/JevClient.java" \
  "$ROOT/src/com/hartrusion/rbmksim/jev/Az5Guard.java" \
  "$ROOT/src/com/hartrusion/rbmksim/ChernobylAccidentDemo.java"

exec java -cp "$OUT" com.hartrusion.rbmksim.ChernobylAccidentDemo "${1:-both}"
