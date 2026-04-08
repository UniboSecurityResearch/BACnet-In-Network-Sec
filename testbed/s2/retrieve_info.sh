#!/bin/bash

# Function to display usage
usage() {
    echo "Usage: $0 [--ppt MODE] [--deq MODE] [-r|--read] [-w|--write]"
    echo "Options:"
    echo "  --ppt MODE    Retrieve Packet Processing Time with mode (plain/128/192/256/bacnet-sc)"
    echo "  --deq MODE    Retrieve Packet Dequeuing Time with mode (plain/128/192/256/bacnet-sc)"
    echo "  -r, --read          Read option"
    echo "  -w, --write         Write option"
    exit 19
}

# Function to validate mode
validate_mode() {
    local mode=$1
    case "$mode" in
    plain | 128 | 192 | 256 | bacnet-sc) return 0 ;;
    *) return 1 ;;
    esac
}

# Initialize variables
PPT_FLAG=false
DEQ_FLAG=false
READ_FLAG=false
WRITE_FLAG=false

read_samples_count() {
    local raw count
    raw="$(echo "register_read last_saved_index" | simple_switch_CLI)"
    count="$(printf '%s\n' "$raw" | awk -F'= ' '/=/{print $2}' | tail -n1 | awk '{print $1}')"
    if [[ "$count" =~ ^[0-9]+$ ]]; then
        echo "$count"
    else
        echo "0"
    fi
}

dump_metric_register() {
    local register_name="$1"
    local out_file="$2"
    local samples_count="$3"
    local raw line

    raw="$(echo "register_read ${register_name}" | simple_switch_CLI)"
    line="$(printf '%s\n' "$raw" | awk -F'= ' '/=/{print $2}' | tail -n1)"

    : >"$out_file"
    if [[ -z "$line" || "$samples_count" -le 0 ]]; then
        return 0
    fi

    printf '%s\n' "$line" \
        | tr ',' '\n' \
        | sed 's/^ *//' \
        | sed -n "1,${samples_count}p" \
        >"$out_file"

    sed -i 's/$/.0/' "$out_file"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
    --ppt)
        PPT_FLAG=true
        shift
        # If next argument exists and does not look like an option, validate it.
        if [[ $# -gt 0 && $1 != -* ]]; then
            if validate_mode "$1"; then
                MODE="$1"
                shift
            else
                echo "Error: --ppt option requires a valid mode (plain/128/192/256/bacnet-sc) if provided."
                usage
            fi
        fi
        ;;
    --deq)
        DEQ_FLAG=true
        shift
        if [[ $# -gt 0 && $1 != -* ]]; then
            if validate_mode "$1"; then
                MODE="$1"
                shift
            else
                echo "Error: --deq option requires a valid mode (plain/128/192/256/bacnet-sc) if provided."
                usage
            fi
        fi
        ;;
    -r | --read)
        READ_FLAG=true
        shift
        ;;
    -w | --write)
        WRITE_FLAG=true
        shift
        ;;
    *)
        echo "Error: Unknown option $1"
        usage
        ;;
    esac
done

if [ "$READ_FLAG" = true ]; then
    OPTION="read"
fi

if [ "$WRITE_FLAG" = true ]; then
    OPTION="write"
fi

if [ "$PPT_FLAG" = true ]; then
    echo "s2: PPT option selected"
    if [ -n "${MODE:-}" ]; then
        FILE=/shared/results_s2_"${MODE}"_"$OPTION"_packet_processing_time.txt
    else
        FILE=/shared/results_s2_"$OPTION"_packet_processing_time.txt
    fi
    SAMPLES_COUNT="$(read_samples_count)"
    dump_metric_register "packet_processing_time_array" "$FILE" "$SAMPLES_COUNT"
fi

if [ "$DEQ_FLAG" = true ]; then
    echo "s2: DEQ option selected"
    if [ -n "${MODE:-}" ]; then
        FILE=/shared/results_s2_"${MODE}"_"$OPTION"_packet_dequeuing_timedelta.txt
    else
        FILE=/shared/results_s2_"$OPTION"_packet_dequeuing_timedelta.txt
    fi
    SAMPLES_COUNT="$(read_samples_count)"
    dump_metric_register "packet_dequeuing_timedelta_array" "$FILE" "$SAMPLES_COUNT"
fi

# If no options were provided, show usage
if [ "$PPT_FLAG" = false ] && [ "$DEQ_FLAG" = false ]; then
    usage
fi

# echo "packet_processing_time_array: " > /shared/results_s2.txt
# echo "register_read packet_processing_time_array" | simple_switch_CLI >> /shared/results_s2.txt

# echo "" >> /shared/results_s2.txt
# echo "packet_dequeuing_timedelta_array: " >> /shared/results_s2.txt
# echo "register_read packet_dequeuing_timedelta_array" | simple_switch_CLI >> /shared/results_s2.txt
