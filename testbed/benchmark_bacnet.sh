#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

usage() {
    cat <<USAGE
Usage: $0 [--all | --plain --aes-128 --aes-192 --aes-256 --bacnet-sc] [--rtt] [--ppt] [--deq] [--no-egress-metrics] [--noterminals]

Scenarios:
  --plain        Run BACnet plain scenario
  --aes-128      Run in-network AES with 128-bit key
  --aes-192      Run in-network AES with 192-bit key
  --aes-256      Run in-network AES with 256-bit key
  --bacnet-sc    Run BACnet/SC over TLS scenario
  --all          Run all five scenarios

Metrics:
  --rtt          RTT measurement (always enabled)
  --ppt          Packet processing time (AES scenarios only)
  --deq          Egress dequeuing timedelta (AES scenarios only)

Other:
  --no-egress-metrics  AES only: disable egress register writes in P4
  --noterminals  Pass --noterminals to kathara lstart

BACnet/SC environment:
  BACNET_SC_TLS_VERSION   TLS version for generated SC configs (default: TLSv1.3)
  BACNET_SC_WORKLOAD_ROWS Limit full workload rows for smoke tests (default: 0 = all)
USAGE
}

SCENARIOS=()
RUN_PPT=0
RUN_DEQ=0
DISABLE_EGRESS_METRICS=0
KATHARA_OPTIONS=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --all)
            SCENARIOS=("bacnet-plain" "aes-128" "aes-192" "aes-256" "bacnet-sc-tls")
            shift
            ;;
        --plain)
            SCENARIOS+=("bacnet-plain")
            shift
            ;;
        --aes-128)
            SCENARIOS+=("aes-128")
            shift
            ;;
        --aes-192)
            SCENARIOS+=("aes-192")
            shift
            ;;
        --aes-256)
            SCENARIOS+=("aes-256")
            shift
            ;;
        --bacnet-sc)
            SCENARIOS+=("bacnet-sc-tls")
            shift
            ;;
        --rtt)
            shift
            ;;
        --ppt)
            RUN_PPT=1
            shift
            ;;
        --deq)
            RUN_DEQ=1
            shift
            ;;
        --no-egress-metrics)
            DISABLE_EGRESS_METRICS=1
            shift
            ;;
        --noterminals)
            KATHARA_OPTIONS="--noterminals"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if [[ ${#SCENARIOS[@]} -eq 0 ]]; then
    echo "At least one scenario must be selected." >&2
    usage
    exit 1
fi

# De-duplicate scenarios while preserving order.
UNIQ_SCENARIOS=()
for scenario in "${SCENARIOS[@]}"; do
    skip=0
    for s in "${UNIQ_SCENARIOS[@]}"; do
        [[ "$s" == "$scenario" ]] && skip=1 && break
    done
    [[ $skip -eq 0 ]] && UNIQ_SCENARIOS+=("$scenario")
done
SCENARIOS=("${UNIQ_SCENARIOS[@]}")

SHARED_DIR="$SCRIPT_DIR/shared"
RESULTS_DIR="$SHARED_DIR"
SUMMARY_CSV="$RESULTS_DIR/benchmark_summary.csv"
SUMMARY_JSON="$RESULTS_DIR/benchmark_summary.json"
DATASET_SOURCE="$SCRIPT_DIR/HVAC-minute.csv"
DATASET_TARGET="$SHARED_DIR/HVAC-minute.csv"
DATASET_DOWNLOAD_URL="https://s3.amazonaws.com/nist-netzero/2014-data-files/HVAC-minute.csv"

mkdir -p "$SHARED_DIR"

if [[ ! -f "$DATASET_SOURCE" ]]; then
    echo "Dataset file not found: $DATASET_SOURCE" >&2
    echo "Please download HVAC-minute.csv from: $DATASET_DOWNLOAD_URL" >&2
    exit 1
fi

if ! cp -f "$DATASET_SOURCE" "$DATASET_TARGET"; then
    echo "Failed to copy dataset to shared folder: $DATASET_TARGET" >&2
    echo "If HVAC-minute.csv is missing, download it from: $DATASET_DOWNLOAD_URL" >&2
    exit 1
fi

# Remove stale outputs for deterministic runs.
rm -f "$RESULTS_DIR"/results_rtt_*.txt
rm -f "$RESULTS_DIR"/results_s1_*packet_processing_time*.txt
rm -f "$RESULTS_DIR"/results_s2_*packet_processing_time*.txt
rm -f "$RESULTS_DIR"/results_s1_*packet_dequeuing_timedelta*.txt
rm -f "$RESULTS_DIR"/results_s2_*packet_dequeuing_timedelta*.txt
rm -f "$SUMMARY_CSV" "$SUMMARY_JSON"

wait_for_machine() {
    local machine="$1"
    local max_attempts=60
    local attempt

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        if kathara exec "$machine" "true" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    echo "Machine $machine did not become ready in time" >&2
    return 1
}

wait_for_switch_cli() {
    local machine="$1"
    local max_attempts=180
    local attempt

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        if kathara exec "$machine" "./wait_switch_cli.sh" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    echo "Switch CLI on $machine did not become ready in time" >&2
    return 1
}

wait_for_bacnet_sc_hub() {
    local max_attempts=120
    local attempt

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        if kathara exec bacnetserver "sh -lc 'grep -q \"090101C8:115B\" /proc/net/tcp'" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    echo "BACnet/SC hub did not start listening on 200.1.1.9:4443 in time" >&2
    return 1
}

wait_for_bacnet_sc_hub_stable() {
    local hub_log="$1"
    local max_attempts="${BACNET_SC_HUB_STABLE_TIMEOUT_SECONDS:-60}"
    local attempt

    if ! [[ "$max_attempts" =~ ^[0-9]+$ ]] || [[ "$max_attempts" -lt 1 ]]; then
        max_attempts=60
    fi

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        if kathara exec bacnetserver "sh -lc 'grep -q \"SC-1-HF:  started\" \"$hub_log\"'" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    echo "BACnet/SC hub did not report startup in time (continuing with fallback delay)" >&2
    return 1
}

wait_for_bacnet_sc_server_connected() {
    local server_log="$1"
    local server_device="${2:-bacnetserver}"
    local max_attempts="${BACNET_SC_SERVER_CONNECT_TIMEOUT_SECONDS:-180}"
    local attempt

    if ! [[ "$max_attempts" =~ ^[0-9]+$ ]] || [[ "$max_attempts" -lt 1 ]]; then
        max_attempts=180
    fi

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        if kathara exec "$server_device" "sh -lc 'grep -q \"Connected to primary hub\" \"$server_log\"'" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    echo "BACnet/SC server node did not connect to primary hub in time" >&2
    return 1
}

wait_for_rtt_growth() {
    local file_path="$1"
    local min_lines="$2"
    local timeout_seconds="$3"
    local elapsed=0

    while [[ "$elapsed" -lt "$timeout_seconds" ]]; do
        if [[ -f "$file_path" ]]; then
            local lines
            lines="$(wc -l < "$file_path" 2>/dev/null || echo 0)"
            if [[ "$lines" -ge "$min_lines" ]]; then
                return 0
            fi
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    return 1
}

prepare_bacnet_sc_client_config() {
    local dest_cfg="$1"
    local max_rows="$2"
    local connection_wait_timeout="$3"
    local tls_version="$4"

    kathara exec bacnetclient "sh -lc '
        cp /bacnet/bacnet-sc/config/kathara/BenchmarkClient.properties ${dest_cfg}
        if grep -q \"^app.maxRows\" ${dest_cfg}; then
            sed -i \"s/^app.maxRows[[:space:]]*=.*/app.maxRows = ${max_rows}/\" ${dest_cfg}
        else
            printf \"\\napp.maxRows = ${max_rows}\\n\" >> ${dest_cfg}
        fi
        if grep -q \"^sc.connectionWaitTimeout\" ${dest_cfg}; then
            sed -i \"s/^sc.connectionWaitTimeout[[:space:]]*=.*/sc.connectionWaitTimeout = ${connection_wait_timeout}/\" ${dest_cfg}
        else
            printf \"\\nsc.connectionWaitTimeout = ${connection_wait_timeout}\\n\" >> ${dest_cfg}
        fi
        if grep -q \"^sc.tlsVersion\" ${dest_cfg}; then
            sed -i \"s/^sc.tlsVersion[[:space:]]*=.*/sc.tlsVersion = ${tls_version}/\" ${dest_cfg}
        else
            printf \"\\nsc.tlsVersion = ${tls_version}\\n\" >> ${dest_cfg}
        fi
    '"
}

prepare_bacnet_sc_server_configs() {
    local hub_cfg="$1"
    local server_cfg="$2"
    local connection_wait_timeout="$3"
    local server_primary_hub_uri="$4"
    local tls_version="$5"

    kathara exec bacnetserver "sh -lc '
        cp /bacnet/bacnet-sc/config/kathara/BenchmarkHub.properties ${hub_cfg}

        if grep -q \"^sc.nodeEnable\" ${hub_cfg}; then
            sed -i \"s/^sc.nodeEnable[[:space:]]*=.*/sc.nodeEnable = false/\" ${hub_cfg}
        else
            printf \"\\nsc.nodeEnable = false\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.primaryHubURI\" ${hub_cfg}; then
            sed -i \"s|^sc.primaryHubURI[[:space:]]*=.*|sc.primaryHubURI = |\" ${hub_cfg}
        else
            printf \"\\nsc.primaryHubURI = \\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.failoverHubURI\" ${hub_cfg}; then
            sed -i \"s|^sc.failoverHubURI[[:space:]]*=.*|sc.failoverHubURI = |\" ${hub_cfg}
        else
            printf \"\\nsc.failoverHubURI = \\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.serverStartupDelay\" ${hub_cfg}; then
            sed -i \"s/^sc.serverStartupDelay[[:space:]]*=.*/sc.serverStartupDelay = 5000/\" ${hub_cfg}
        else
            printf \"\\nsc.serverStartupDelay = 5000\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.connectionWaitTimeout\" ${hub_cfg}; then
            sed -i \"s/^sc.connectionWaitTimeout[[:space:]]*=.*/sc.connectionWaitTimeout = ${connection_wait_timeout}/\" ${hub_cfg}
        else
            printf \"\\nsc.connectionWaitTimeout = ${connection_wait_timeout}\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.tlsVersion\" ${hub_cfg}; then
            sed -i \"s/^sc.tlsVersion[[:space:]]*=.*/sc.tlsVersion = ${tls_version}/\" ${hub_cfg}
        else
            printf \"\\nsc.tlsVersion = ${tls_version}\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^device.instance\" ${hub_cfg}; then
            sed -i \"s/^device.instance[[:space:]]*=.*/device.instance = 555010/\" ${hub_cfg}
        else
            printf \"\\ndevice.instance = 555010\\n\" >> ${hub_cfg}
        fi
    '"

    kathara exec bacnetclient "sh -lc '
        cp /bacnet/bacnet-sc/config/TestNode.properties ${server_cfg}

        if grep -q \"^common.shell\" ${server_cfg}; then
            sed -i \"s/^common.shell[[:space:]]*=.*/common.shell = console/\" ${server_cfg}
        else
            printf \"\\ncommon.shell = console\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.hubFunctionEnable\" ${server_cfg}; then
            sed -i \"s/^sc.hubFunctionEnable[[:space:]]*=.*/sc.hubFunctionEnable = false/\" ${server_cfg}
        else
            printf \"\\nsc.hubFunctionEnable = false\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.hubFunctionBindURI\" ${server_cfg}; then
            sed -i \"s|^sc.hubFunctionBindURI[[:space:]]*=.*|sc.hubFunctionBindURI = |\" ${server_cfg}
        else
            printf \"\\nsc.hubFunctionBindURI = \\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.nodeEnable\" ${server_cfg}; then
            sed -i \"s/^sc.nodeEnable[[:space:]]*=.*/sc.nodeEnable = true/\" ${server_cfg}
        else
            printf \"\\nsc.nodeEnable = true\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.directConnectEnable\" ${server_cfg}; then
            sed -i \"s/^sc.directConnectEnable[[:space:]]*=.*/sc.directConnectEnable = false/\" ${server_cfg}
        else
            printf \"\\nsc.directConnectEnable = false\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.directConnectBindURI\" ${server_cfg}; then
            sed -i \"s|^sc.directConnectBindURI[[:space:]]*=.*|sc.directConnectBindURI = |\" ${server_cfg}
        else
            printf \"\\nsc.directConnectBindURI = \\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.directConnectAcceptURIs\" ${server_cfg}; then
            sed -i \"s|^sc.directConnectAcceptURIs[[:space:]]*=.*|sc.directConnectAcceptURIs = |\" ${server_cfg}
        else
            printf \"\\nsc.directConnectAcceptURIs = \\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.primaryHubURI\" ${server_cfg}; then
            sed -i \"s|^sc.primaryHubURI[[:space:]]*=.*|sc.primaryHubURI = ${server_primary_hub_uri}|\" ${server_cfg}
        else
            printf \"\\nsc.primaryHubURI = ${server_primary_hub_uri}\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.failoverHubURI\" ${server_cfg}; then
            sed -i \"s|^sc.failoverHubURI[[:space:]]*=.*|sc.failoverHubURI = |\" ${server_cfg}
        else
            printf \"\\nsc.failoverHubURI = \\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.serverStartupDelay\" ${server_cfg}; then
            sed -i \"s/^sc.serverStartupDelay[[:space:]]*=.*/sc.serverStartupDelay = 5000/\" ${server_cfg}
        else
            printf \"\\nsc.serverStartupDelay = 5000\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.connectionWaitTimeout\" ${server_cfg}; then
            sed -i \"s/^sc.connectionWaitTimeout[[:space:]]*=.*/sc.connectionWaitTimeout = ${connection_wait_timeout}/\" ${server_cfg}
        else
            printf \"\\nsc.connectionWaitTimeout = ${connection_wait_timeout}\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.tlsVersion\" ${server_cfg}; then
            sed -i \"s/^sc.tlsVersion[[:space:]]*=.*/sc.tlsVersion = ${tls_version}/\" ${server_cfg}
        else
            printf \"\\nsc.tlsVersion = ${tls_version}\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.vmac\" ${server_cfg}; then
            sed -i \"s/^sc.vmac[[:space:]]*=.*/sc.vmac = 222222222222/\" ${server_cfg}
        else
            printf \"\\nsc.vmac = 222222222222\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.privateKey\" ${server_cfg}; then
            sed -i \"s|^sc.privateKey[[:space:]]*=.*|sc.privateKey = config/TestNode.key|\" ${server_cfg}
        else
            printf \"\\nsc.privateKey = config/TestNode.key\\n\" >> ${server_cfg}
        fi
        if grep -q \"^sc.operationalCertificate\" ${server_cfg}; then
            sed -i \"s|^sc.operationalCertificate[[:space:]]*=.*|sc.operationalCertificate = config/TestNode.pem|\" ${server_cfg}
        else
            printf \"\\nsc.operationalCertificate = config/TestNode.pem\\n\" >> ${server_cfg}
        fi
        if grep -q \"^device.instance\" ${server_cfg}; then
            sed -i \"s/^device.instance[[:space:]]*=.*/device.instance = 555001/\" ${server_cfg}
        else
            printf \"\\ndevice.instance = 555001\\n\" >> ${server_cfg}
        fi
        if grep -q \"^device.uuid\" ${server_cfg}; then
            sed -i \"s/^device.uuid[[:space:]]*=.*/device.uuid = 3c5ff1d1-fbfc-4379-b67b-711ea57d6a4f/\" ${server_cfg}
        else
            printf \"\\ndevice.uuid = 3c5ff1d1-fbfc-4379-b67b-711ea57d6a4f\\n\" >> ${server_cfg}
        fi
        if grep -q \"^device.namePrefix\" ${server_cfg}; then
            sed -i \"s/^device.namePrefix[[:space:]]*=.*/device.namePrefix = KTServer/\" ${server_cfg}
        else
            printf \"\\ndevice.namePrefix = KTServer\\n\" >> ${server_cfg}
        fi
    '"
}

prepare_bacnet_sc_single_process_config() {
    local hub_cfg="$1"
    local connection_wait_timeout="$2"
    local tls_version="$3"

    kathara exec bacnetserver "sh -lc '
        cp /bacnet/bacnet-sc/config/kathara/BenchmarkHub.properties ${hub_cfg}

        if grep -q \"^sc.nodeEnable\" ${hub_cfg}; then
            sed -i \"s/^sc.nodeEnable[[:space:]]*=.*/sc.nodeEnable = true/\" ${hub_cfg}
        else
            printf \"\\nsc.nodeEnable = true\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.hubFunctionEnable\" ${hub_cfg}; then
            sed -i \"s/^sc.hubFunctionEnable[[:space:]]*=.*/sc.hubFunctionEnable = true/\" ${hub_cfg}
        else
            printf \"\\nsc.hubFunctionEnable = true\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.primaryHubURI\" ${hub_cfg}; then
            sed -i \"s|^sc.primaryHubURI[[:space:]]*=.*|sc.primaryHubURI = wss://200.1.1.9:4443|\" ${hub_cfg}
        else
            printf \"\\nsc.primaryHubURI = wss://200.1.1.9:4443\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.failoverHubURI\" ${hub_cfg}; then
            sed -i \"s|^sc.failoverHubURI[[:space:]]*=.*|sc.failoverHubURI = |\" ${hub_cfg}
        else
            printf \"\\nsc.failoverHubURI = \\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.connectionWaitTimeout\" ${hub_cfg}; then
            sed -i \"s/^sc.connectionWaitTimeout[[:space:]]*=.*/sc.connectionWaitTimeout = ${connection_wait_timeout}/\" ${hub_cfg}
        else
            printf \"\\nsc.connectionWaitTimeout = ${connection_wait_timeout}\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^sc.tlsVersion\" ${hub_cfg}; then
            sed -i \"s/^sc.tlsVersion[[:space:]]*=.*/sc.tlsVersion = ${tls_version}/\" ${hub_cfg}
        else
            printf \"\\nsc.tlsVersion = ${tls_version}\\n\" >> ${hub_cfg}
        fi
        if grep -q \"^device.instance\" ${hub_cfg}; then
            sed -i \"s/^device.instance[[:space:]]*=.*/device.instance = 555001/\" ${hub_cfg}
        else
            printf \"\\ndevice.instance = 555001\\n\" >> ${hub_cfg}
        fi
    '"
}

write_bacnet_sc_debug_logs() {
    local hub_log="$1"
    local server_log="$2"
    local bootstrap_log="$3"
    local client_log="$4"
    local host_hub_log="${RESULTS_DIR}/bacnet_sc_hub.log"
    local host_client_log="${RESULTS_DIR}/bacnet_sc_client.log"

    rm -f "$host_hub_log" "$host_client_log"

    kathara exec bacnetserver "sh -lc 'if [ -f ${hub_log} ]; then echo \"===== hub =====\"; cat ${hub_log}; fi'" > "$host_hub_log" 2>/dev/null || true
    kathara exec bacnetclient "sh -lc 'if [ -f ${server_log} ]; then echo \"===== server-node =====\"; cat ${server_log}; fi; if [ -f ${bootstrap_log} ]; then echo \"===== bootstrap =====\"; cat ${bootstrap_log}; fi; if [ -f ${client_log} ]; then echo \"===== full =====\"; cat ${client_log}; fi'" > "$host_client_log" 2>/dev/null || true
}

start_lab() {
    kathara lclean >/dev/null 2>&1 || true
    if [[ -n "$KATHARA_OPTIONS" ]]; then
        kathara lstart $KATHARA_OPTIONS
    else
        kathara lstart
    fi

    wait_for_machine s1
    wait_for_machine s2
    wait_for_machine bacnetserver
    wait_for_machine bacnetclient
    wait_for_switch_cli s1
    wait_for_switch_cli s2
}

configure_switch_mode() {
    local mode="$1"
    local no_egress_metrics=0

    case "$mode" in
        128|192|256)
            no_egress_metrics="$DISABLE_EGRESS_METRICS"
            ;;
        *)
            no_egress_metrics=0
            ;;
    esac

    kathara exec s1 "sh -lc 'NO_EGRESS_METRICS=${no_egress_metrics} ./change_key.sh ${mode}'"
    kathara exec s2 "sh -lc 'NO_EGRESS_METRICS=${no_egress_metrics} ./change_key.sh ${mode}'"
}

collect_switch_metrics_if_needed() {
    local mode="$1"

    case "$mode" in
        128|192|256)
            if [[ $RUN_PPT -eq 1 ]]; then
                kathara exec s1 "./retrieve_info.sh --ppt $mode -w"
                kathara exec s2 "./retrieve_info.sh --ppt $mode -w"
            fi
            if [[ $RUN_DEQ -eq 1 ]]; then
                kathara exec s1 "./retrieve_info.sh --deq $mode -w"
                kathara exec s2 "./retrieve_info.sh --deq $mode -w"
            fi
            ;;
        *)
            ;;
    esac
}

