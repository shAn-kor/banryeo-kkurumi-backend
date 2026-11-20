#!/usr/bin/env bash
set -euo pipefail

# Prints one row per strategy, scenario and worker count from summary.tsv.
# Performance columns are "median (min~max)" over runs; failure columns are sums over runs.
performance_dir="$(cd "$(dirname "$0")" && pwd)"
summary="${1:-$performance_dir/results/issue-9/strategies/summary.tsv}"

awk -F'\t' '
  function median(values, n,    i, j, t, sorted) {
    for (i = 1; i <= n; i++) sorted[i] = values[i]
    for (i = 2; i <= n; i++)
      for (j = i; j > 1 && sorted[j - 1] > sorted[j]; j--) {
        t = sorted[j]; sorted[j] = sorted[j - 1]; sorted[j - 1] = t
      }
    lo = sorted[1]; hi = sorted[n]
    return (n % 2) ? sorted[(n + 1) / 2] : (sorted[n / 2] + sorted[n / 2 + 1]) / 2
  }
  function cell(key, column, format,    i, values, m) {
    for (i = 1; i <= runs[key]; i++) values[i] = data[key, column, i]
    m = median(values, runs[key])
    return sprintf(format " (" format "~" format ")", m, lo, hi)
  }
  NR == 1 { for (i = 1; i <= NF; i++) col[$i] = i; next }
  {
    key = $col["strategy"] SUBSEP $col["scenario"] SUBSEP $col["workers"]
    if (!(key in runs)) order[++keys] = key
    n = ++runs[key]
    split("attemptsPerSec successesPerSec p50 p95 p99", perf, " ")
    for (p in perf) data[key, perf[p], n] = $col[perf[p]]
    split("retryExhausted errors deadlocks lockWaitTimeouts retries compensations", count, " ")
    for (c in count) total[key, count[c]] += $col[count[c]]
    violations[key] += $col["oversell"] + $col["negativeStock"] + $col["sumMismatch"] \
      + $col["rowCountMismatch"] + $col["conservationMismatch"] + $col["lost"] + $col["redisMismatch"]
  }
  END {
    print "strategy\tscenario\tworkers\truns\tattempts/s\tsuccesses/s\tp50 ms\tp95 ms\tp99 ms\tretryExhausted\terrors\tdeadlocks\tlockWaitTimeouts\tretries\tcompensations\tinvariantViolations"
    for (k = 1; k <= keys; k++) {
      key = order[k]
      split(key, id, SUBSEP)
      printf "%s\t%s\t%s\t%d\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n", id[1], id[2], id[3], runs[key],
        cell(key, "attemptsPerSec", "%.0f"), cell(key, "successesPerSec", "%.0f"),
        cell(key, "p50", "%.1f"), cell(key, "p95", "%.1f"), cell(key, "p99", "%.1f"),
        total[key, "retryExhausted"], total[key, "errors"], total[key, "deadlocks"],
        total[key, "lockWaitTimeouts"], total[key, "retries"], total[key, "compensations"], violations[key]
    }
  }
' "$summary"
