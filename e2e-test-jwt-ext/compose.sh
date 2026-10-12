#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

exec docker compose -f "$script_dir/compose.yml" "$@"

#curl -X post http://localhost:8187/oauth/token -d "source_app=prot-jwt-ext-testing" | jq .access_token