# BACnet In-Network Security Benchmark (P4 + Kathara)

This repository provides a BACnet-only benchmarking testbed to compare plaintext BACnet traffic, in-network AES on P4 switches, and BACnet/SC over TLS.

The testbed sends sensor values from the NIST Zero Net House HVAC dataset through two P4 software switches and measures latency/processing metrics across five scenarios.

## Scenarios

The benchmark supports 5 scenarios:

1. `bacnet-plain`
2. `aes-128`
3. `aes-192`
4. `aes-256`
5. `bacnet-sc-tls`

## Metrics

- `RTT` (all 5 scenarios)
- `PPT` packet processing time (AES scenarios only)
- `DEQ` egress dequeuing time (AES scenarios only)

## Repository Structure

```text
.
├── Dockerfile/
│   └── bacnet.Dockerfile
├── protocols/
│   └── bacnet/
│       ├── server-client/         # BACnet WriteProperty benchmark client (CSV batch sender)
│       └── bin/                   # Built benchmark client + BACnet server binary
├── bacnet-sc-reference-stack-code/
│   ├── config/kathara/            # BACnet/SC benchmark configs
│   ├── scripts/                   # BACnet/SC benchmark client script
│   └── dev/src/java/              # Reference stack sources + local service changes
├── testbed/
│   ├── benchmark_bacnet.sh        # Main benchmark orchestrator
│   ├── build_bacnet_image.sh      # Docker build helper
│   ├── lab.conf                   # Kathara topology
│   ├── HVAC-minute.csv            # NIST dataset used by benchmark
│   ├── s1/                        # Switch 1 assets (P4, keys, metrics scripts)
│   ├── s2/                        # Switch 2 assets (P4, keys, metrics scripts)
│   ├── scripts/
│   │   ├── summarize_metrics.py   # Per-run metric summary (CSV/JSON)
│   │   └── plots/                 # Batch run plot generator
│   └── shared/                    # Runtime outputs (RTT/PPT/DEQ/summary)
└── README.md
```

## Dataset Handling

Input dataset: `testbed/HVAC-minute.csv` (NIST Zero Net House).

- The benchmark reads the full file by default.
- CSV columns 4-9 are used as numeric HVAC values.
- Columns 10-13 are empty in the source dataset and are ignored.
- No one-minute pacing is applied; packets are sent as fast as possible for faster experiments.

## Prerequisites

- Docker
- Kathara
- Permission to run Docker/Kathara commands on your host

## Build the BACnet Image

From repository root:

```bash
./testbed/build_bacnet_image.sh
```

This builds `bacnet-local:latest`, used by `bacnetclient` and `bacnetserver` in `testbed/lab.conf`.

## Run the Benchmark

From `testbed/`:

```bash
kathara lclean || true
./benchmark_bacnet.sh --all --ppt --deq --noterminals
```

### Scenario Flags

- `--plain`
- `--aes-128`
- `--aes-192`
- `--aes-256`
- `--bacnet-sc`
- `--all`

### Metric Flags

- `--rtt` (always enabled logically)
- `--ppt` (AES only)
- `--deq` (AES only)

### Optional

- `--no-egress-metrics` (AES only, keeps in-network AES but skips egress register writes)
- `--noterminals` (recommended for non-interactive runs)

## Quick Checks

Run only plaintext BACnet:

```bash
./benchmark_bacnet.sh --plain --noterminals
```

Run only one AES scenario:

```bash
./benchmark_bacnet.sh --aes-128 --ppt --deq --noterminals
```

Run AES scenarios without egress register writes:

```bash
./benchmark_bacnet.sh --aes-128 --aes-192 --aes-256 --no-egress-metrics --noterminals
```

## Generate Batch Run Plots

Generate charts from a completed batch folder:

```bash
python3 testbed/scripts/plots/generate_batch_plots.py \
  --batch-dir testbed/batch_runs/20260330_182156
```

By default, plots are saved to `testbed/batch_runs/<timestamp>/plots/`.

Shortcut for latest batch:

```bash
./testbed/scripts/plots/generate_batch_plots.sh
```

## Output Files

All outputs are written to `testbed/shared/`.

### Raw

- RTT: `results_rtt_<scenario>.txt`
- Switch PPT (AES): `results_s1_*packet_processing_time*.txt`, `results_s2_*packet_processing_time*.txt`
- Switch DEQ (AES): `results_s1_*packet_dequeuing_timedelta*.txt`, `results_s2_*packet_dequeuing_timedelta*.txt`

### Aggregated

- `benchmark_summary.csv`
- `benchmark_summary.json`

Each summary row reports:

- `count`
- `min`
- `max`
- `mean`
- `median`
- `p95`
- `p99`

## BACnet-Only Switch Behavior

The P4 programs are BACnet-focused (`testbed/s1/bacnet_secure_switch.p4`, `testbed/s2/bacnet_secure_switch.p4`).

- BACnet UDP traffic (port `47808`) is parsed and eligible for in-network encryption/decryption.
- `plain` mode clears `bacnet_sec` table entries, so traffic is forwarded without in-network crypto.
- AES modes (`128/192/256`) install `cipher/decipher` actions in `bacnet_sec`.
- With `--no-egress-metrics`, AES modes install `cipher_no_metrics/decipher_no_metrics`, so crypto remains active while egress metric registers are not written.

## BACnet Request Model

- `bacnet-plain` and `aes-128/192/256` use BACnet/IP confirmed `WriteProperty` requests.
- Each valid CSV row is sent as one `WriteProperty` to a `CharacterString Value` object, carrying six HVAC values in one payload string.
- `bacnet-sc-tls` uses BACnet/SC (`WriteProperty`) over TLS in the reference stack.

### Optional SC Session Churn (Realistic Reconnects)

To simulate plants where secure channels occasionally drop and reconnect, set these in `bacnet-sc-reference-stack-code/config/kathara/BenchmarkClient.properties`:

- `app.reconnectEveryRows` (0 disables, e.g. `5000`)
- `app.reconnectJitterRows` (random interval jitter in rows)
- `app.reconnectPauseMs` (delay before reconnect)
- `app.reconnectSeed` (`-1` for random, fixed value for reproducible runs)

## Notes

- Switch programs are compiled at lab startup by `s1.startup` and `s2.startup`.
- If a run is interrupted, execute `kathara lclean` before rerunning.
- BACnet/SC scenario uses local reference stack integration inside `bacnet-local:latest`.

## Citation

If you use this repository in academic work, please cite the project repository.

```bibtex
@misc{bacnet_in_network_p4,
  title        = {BACnet In-Network Security Benchmark (P4 + Kathara)},
  author       = {BACnet-in-network-P4 Contributors},
  year         = {2026},
  howpublished = {\url{<repository-url>}},
  note         = {Accessed: 2026-03-17}
}
```

Replace `<repository-url>` with your canonical repository URL.

## Acknowledgments

- NIST Zero Net House HVAC dataset (`HVAC-minute.csv`)
- `bacnet-stack` project (C BACnet stack used by benchmark client/server container image)
- BACnet/SC reference stack included in `bacnet-sc-reference-stack-code/`
