#!/bin/sh
set -eu

output="$(simple_switch_CLI 2>&1 <<'EOF'
help
EOF
)"

case "$output" in
    *"Could not connect to thrift client"*|*"Could not connect to any of "*)
        exit 1
        ;;
    *)
        exit 0
        ;;
esac
