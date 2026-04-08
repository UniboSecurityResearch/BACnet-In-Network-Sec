#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker build --progress=plain -t bacnet-local:latest -f "$ROOT_DIR/Dockerfile/bacnet.Dockerfile" "$ROOT_DIR"
