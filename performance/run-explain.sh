#!/usr/bin/env bash
set -euo pipefail

performance_dir="$(cd "$(dirname "$0")" && pwd)"
result_dir="$performance_dir/results"
mkdir -p "$result_dir"
docker compose -f "$performance_dir/compose.yml" exec -T mysql mysql --default-character-set=utf8mb4 -uperformance -pperformance banryeo_performance \
  < "$performance_dir/explain-analyze.sql" | tee "$result_dir/explain-analyze.txt"
