#!/usr/bin/env bash
set -euo pipefail

performance_dir="$(cd "$(dirname "$0")" && pwd)"
result_dir="$performance_dir/results"
duration="${DURATION:-3m}"
warmup_duration="${WARMUP_DURATION:-2m}"
mkdir -p "$result_dir"

VUS=10 DURATION="$warmup_duration" RESULT_PATH="$result_dir/warmup.json" k6 run --quiet "$performance_dir/search.js"
for vus in 10 50 100; do
  for repetition in 1 2 3; do
    VUS="$vus" DURATION="$duration" RESULT_PATH="$result_dir/k6-${vus}vu-run${repetition}.json" \
      k6 run --quiet "$performance_dir/search.js"
  done
done