run_plain_or_aes() {
    local scenario="$1"
    local mode="$2"
    local raw_file="/shared/results_rtt_${scenario}.txt"
    local target_device_instance="2001"
    local target_object_instance="1"
    local max_attempts="${BACNET_CLASSIC_MAX_ATTEMPTS:-2}"
    local client_write_retries="${BACNET_CLIENT_WRITE_RETRIES:-2}"
    local client_retry_delay_ms="${BACNET_CLIENT_RETRY_DELAY_MS:-250}"
    local attempt

    if ! [[ "$max_attempts" =~ ^[0-9]+$ ]] || [[ "$max_attempts" -lt 1 ]]; then
        max_attempts=2
    fi
    if ! [[ "$client_write_retries" =~ ^[0-9]+$ ]] || [[ "$client_write_retries" -lt 0 ]]; then
        client_write_retries=2
    fi
    if ! [[ "$client_retry_delay_ms" =~ ^[0-9]+$ ]] || [[ "$client_retry_delay_ms" -lt 0 ]]; then
        client_retry_delay_ms=250
    fi

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        local client_log
        local client_rc
        local retryable=0

        echo "Scenario ${scenario} attempt ${attempt}/${max_attempts}"

        start_lab
        configure_switch_mode "$mode"

        kathara exec bacnetserver "sh -lc 'BACNET_IFACE=eth0 BACNET_IP_PORT=47808 /bacnet/bacnet-stack/apps/bin/server ${target_device_instance}'" >/dev/null 2>&1 &
        sleep 2

        client_log="$(mktemp)"
        set +e
        kathara exec bacnetclient "sh -lc 'BACNET_IFACE=eth0 BACNET_IP_PORT=47808 /bacnet/bacnet-stack/apps/bin/client --csv /shared/HVAC-minute.csv --server-ip 200.1.1.9 --server-port 47808 --target-device ${target_device_instance} --target-object-instance ${target_object_instance} --scenario ${scenario} --raw-output ${raw_file} --write-retries ${client_write_retries} --retry-delay-ms ${client_retry_delay_ms}'" >"$client_log" 2>&1
        client_rc=$?
        set -e

        if [[ "$client_rc" -eq 0 ]]; then
            cat "$client_log"
            rm -f "$client_log"

            collect_switch_metrics_if_needed "$mode"
            kathara lclean
            return 0
        fi

        cat "$client_log" >&2 || true
        if rg -q "TSM timeout|APDU timeout|Unable to bind BACnet target device|WriteProperty failed at row" "$client_log"; then
            retryable=1
        fi
        rm -f "$client_log"
        kathara lclean

        if [[ "$retryable" -eq 1 && "$attempt" -lt "$max_attempts" ]]; then
            echo "Scenario ${scenario} failed with a retryable BACnet timeout. Retrying..." >&2
            sleep 2
            continue
        fi

        return "$client_rc"
    done
}

