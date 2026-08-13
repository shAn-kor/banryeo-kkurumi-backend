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

## EXPLAIN ANALYZE

| 조회 | 주요 실행 계획 | actual time |
|---|---|---:|
| 키워드 관련도 | ngram FULLTEXT 후보 90,000행 후 관련도 정렬 | 약 1,421 ms |
| 복합 필터 | `idx_search_document_filter`, 후보 1,352행 | 약 18.1 ms |
| 최신순 | `idx_search_document_latest` covering lookup 21행 | 약 0.009 ms |
| 가격 오름차순 | `idx_search_document_price` covering range scan 21행 | 약 0.017 ms |
| 가격 내림차순 | 후보 73,872행 후 정렬 | 약 40.2 ms |
| 인기순 | `idx_search_document_popular` covering lookup 21행 | 약 0.012 ms |

첫 측정에서 최신순은 `UNIX_TIMESTAMP(cataloged_at)` 계산식 때문에 30만 행을 읽어 약 3.6초가 걸렸고, 가격 오름차순은 약 104ms가 걸렸습니다. projection에 epoch 값을 함께 저장하고 `(active, cataloged_epoch DESC, product_id)`, `(active, price, product_id)` index를 추가해 위 결과로 바꿨습니다.

키워드 관련도는 9만 후보를 점수순으로 정렬해 약 1.4초가 걸립니다. 이 결과만으로 외부 검색엔진을 도입하지 않으며, 검색어 선택도와 pagination까지 포함한 후속 측정에서 경계를 다시 판단합니다.

원본 실행 계획은 [`performance/results/explain-analyze.txt`](https://github.com/shAn-kor/banryeo-kkurumi-backend/blob/main/performance/results/explain-analyze.txt)에 보관합니다.

## k6 혼합 검색

2분 warm-up 뒤 키워드 35%, 복합 필터 25%, 인기순 15%, 최신순 15%, 가격순 cursor 10% 비율로 10·50·100 VU를 각각 3분씩 3회 실행합니다. latency는 합격 기준으로 두지 않고 HTTP 오류·schema 오류·cursor 중복이 0인지에만 hard gate를 적용합니다.

측정 결과 표는 동일 commit의 `performance/results` 원본에서 산출합니다.

## 재현

```bash
docker compose -f performance/compose.yml up -d --build
./performance/load-dataset.sh
./performance/run-explain.sh
./performance/run-k6.sh
./performance/summarize-k6.sh
```
