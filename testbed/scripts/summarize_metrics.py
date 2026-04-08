#!/usr/bin/env python3
import argparse
import json
import math
import statistics
from pathlib import Path


def parse_numbers(path: Path):
    values = []
    with path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            token = line.strip()
            if not token:
                continue
            try:
                values.append(float(token))
            except ValueError:
                continue
    return values


def percentile(sorted_values, p: float):
    if not sorted_values:
        return math.nan
    if len(sorted_values) == 1:
        return sorted_values[0]
    rank = (len(sorted_values) - 1) * p
    lo = int(math.floor(rank))
    hi = int(math.ceil(rank))
    if lo == hi:
        return sorted_values[lo]
    weight = rank - lo
    return sorted_values[lo] * (1.0 - weight) + sorted_values[hi] * weight


def detect_metadata(path: Path):
    name = path.name
    info = {
        "scenario": "unknown",
        "metric": "unknown",
        "switch": "",
        "direction": "",
    }

    if name.startswith("results_rtt_"):
        info["scenario"] = name.replace("results_rtt_", "").replace(".txt", "")
        info["metric"] = "rtt"
        return info

    if "packet_processing_time" in name:
        info["metric"] = "ppt"
    elif "packet_dequeuing_timedelta" in name:
        info["metric"] = "deq"

    if name.startswith("results_s1_"):
        info["switch"] = "s1"
    elif name.startswith("results_s2_"):
        info["switch"] = "s2"

    if "_read_" in name:
        info["direction"] = "read"
    elif "_write_" in name:
        info["direction"] = "write"

    if name.startswith("results_s1_") or name.startswith("results_s2_"):
        prefix = "results_%s_" % info["switch"]
        remainder = name[len(prefix):]
        if "_read_" in remainder:
            info["scenario"] = remainder.split("_read_", 1)[0]
        elif "_write_" in remainder:
            info["scenario"] = remainder.split("_write_", 1)[0]

    # Normalize switch metric scenario labels to RTT scenario labels.
    if info["scenario"] in {"128", "192", "256"}:
        info["scenario"] = "aes-%s" % info["scenario"]
    elif info["scenario"] == "plain":
        info["scenario"] = "bacnet-plain"
    elif info["scenario"] == "bacnet-sc":
        info["scenario"] = "bacnet-sc-tls"

    return info


def summarize_file(path: Path):
    values = parse_numbers(path)
    meta = detect_metadata(path)

    if not values:
        return {
            "file": str(path),
            "count": 0,
            "min": None,
            "max": None,
            "mean": None,
            "median": None,
            "p95": None,
            "p99": None,
            **meta,
        }

    ordered = sorted(values)
    return {
        "file": str(path),
        "count": len(ordered),
        "min": ordered[0],
        "max": ordered[-1],
        "mean": statistics.fmean(ordered),
        "median": statistics.median(ordered),
        "p95": percentile(ordered, 0.95),
        "p99": percentile(ordered, 0.99),
        **meta,
    }


def main():
    parser = argparse.ArgumentParser(description="Summarize benchmark metric files")
    parser.add_argument("files", nargs="+", help="Metric files to summarize")
    parser.add_argument("--output-csv", required=True, help="Output CSV summary path")
    parser.add_argument("--output-json", required=True, help="Output JSON summary path")
    args = parser.parse_args()

    summaries = []
    for item in args.files:
        path = Path(item)
        if not path.exists():
            continue
        summaries.append(summarize_file(path))

    summaries.sort(key=lambda x: (x["scenario"], x["metric"], x["switch"], x["direction"], x["file"]))

    csv_fields = [
        "scenario",
        "metric",
        "switch",
        "direction",
        "file",
        "count",
        "min",
        "max",
        "mean",
        "median",
        "p95",
        "p99",
    ]

    output_csv = Path(args.output_csv)
    output_csv.parent.mkdir(parents=True, exist_ok=True)
    with output_csv.open("w", encoding="utf-8") as f:
        f.write(",".join(csv_fields) + "\n")
        for row in summaries:
            values = []
            for field in csv_fields:
                value = row.get(field)
                if value is None:
                    values.append("")
                else:
                    values.append(str(value))
            f.write(",".join(values) + "\n")

    output_json = Path(args.output_json)
    output_json.parent.mkdir(parents=True, exist_ok=True)
    with output_json.open("w", encoding="utf-8") as f:
        json.dump(summaries, f, indent=2)


if __name__ == "__main__":
    main()