run_bacnet_sc() {
    local scenario="bacnet-sc-tls"
    local rtt_file="${RESULTS_DIR}/results_rtt_bacnet-sc-tls.txt"
    local hub_log="/tmp/bacnet_sc_hub.log"
    local server_log="/tmp/bacnet_sc_server.log"
    local client_log="/tmp/bacnet_sc_client.log"
    local bootstrap_log="/tmp/bacnet_sc_client_bootstrap.log"
    local bootstrap_cfg="/tmp/BenchmarkClientBootstrap.properties"
    local full_cfg="/tmp/BenchmarkClientFull.properties"
    local hub_cfg="/tmp/BenchmarkHubOnly.properties"
    local server_cfg="/tmp/BenchmarkServerNode.properties"
    local max_attempts="${BACNET_SC_CONNECT_RETRIES:-6}"
    local warmup_seconds="${BACNET_SC_HUB_WARMUP_SECONDS:-8}"
    local min_rtt_lines="${BACNET_SC_MIN_RTT_LINES:-1024}"
    local bootstrap_rows="${BACNET_SC_BOOTSTRAP_ROWS:-1}"
    local workload_rows="${BACNET_SC_WORKLOAD_ROWS:-0}"
    local connection_wait_timeout="${BACNET_SC_CONNECTION_WAIT_TIMEOUT:-60000}"
    local server_primary_hub_uri="${BACNET_SC_SERVER_PRIMARY_HUB_URI:-wss://200.1.1.9:4443}"
    local single_process_mode="${BACNET_SC_SINGLE_PROCESS_MODE:-0}"
    local tls_version="${BACNET_SC_TLS_VERSION:-TLSv1.3}"
    local attempt

    # Keep shared clean by default; SC logs are ephemeral unless failure diagnostics are printed.
    rm -f "${RESULTS_DIR}/bacnet_sc_hub.log" "${RESULTS_DIR}/bacnet_sc_client.log"
    rm -f "$rtt_file"

    if ! [[ "$min_rtt_lines" =~ ^[0-9]+$ ]]; then
        min_rtt_lines=1024
    fi
    if ! [[ "$bootstrap_rows" =~ ^[0-9]+$ ]]; then
        bootstrap_rows=1
    fi
    if ! [[ "$workload_rows" =~ ^[0-9]+$ ]]; then
        workload_rows=0
    fi
    if ! [[ "$connection_wait_timeout" =~ ^[0-9]+$ ]]; then
        connection_wait_timeout=60000
    fi
    if ! [[ "$single_process_mode" =~ ^[0-9]+$ ]]; then
        single_process_mode=0
    fi
    case "$tls_version" in
        TLSv1|TLSv1.1|TLSv1.2|TLSv1.3) ;;
        *)
            echo "Unsupported BACNET_SC_TLS_VERSION=${tls_version}; falling back to TLSv1.3" >&2
            tls_version="TLSv1.3"
            ;;
    esac

    for ((attempt = 1; attempt <= max_attempts; attempt++)); do
        echo "BACnet/SC bootstrap attempt ${attempt}/${max_attempts}"

        start_lab
        configure_switch_mode "bacnet-sc"

        if [[ "$single_process_mode" -eq 1 ]]; then
            prepare_bacnet_sc_single_process_config "$hub_cfg" "$connection_wait_timeout" "$tls_version"
            kathara exec bacnetserver "sh -lc 'rm -f ${hub_log} ${server_log}; cd /bacnet/bacnet-sc && nohup ./Application ${hub_cfg} >${hub_log} 2>&1 </dev/null &'"
            wait_for_bacnet_sc_hub
            if ! wait_for_bacnet_sc_hub_stable "$hub_log"; then
                :
            fi
        else
            prepare_bacnet_sc_server_configs "$hub_cfg" "$server_cfg" "$connection_wait_timeout" "$server_primary_hub_uri" "$tls_version"
            kathara exec bacnetserver "sh -lc 'rm -f ${hub_log} ${server_log}; cd /bacnet/bacnet-sc && nohup ./Application ${hub_cfg} >${hub_log} 2>&1 </dev/null &'"
            wait_for_bacnet_sc_hub
            if ! wait_for_bacnet_sc_hub_stable "$hub_log"; then
                :
            fi
            kathara exec bacnetclient "sh -lc 'rm -f ${server_log}; cd /bacnet/bacnet-sc && nohup ./Application ${server_cfg} >${server_log} 2>&1 </dev/null &'"
            if ! wait_for_bacnet_sc_server_connected "$server_log" "bacnetclient"; then
                echo "BACnet/SC server node did not join hub on attempt ${attempt}. Hub/server diagnostics:" >&2
                kathara exec bacnetserver "sh -lc 'tail -n 80 ${hub_log} || true'" >&2 || true
                kathara exec bacnetclient "sh -lc 'tail -n 80 ${server_log} || true'" >&2 || true
                write_bacnet_sc_debug_logs "$hub_log" "$server_log" "$bootstrap_log" "$client_log"
                kathara lclean
                continue
            fi
        fi
        if [[ "$warmup_seconds" -gt 0 ]]; then
            sleep "$warmup_seconds"
        fi

        if [[ "$bootstrap_rows" -gt 0 ]]; then
            # Optional bootstrap to ensure SC session is usable before full run.
            prepare_bacnet_sc_client_config "$bootstrap_cfg" "$bootstrap_rows" "$connection_wait_timeout" "$tls_version"
            kathara exec bacnetclient "sh -lc 'rm -f ${bootstrap_log} ${client_log}'"
            rm -f "$rtt_file"

            if timeout 180s kathara exec bacnetclient "sh -lc 'cd /bacnet/bacnet-sc && ./Application ${bootstrap_cfg} >${bootstrap_log} 2>&1'"; then
                if wait_for_rtt_growth "$rtt_file" 1 20; then
                    echo "BACnet/SC bootstrap succeeded on attempt ${attempt}."
                    rm -f "$rtt_file"
                    break
                fi
            fi

            echo "BACnet/SC bootstrap failed on attempt ${attempt}. Hub/client diagnostics:" >&2
            kathara exec bacnetserver "sh -lc 'tail -n 80 ${hub_log} || true'" >&2 || true
            kathara exec bacnetclient "sh -lc 'tail -n 80 ${server_log} || true'" >&2 || true
            kathara exec bacnetclient "sh -lc 'tail -n 80 ${bootstrap_log} || true'" >&2 || true
            write_bacnet_sc_debug_logs "$hub_log" "$server_log" "$bootstrap_log" "$client_log"
            kathara lclean
            continue
        fi

        echo "BACnet/SC bootstrap skipped (BACNET_SC_BOOTSTRAP_ROWS=0)."
        break
    done

    if [[ "$attempt" -gt "$max_attempts" ]]; then
        echo "BACnet/SC bootstrap failed after ${max_attempts} attempts." >&2
        return 1
    fi

    echo "BACnet/SC workload started."
    if [[ "$workload_rows" -eq 0 ]]; then
        echo "Full CSV mode can take several minutes."
    else
        echo "Limited CSV mode: ${workload_rows} rows."
    fi

    prepare_bacnet_sc_client_config "$full_cfg" "$workload_rows" "$connection_wait_timeout" "$tls_version"
    rm -f "$rtt_file"
    if ! kathara exec bacnetclient "sh -lc 'rm -f ${client_log}; cd /bacnet/bacnet-sc && ./Application ${full_cfg} >${client_log} 2>&1'"; then
        echo "BACnet/SC client failed. Last hub log lines:" >&2
        kathara exec bacnetserver "sh -lc 'tail -n 80 ${hub_log} || true'" >&2 || true
        echo "Last server log lines:" >&2
        kathara exec bacnetclient "sh -lc 'tail -n 80 ${server_log} || true'" >&2 || true
        echo "Last client log lines:" >&2
        kathara exec bacnetclient "sh -lc 'tail -n 80 ${client_log} || true'" >&2 || true
        write_bacnet_sc_debug_logs "$hub_log" "$server_log" "$bootstrap_log" "$client_log"
        return 1
    fi

    if [[ ! -f "$rtt_file" ]]; then
        echo "BACnet/SC completed without producing RTT file: ${rtt_file}" >&2
        write_bacnet_sc_debug_logs "$hub_log" "$server_log" "$bootstrap_log" "$client_log"
        return 1
    fi

    local rtt_lines
    rtt_lines="$(wc -l < "$rtt_file" 2>/dev/null || echo 0)"
    if [[ "$rtt_lines" -lt "$min_rtt_lines" ]]; then
        echo "BACnet/SC RTT file has too few samples: ${rtt_lines} (expected >= ${min_rtt_lines})." >&2
        write_bacnet_sc_debug_logs "$hub_log" "$server_log" "$bootstrap_log" "$client_log"
        return 1
    fi

    echo "BACnet/SC workload completed with ${rtt_lines} RTT samples."

    kathara lclean
}

