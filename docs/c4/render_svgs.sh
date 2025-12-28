#!/usr/bin/env bash
set -euo pipefail

# Render all C4 .puml diagrams under docs/c4 into SVG files under docs/c4/out.
# Requirements (offline):
# - plantuml command available (e.g., via brew) OR provide PLANTUML_JAR.
# - Java available when using PLANTUML_JAR.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
C4_DIR="$ROOT_DIR/docs/c4"

mkdir -p "$C4_DIR/out"

if command -v plantuml >/dev/null 2>&1; then
  plantuml -tsvg -charset UTF-8 -o out "$C4_DIR"/*.puml
  echo "Rendered SVGs to $C4_DIR/out (via plantuml command)."
  exit 0
fi

if [[ -n "${PLANTUML_JAR:-}" ]]; then
  if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found, but PLANTUML_JAR is set." >&2
    exit 1
  fi
  java -jar "$PLANTUML_JAR" -tsvg -charset UTF-8 -o out "$C4_DIR"/*.puml
  echo "Rendered SVGs to $C4_DIR/out (via PLANTUML_JAR)."
  exit 0
fi

echo "ERROR: plantuml command not found and PLANTUML_JAR is not set." >&2
echo "Install PlantUML (e.g., 'brew install plantuml') or set PLANTUML_JAR to a local plantuml.jar path." >&2
exit 1
