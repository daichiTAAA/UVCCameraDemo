#!/usr/bin/env bash
set -euo pipefail

# Generates OpenAPI (Swagger) JSON for the ASP.NET webserver.
# Output: docs/openapi/webserver.swagger.json

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/webserver/WebServer.csproj"
OUT_DIR="$ROOT_DIR/docs/openapi"
OUT_FILE="$OUT_DIR/webserver.swagger.json"
OVERLAY_FILE="$OUT_DIR/overlays/tusd.paths.json"
MERGE_SCRIPT="$ROOT_DIR/scripts/merge_openapi.py"

PORT="${PORT:-5161}"
BASE_URL="http://127.0.0.1:${PORT}"
SWAGGER_URL="${BASE_URL}/swagger/v1/swagger.json"

mkdir -p "$OUT_DIR"

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

LOG_FILE="$OUT_DIR/.openapi_export_server.log"

ASPNETCORE_ENVIRONMENT=Development \
OPENAPI_EXPORT=true \
dotnet run --project "$PROJECT" --urls "$BASE_URL" >"$LOG_FILE" 2>&1 &
SERVER_PID=$!

# Wait for swagger endpoint
for i in {1..60}; do
  if curl -fsS "$SWAGGER_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
  if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    echo "webserver exited early. See: $LOG_FILE" >&2
    exit 1
  fi
  if [[ $i -eq 60 ]]; then
    echo "timed out waiting for swagger endpoint: $SWAGGER_URL" >&2
    echo "See: $LOG_FILE" >&2
    exit 1
  fi
done

curl -fsS "$SWAGGER_URL" -o "$OUT_FILE"

# Optionally inject additional paths that are served by reverse-proxy (e.g. tusd /files/*)
if [[ -f "$OVERLAY_FILE" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    python3 "$MERGE_SCRIPT" "$OUT_FILE" "$OVERLAY_FILE" "$OUT_FILE"
  else
    echo "python3 not found; skipping OpenAPI overlay merge: $OVERLAY_FILE" >&2
  fi
fi

echo "Wrote: $OUT_FILE"
