#!/usr/bin/env bash
set -euo pipefail

performance_dir="$(cd "$(dirname "$0")" && pwd)"
compose_file="$performance_dir/compose.yml"
for attempt in $(seq 1 60); do
  if curl -fsS http://localhost:18080/actuator/health/readiness >/dev/null; then
    break
  fi
  if [[ "$attempt" = "60" ]]; then
    echo "Application did not become ready" >&2
    exit 1
  fi
  sleep 2
done
database_name="$(docker compose -f "$compose_file" exec -T mysql mysql --default-character-set=utf8mb4 -N -uperformance -pperformance -e 'SELECT DATABASE()' banryeo_performance)"
if [[ "$database_name" != "banryeo_performance" ]]; then
  echo "Refusing to load dataset into $database_name" >&2
  exit 1
fi

docker compose -f "$compose_file" exec -T mysql mysql --default-character-set=utf8mb4 -uperformance -pperformance banryeo_performance < "$performance_dir/load-search-dataset.sql"
document_count="$(docker compose -f "$compose_file" exec -T mysql mysql --default-character-set=utf8mb4 -N -uperformance -pperformance -e 'SELECT COUNT(*) FROM search_document' banryeo_performance)"
test "$document_count" = "300000"
dataset_hash="$(shasum -a 256 "$performance_dir/load-search-dataset.sql" | awk '{print $1}')"
echo "document_count=$document_count"
echo "dataset_hash=$dataset_hash"
