# Performance harness

이 디렉터리는 운영 SLA가 아니라 동일 조건에서 다시 실행할 수 있는 로컬 기준선을 만듭니다.

```bash
docker compose -f performance/compose.yml up -d --build
./performance/load-dataset.sh
./performance/run-explain.sh
./performance/run-k6.sh
./performance/summarize-k6.sh
```

loader는 현재 DB가 정확히 `banryeo_performance`일 때만 `search_document`를 초기화합니다. 전체 k6 실행은 2분 warm-up 뒤 10·50·100 VU를 각 3분씩 3회 실행합니다. 짧은 smoke는 `WARMUP_DURATION=10s DURATION=15s ./performance/run-k6.sh`로 실행할 수 있습니다.
