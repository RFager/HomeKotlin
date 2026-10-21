#!/usr/bin/env bash
set -e

echo "=================================================="
echo " Starting HomeKotlin Home Assistant Add-on"
echo "=================================================="

# Check if running under Home Assistant Supervisor
if [ -f "/data/options.json" ]; then
    echo "Loading options from /data/options.json"
    AUTOMATIONS_DIR=$(jq --raw-output '.automations_dir // empty' /data/options.json)
    LOG_LEVEL=$(jq --raw-output '.log_level // "INFO"' /data/options.json)
    
    export AUTOMATIONS_DIR="${AUTOMATIONS_DIR:-/config/homekotlin/automations}"
    export LOG_LEVEL="${LOG_LEVEL:-INFO}"
fi

# Ensure automations directory exists
mkdir -p "${AUTOMATIONS_DIR:-/config/homekotlin/automations}"

echo "Automations directory: ${AUTOMATIONS_DIR}"
echo "Launching HomeKotlin application..."

exec /app/bin/homekotlin