cleanup_on_exit() {
    kathara lclean >/dev/null 2>&1 || true
}
trap cleanup_on_exit EXIT

kathara wipe -f

for scenario in "${SCENARIOS[@]}"; do
    echo "===== Running scenario: $scenario ====="
    case "$scenario" in
        bacnet-plain)
            run_plain_or_aes "$scenario" "plain"
            ;;
        aes-128)
            run_plain_or_aes "$scenario" "128"
            ;;
        aes-192)
            run_plain_or_aes "$scenario" "192"
            ;;
        aes-256)
            run_plain_or_aes "$scenario" "256"
            ;;
        bacnet-sc-tls)
            run_bacnet_sc
            ;;
        *)
            echo "Unsupported scenario: $scenario" >&2
            exit 1
            ;;
    esac
done

shopt -s nullglob
METRIC_FILES=(
    "$RESULTS_DIR"/results_rtt_*.txt
    "$RESULTS_DIR"/results_s1_*packet_processing_time*.txt
    "$RESULTS_DIR"/results_s2_*packet_processing_time*.txt
    "$RESULTS_DIR"/results_s1_*packet_dequeuing_timedelta*.txt
    "$RESULTS_DIR"/results_s2_*packet_dequeuing_timedelta*.txt
)
shopt -u nullglob

if [[ ${#METRIC_FILES[@]} -eq 0 ]]; then
    echo "No metric files were produced." >&2
    exit 1
fi

python3 "$SCRIPT_DIR/scripts/summarize_metrics.py" \
    --output-csv "$SUMMARY_CSV" \
    --output-json "$SUMMARY_JSON" \
    "${METRIC_FILES[@]}"

echo "Benchmark completed."
echo "Summary CSV: $SUMMARY_CSV"
echo "Summary JSON: $SUMMARY_JSON"
