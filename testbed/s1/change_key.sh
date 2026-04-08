#!/bin/sh
set -eu

usage() {
    echo "Usage: $0 <plain|128|192|256|bacnet-sc>" >&2
    echo "Env: NO_EGRESS_METRICS=1 (AES only) disables egress register writes" >&2
    exit 1
}

[ $# -eq 1 ] || usage
MODE="$1"
NO_EGRESS_METRICS="${NO_EGRESS_METRICS:-0}"

case "$NO_EGRESS_METRICS" in
    0|1) ;;
    *)
        echo "NO_EGRESS_METRICS must be 0 or 1 (got: $NO_EGRESS_METRICS)" >&2
        exit 1
        ;;
esac

write_base_key() {
    simple_switch_CLI <<'EOF'
register_write keys 0 729683222
register_write keys 1 682545830
register_write keys 2 2885096840
register_write keys 3 164581180
EOF
}

apply_key_tail() {
    k4="$1"
    k5="$2"
    k6="$3"
    k7="$4"
    simple_switch_CLI <<EOF
register_write keys 4 $k4
register_write keys 5 $k5
register_write keys 6 $k6
register_write keys 7 $k7
EOF
}

disable_in_network_cipher() {
    simple_switch_CLI <<'EOF'
table_clear bacnet_sec
EOF
}

enable_in_network_cipher() {
    simple_switch_CLI <<'EOF'
table_clear bacnet_sec
table_add bacnet_sec decipher 1 =>
table_add bacnet_sec cipher 2 =>
EOF
}

enable_in_network_cipher_without_egress_metrics() {
    simple_switch_CLI <<'EOF'
table_clear bacnet_sec
table_add bacnet_sec decipher_no_metrics 1 =>
table_add bacnet_sec cipher_no_metrics 2 =>
EOF
}

enable_in_network_cipher_for_mode() {
    if [ "$NO_EGRESS_METRICS" = "1" ]; then
        enable_in_network_cipher_without_egress_metrics
    else
        enable_in_network_cipher
    fi
}

write_base_key

case "$MODE" in
    plain|bacnet-sc)
        apply_key_tail 102358694 259174683 243695780 096548217
        disable_in_network_cipher
        ;;
    128)
        apply_key_tail 0 0 0 0
        enable_in_network_cipher_for_mode
        ;;
    192)
        apply_key_tail 102358694 259174683 0 0
        enable_in_network_cipher_for_mode
        ;;
    256)
        apply_key_tail 102358694 259174683 243695780 096548217
        enable_in_network_cipher_for_mode
        ;;
    *)
        usage
        ;;
esac
