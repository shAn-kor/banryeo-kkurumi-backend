#!/usr/bin/env bash
set -euo pipefail

performance_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(dirname "$performance_dir")"
result_dir="${RESULT_DIR:-$performance_dir/results/stock}"
mkdir -p "$result_dir"

cd "$project_dir"
./gradlew stockReservationBenchmark -Dbench.resultDir="$result_dir" "$@"
