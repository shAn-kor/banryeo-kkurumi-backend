# 성능 측정

이 결과는 운영 SLA가 아니라 고정된 로컬 환경에서 같은 실행물을 다시 돌릴 수 있는 기준선입니다. 성능이 좋은 결과만 골라내지 않았으며, 30만 검색 문서에서 확인된 느린 키워드 관련도 검색도 함께 공개합니다.

## 환경

| 항목 | 값 |
|---|---|
| host | Apple Mac mini `Mac16,10`, CPU 10 core, memory 16 GiB |
| OS | macOS 26.0 |
| container runtime | Colima VM 6 CPU·7.7 GiB, Docker Engine 29.2.1 |
| application | Eclipse Temurin Java 25, 2 CPU·1 GiB, heap 512 MiB |
| database | MySQL 8.4, 2 CPU·2 GiB |
| dataset | 300,000건, seed SQL SHA-256 `9aa73fcf46cd1332cfeff09becc8e0f77d07c70b267df9517f54e524d0836bce` |
| measured source | commit `9d99328` |

## EXPLAIN ANALYZE

| 조회 | 주요 실행 계획 | actual time |
|---|---|---:|
| 키워드 관련도 | ngram FULLTEXT 후보 90,000행 후 관련도 정렬 | 약 204 ms |
| 복합 필터 | `idx_search_document_filter`, 후보 1,352행 | 약 3.0 ms |
| 최신순 | `idx_search_document_latest` covering lookup 21행 | 약 0.007 ms |
| 가격 오름차순 | `idx_search_document_price` covering range scan 21행 | 약 0.019 ms |
| 가격 내림차순 | 후보 73,872행 후 정렬 | 약 109 ms |
| 인기순 | `idx_search_document_popular` covering lookup 21행 | 약 0.011 ms |

첫 측정에서 최신순은 `UNIX_TIMESTAMP(cataloged_at)` 계산식 때문에 30만 행을 읽어 약 3.6초가 걸렸고, 가격 오름차순은 약 104ms가 걸렸습니다. projection에 epoch 값을 함께 저장하고 `(active, cataloged_epoch DESC, product_id)`, `(active, price, product_id)` index를 추가해 위 결과로 바꿨습니다.

키워드 관련도는 9만 후보를 점수순으로 정렬합니다. 마지막 워밍 상태 측정은 약 204ms였지만, 최초 cold 측정에서는 약 1.4초가 걸렸습니다. 이 결과만으로 외부 검색엔진을 도입하지 않으며, 검색어 선택도와 pagination까지 포함한 후속 측정에서 경계를 다시 판단합니다.

원본 실행 계획은 [`performance/results/explain-analyze.txt`](https://github.com/shAn-kor/banryeo-kkurumi-backend/blob/main/performance/results/explain-analyze.txt)에 보관합니다.

## k6 혼합 검색

2분 warm-up 뒤 키워드 35%, 복합 필터 25%, 인기순 15%, 최신순 15%, 가격순 cursor 10% 비율로 10·50·100 VU를 각각 3분씩 3회 실행합니다. latency는 합격 기준으로 두지 않고 HTTP 오류·schema 오류·cursor 중복이 0인지에만 hard gate를 적용합니다.

각 값은 3회 중 중앙값이며 괄호 안은 최소~최대 범위입니다. latency 단위는 ms입니다.

| VU | 요청 수 | RPS | p50 | p95 | p99 | max | HTTP/schema/cursor 오류 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1,949 (1,788~2,016) | 10.75 (9.87~11.17) | 15.1 (13.7~16.5) | 3,198.8 (3,196.2~3,278.8) | 3,464.4 (3,410.3~3,589.7) | 4,274.4 (3,829.3~4,545.0) | 0 / 0 / 0 |
| 50 | 3,212 (3,187~3,214) | 17.38 (17.38~17.48) | 2,106.3 (2,100.4~2,132.8) | 8,061.9 (8,058.2~8,268.7) | 10,011.6 (9,917.3~10,197.4) | 19,723.8 (19,392.0~20,493.2) | 0 / 0 / 0 |
| 100 | 2,698 (2,647~2,701) | 14.39 (14.19~14.41) | 6,570.2 (6,509.1~6,671.5) | 10,564.0 (10,348.6~11,223.1) | 15,156.0 (14,518.6~15,375.5) | 21,286.7 (19,395.7~23,716.4) | 0 / 0 / 0 |

10 VU에서도 키워드 관련도 요청이 포함되면서 p95가 약 3.2초였고, 50 VU부터 p50이 2초를 넘었습니다. 100 VU에서는 RPS가 오히려 감소해 이 로컬 자원 제한에서 DB 검색이 포화됐음을 보여줍니다. 이는 운영 처리량이나 SLA가 아니라 후속 개선을 비교하기 위한 재현 가능한 기준선입니다. 9개 실행의 개별 값과 분포는 [`performance/results`](https://github.com/shAn-kor/banryeo-kkurumi-backend/tree/main/performance/results)의 원본 JSON에 보관합니다.

## 재현

```bash
docker compose -f performance/compose.yml up -d --build
./performance/load-dataset.sh
./performance/run-explain.sh
./performance/run-k6.sh
./performance/summarize-k6.sh
```
