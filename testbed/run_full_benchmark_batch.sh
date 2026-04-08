#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

usage() {
    cat <<USAGE
Usage: $0 [--runs N] [--out-dir DIR] [--fail-fast] [--sc-only] [-- benchmark_extra_args]

Default behavior:
  - Runs full benchmark 10 times:
      ./benchmark_bacnet.sh --all --ppt --deq --noterminals
  - Saves each run in a dedicated folder:
      testbed/batch_runs/<timestamp>/run_XX/

Options:
  --runs N       Number of repetitions (default: 10)
  --out-dir DIR  Destination root directory (default: testbed/batch_runs/<timestamp>)
  --fail-fast    Stop at first failed run
  --sc-only      Run only BACnet/SC scenario in each batch run
  --             Pass extra args to benchmark_bacnet.sh after defaults

Examples:
  $0
  $0 --runs 10
  $0 --out-dir /tmp/bacnet_runs
  $0 --sc-only
USAGE
}

RUNS=10
FAIL_FAST=0
SC_ONLY=0
OUT_DIR=""
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --runs)
            [[ $# -ge 2 ]] || { echo "Missing value for --runs" >&2; exit 1; }
            RUNS="$2"
            shift 2
            ;;
        --out-dir)
            [[ $# -ge 2 ]] || { echo "Missing value for --out-dir" >&2; exit 1; }
            OUT_DIR="$2"
            shift 2
            ;;
        --fail-fast)
            FAIL_FAST=1
            shift
            ;;
        --sc-only)
            SC_ONLY=1
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        --)
            shift
            EXTRA_ARGS=("$@")
            break
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if ! [[ "$RUNS" =~ ^[0-9]+$ ]] || [[ "$RUNS" -lt 1 ]]; then
    echo "--runs must be a positive integer" >&2
    exit 1
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
if [[ -z "$OUT_DIR" ]]; then
    OUT_DIR="$SCRIPT_DIR/batch_runs/$TIMESTAMP"
fi

mkdir -p "$OUT_DIR"

SUMMARY_CSV="$OUT_DIR/runs_summary.csv"
SUMMARY_TXT="$OUT_DIR/runs_summary.txt"
COMMAND_FILE="$OUT_DIR/command_used.txt"

BENCH_CMD=(./benchmark_bacnet.sh --all --ppt --deq --noterminals)
if [[ "$SC_ONLY" -eq 1 ]]; then
    BENCH_CMD=(./benchmark_bacnet.sh --bacnet-sc --noterminals)
fi
if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    BENCH_CMD+=("${EXTRA_ARGS[@]}")
fi

{
    echo "Batch timestamp: $TIMESTAMP"
    echo "Runs: $RUNS"
    echo "Fail fast: $FAIL_FAST"
    echo "SC only: $SC_ONLY"
    echo -n "Command: "
    printf "%q " "${BENCH_CMD[@]}"
    echo
} > "$COMMAND_FILE"

echo "run,status,exit_code,start_iso,end_iso,duration_seconds,metric_files" > "$SUMMARY_CSV"
: > "$SUMMARY_TXT"

cleanup_shared_before_run() {
    rm -f "$SCRIPT_DIR"/shared/results_*.txt
    rm -f "$SCRIPT_DIR"/shared/benchmark_summary.csv "$SCRIPT_DIR"/shared/benchmark_summary.json
    rm -f "$SCRIPT_DIR"/shared/bacnet_sc_hub.log "$SCRIPT_DIR"/shared/bacnet_sc_client.log
}

copy_artifacts_to_run_dir() {
    local run_dir="$1"
    shopt -s nullglob
    local files=(
        "$SCRIPT_DIR"/shared/results_*.txt
        "$SCRIPT_DIR"/shared/benchmark_summary.csv
        "$SCRIPT_DIR"/shared/benchmark_summary.json
        "$SCRIPT_DIR"/shared/bacnet_sc_hub.log
        "$SCRIPT_DIR"/shared/bacnet_sc_client.log
    )
    local file
    for file in "${files[@]}"; do
        [[ -f "$file" ]] && cp -f "$file" "$run_dir/"
    done
    shopt -u nullglob
}

count_metric_files_in_run() {
    local run_dir="$1"
    find "$run_dir" -maxdepth 1 -type f -name 'results_*.txt' | wc -l
}

SUCCESS_COUNT=0
FAIL_COUNT=0

for ((i = 1; i <= RUNS; i++)); do
    RUN_LABEL="$(printf 'run_%02d' "$i")"
    RUN_DIR="$OUT_DIR/$RUN_LABEL"
    mkdir -p "$RUN_DIR"

    START_ISO="$(date --iso-8601=seconds)"
    START_EPOCH="$(date +%s)"

    cleanup_shared_before_run

    echo "===== $RUN_LABEL / $RUNS ====="
    set +e
    "${BENCH_CMD[@]}" > "$RUN_DIR/benchmark_stdout.log" 2>&1
    RC=$?
    set -e

    END_ISO="$(date --iso-8601=seconds)"
    END_EPOCH="$(date +%s)"
    DURATION=$((END_EPOCH - START_EPOCH))

    copy_artifacts_to_run_dir "$RUN_DIR"
    METRIC_FILES="$(count_metric_files_in_run "$RUN_DIR")"

    if [[ "$RC" -eq 0 ]]; then
        STATUS="ok"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        STATUS="failed"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi

    {
        echo "run=$RUN_LABEL"
        echo "status=$STATUS"
        echo "exit_code=$RC"
        echo "start_iso=$START_ISO"
        echo "end_iso=$END_ISO"
        echo "duration_seconds=$DURATION"
        echo "metric_files=$METRIC_FILES"
    } > "$RUN_DIR/run_status.txt"

    echo "$RUN_LABEL,$STATUS,$RC,$START_ISO,$END_ISO,$DURATION,$METRIC_FILES" >> "$SUMMARY_CSV"
    echo "$RUN_LABEL: status=$STATUS exit_code=$RC duration=${DURATION}s metric_files=$METRIC_FILES" >> "$SUMMARY_TXT"

    if [[ "$RC" -ne 0 && "$FAIL_FAST" -eq 1 ]]; then
        echo "Stopping early due to --fail-fast (failed at $RUN_LABEL)." | tee -a "$SUMMARY_TXT"
        break
    fi
done

{
    echo
    echo "Completed batch."
    echo "Success: $SUCCESS_COUNT"
    echo "Failed: $FAIL_COUNT"
    echo "Output: $OUT_DIR"
} | tee -a "$SUMMARY_TXT"

echo "Summary CSV: $SUMMARY_CSV"
echo "Summary TXT: $SUMMARY_TXT"
