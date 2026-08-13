#!/usr/bin/env bash
set -euo pipefail

performance_dir="$(cd "$(dirname "$0")" && pwd)"
result_dir="$performance_dir/results"
for vus in 10 50 100; do
  for repetition in 1 2 3; do
    result="$result_dir/k6-${vus}vu-run${repetition}.json"
    jq -r --arg vus "$vus" --arg run "$repetition" '[
      $vus, $run,
      (.metrics.http_reqs.values.count|tostring),
      (.metrics.http_reqs.values.rate|tostring),
      (.metrics.http_req_duration.values.med|tostring),
      (.metrics.http_req_duration.values["p(95)"]|tostring),
      (.metrics.http_req_duration.values["p(99)"]|tostring),
      (.metrics.http_req_duration.values.max|tostring),
      (.metrics.http_req_failed.values.passes|tostring),
      (.metrics.schema_errors.values.count|tostring),
      (.metrics.cursor_duplicates.values.count|tostring)
    ] | @tsv' "$result"
  done
done
