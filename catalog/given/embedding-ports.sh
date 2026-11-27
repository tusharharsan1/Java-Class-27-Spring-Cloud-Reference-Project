#!/usr/bin/env bash
set -euo pipefail
curl -fsS --max-time 5 -H 'Accept: application/json' http://127.0.0.1:8761/eureka/apps |
python3 -c 'import json,sys
apps = json.load(sys.stdin)["applications"].get("application", [])
entries = [i for app in apps if app["name"] == "EMBEDDING-SERVICE" for i in app["instance"]]
for item in entries:
    print(f"{item['\''instanceId'\'']} {item['\''ipAddr'\'']}:{item['\''port'\'']['\''$'\'']} {item['\''status'\'']}")
if not entries:
    print("No embedding instance in Eureka")'
