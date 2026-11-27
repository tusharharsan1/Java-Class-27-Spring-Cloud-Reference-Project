#!/usr/bin/env bash
set -eu
body=$(mktemp)
trap 'rm -f "$body"' EXIT
status=$(curl -sS --max-time 9 -G -o "$body" -w '%{http_code}' \
   --data-urlencode "query=${1:-lipstick}" 'http://127.0.0.1:8080/products/search/meaning')
printf 'HTTP %s\n' "$status"
if [ "$status" != 200 ]; then
    cat "$body"
    exit 1
fi
python3 -c 'import json,sys; rows=json.load(sys.stdin); print(json.dumps(rows[0]) if rows else "No results")' < "$body"
