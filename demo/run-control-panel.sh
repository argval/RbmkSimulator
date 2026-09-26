#!/usr/bin/env bash
# Launch the existing "RBMK Simulator - Control Panel" in the accident-test
# layout. One scenario per process. Does not print TYPESAFE_API_KEY.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODE="${1:-}"
case "$MODE" in
  incident|unguarded)
    export JEV_GUARD=off
    ;;
  jev|guarded|prevent)
    export JEV_GUARD=on
    export JEV_MODE="${JEV_MODE:-auto}"
    ;;
  *)
    echo "usage: demo/run-control-panel.sh incident|jev" >&2
    exit 2
    ;;
esac

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

export RBMK_ACCIDENT_TEST=1

if [[ "$MODE" == "incident" || "$MODE" == "unguarded" ]]; then
  echo "Scenario: unguarded AZ-5. JEV_GUARD=off. This run does not call Jev."
else
  if [[ -n "${TYPESAFE_API_KEY:-}" ]]; then
    echo "Scenario: Jev in the loop. JEV_MODE=${JEV_MODE}. TYPESAFE_API_KEY is set and is not printed."
  else
    echo "Scenario: guard on, but TYPESAFE_API_KEY is not set. AZ-5 will use the local stand-in."
  fi
fi

if [[ ! -f lib/RbmkSimulator-0.4.6.jar ]]; then
  echo "Missing lib/RbmkSimulator-0.4.6.jar (the v0.4.6 release fat jar)." >&2
  exit 1
fi

mkdir -p build/classes
# Current sources override the classes inside the fat jar. Resources stay in
# the jar unless this checkout has a newer copy.
find src -name '*.java' > build/sources.list
javac --release 17 -encoding UTF-8 \
  -cp "lib/RbmkSimulator-0.4.6.jar" \
  -d build/classes \
  @build/sources.list

while IFS= read -r -d '' resource; do
  rel="${resource#src/}"
  dest="build/classes/$rel"
  mkdir -p "$(dirname "$dest")"
  cp "$resource" "$dest"
done < <(find src -type f ! -name '*.java' -print0)

echo "Click AZ-5 on Reactor Controls. Close the window before the other scenario."
exec java -cp "build/classes:lib/RbmkSimulator-0.4.6.jar" \
  com.hartrusion.rbmksim.RbmkSimulator
