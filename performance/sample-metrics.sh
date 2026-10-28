#!/usr/bin/env bash
set -euo pipefail

# Samples Hikari pool and MySQL status every INTERVAL seconds until stopped.
performance_dir="$(cd "$(dirname "$0")" && pwd)"
output="${1:?usage: sample-metrics.sh <output.tsv>}"
interval="${INTERVAL:-5}"
base_url="${BASE_URL:-http://localhost:18080}"
login_id="${METRICS_LOGIN_ID:-perf-metrics}"
login_password="${METRICS_PASSWORD:-Perf-metrics-2026!}"
cookie_jar="$(mktemp)"
trap 'rm -f "$cookie_jar"' EXIT

# Actuator metrics require an authenticated session; log in with a performance-only member.
csrf_token="$(curl -fsS -c "$cookie_jar" -b "$cookie_jar" "$base_url/api/v1/auth/csrf" | jq -r '.token')"
credentials="$(jq -n --arg id "$login_id" --arg pw "$login_password" '{loginId:$id,password:$pw,name:"perf"}')"
curl -sS -o /dev/null -c "$cookie_jar" -b "$cookie_jar" -H 'Content-Type: application/json' \
  -H "X-CSRF-TOKEN: $csrf_token" -d "$credentials" "$base_url/api/v1/auth/register"
csrf_token="$(curl -fsS -c "$cookie_jar" -b "$cookie_jar" "$base_url/api/v1/auth/csrf" | jq -r '.token')"
curl -fsS -o /dev/null -c "$cookie_jar" -b "$cookie_jar" -H 'Content-Type: application/json' \
  -H "X-CSRF-TOKEN: $csrf_token" -d "$credentials" "$base_url/api/v1/auth/login"

metric() {
  curl -fsS -b "$cookie_jar" "$base_url/actuator/metrics/$1" | jq -r '.measurements[0].value'
}

printf 'epoch\thikari_active\thikari_pending\tthreads_running\tbuffer_pool_reads\tbuffer_pool_read_requests\n' > "$output"
while true; do
  mysql_status="$(docker compose -f "$performance_dir/compose.yml" exec -T mysql mysql -N -uperformance -pperformance -e \
    "SELECT variable_value FROM performance_schema.global_status WHERE variable_name IN ('Threads_running','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests') ORDER BY FIELD(variable_name,'Threads_running','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests')" 2>/dev/null | paste -sd '\t' -)"
  printf '%s\t%s\t%s\t%s\n' "$(date +%s)" "$(metric hikaricp.connections.active)" "$(metric hikaricp.connections.pending)" "$mysql_status" >> "$output"
  sleep "$interval"
done
