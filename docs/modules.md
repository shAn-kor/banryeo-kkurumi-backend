# 모듈 지도

| 모듈 | 소유 데이터 | 외부 협력 방식 |
|---|---|---|
| identity | 회원, 역할 | Security `UserDetailsService` |
| catalog | 브랜드·카테고리·상품·SKU 정본 | 상품 변경 event |
| display | 판매가·판매상태·노출 | offer 변경 event |
| search | MySQL ngram 검색 문서 | catalog/display/inventory/review/recommendation event 투영 |
| inventory | 가용·예약·판매 재고 | 예약/확정/해제 command event |
| cart | 회원 장바구니 | MySQL 정본, Redis cache-aside |
| promotion | 캠페인·발급·예약·사용 | DB 원자 갱신과 unique constraint |
| ordering | 주문 snapshot, Process Manager | versioned event로 거래 조정 |
| payment | 결제 시도와 provider 상태 | Kotlin 2.3.21, idempotency key 기반 `CompletableFuture` adapter |
| shipping | 암호화 주소 snapshot, 배송 상태 | 주문 결제/구매확정 event |
| review | 리뷰 권한·평점 | 구매확정 event, 평점 변경 event |
| recommendation | 행동 신호·시간감쇠 Top 100 | 행동 event, Redis 가속/MySQL fallback |
| contracts | immutable command/event record | 도메인 로직·repository 없음 |

`ApplicationModules.verify()`가 순환 의존과 named interface 밖의 침범을 빌드에서 검사합니다. 쓰기는 JPA를 기본으로 하고, 검색 투영과 재고·쿠폰 원자 갱신에는 명시적 SQL을 사용합니다.

`payment`만 Kotlin으로 구현합니다. timeout·거절처럼 nullable한 provider 결과와 비동기 경계를 명시적으로 모델링하되, `contracts`의 Java record와 주문 Process Manager는 그대로 유지해 언어 전환 범위를 결제 모듈 안으로 제한했습니다. repository·listener·adapter는 `payment.internal`에 두며 Kotlin의 `internal` 키워드가 아니라 Spring Modulith package 경계로 접근을 검사합니다.
