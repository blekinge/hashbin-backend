#!/usr/bin/env bash
#
# Phase 5 compatibility check: drives the REAL Python CLI from the Lovable
# repository against this Quarkus backend, proving the port is a drop-in
# replacement for the original Supabase edge function.
#
# Prerequisites:
#   - This service running (e.g. `mvn quarkus:dev`, or the built jar)
#   - PostgreSQL reachable (dev: docker compose up -d)
#   - python3 on PATH
#
# Env overrides:
#   HASHBIN_API_BASE  default http://localhost:8093/api
#   HASHBIN_CLI       path to the original cli/hashbin script
#                     default ../hashbin-time-vault/cli/hashbin
#   HASHBIN_HMAC_SECRET  must match the running server's secret so the
#                     signature re-check passes (dev default below).
set -euo pipefail

API_BASE="${HASHBIN_API_BASE:-http://localhost:8093/api}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_SRC="${HASHBIN_CLI:-$HERE/../../hashbin-time-vault/cli/hashbin}"
SECRET="${HASHBIN_HMAC_SECRET:-dev-only-insecure-secret-change-me}"

command -v python3 >/dev/null || { echo "python3 required"; exit 1; }
[ -f "$CLI_SRC" ] || { echo "CLI not found at $CLI_SRC (set HASHBIN_CLI)"; exit 1; }

# Patch only a throwaway copy; never edit the original CLI.
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp "$CLI_SRC" "$WORK/hashbin"
sed -i "s#^API_BASE = \".*\"#API_BASE = \"$API_BASE\"#" "$WORK/hashbin"

pass() { echo "  PASS: $1"; }
fail() { echo "  FAIL: $1"; exit 1; }

echo "== target: $API_BASE"
echo "== cli:    $CLI_SRC"

# 1. health
python3 "$WORK/hashbin" health | grep -q "healthy" || fail "health check"
pass "health"

# 2. stamp a generated file
printf 'hashbin compatibility probe %s\n' "$(date -u +%s%N)" > "$WORK/file.bin"
STAMP_OUT="$(python3 "$WORK/hashbin" stamp "$WORK/file.bin")"
echo "$STAMP_OUT" | grep -q "Timestamped at" || fail "stamp (no timestamp returned)"
SHA="$(grep 'SHA-256:' <<<"$STAMP_OUT" | awk '{print $2}')"
pass "stamp (sha256=${SHA:0:12}…)"

# 3. verify by file path
python3 "$WORK/hashbin" verify "$WORK/file.bin" | grep -q "Found 1 timestamp" || fail "verify by file"
pass "verify by file"

# 4. verify by raw hash + signature integrity
JSON_FILE="$WORK/verify.json"
curl -fsS "$API_BASE/verify?hash=$SHA" -o "$JSON_FILE"
python3 - "$SHA" "$SECRET" "$JSON_FILE" <<'PY'
import sys, hmac, hashlib, json
sha, secret, path = sys.argv[1], sys.argv[2], sys.argv[3]
rows = json.load(open(path))
match = next((r for r in rows if r["hash_sha256"] == sha), None)
assert match, "hash not found in verify results"
exp = hmac.new(secret.encode(), f"{sha}:{match['created_at']}".encode(), hashlib.sha256).hexdigest()
assert exp == match["server_signature"], "signature mismatch"
print(f"  PASS: signature recomputes for {sha[:12]}…")
PY

echo "ALL COMPATIBILITY CHECKS PASSED"
