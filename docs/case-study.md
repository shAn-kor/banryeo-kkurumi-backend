# 문제 해결 사례

## 결제 결과 지연으로 주문과 예약 자원이 어긋나는 문제

### 문제

주문은 재고·쿠폰·결제·배송에 걸쳐 진행됩니다. 결제 거절이나 timeout을 단순 예외로 처리하면 주문만 실패하고 예약 재고와 쿠폰이 남을 수 있습니다. 반대로 timeout 직후 자원을 해제하면 상태 조회에서 뒤늦게 확인된 결제 성공과 충돌합니다. 결제 완료 후 출고 전 취소에서는 이미 `sold`로 옮긴 재고, `USED` 쿠폰, 생성된 배송을 함께 되돌려야 합니다.

### 비교한 대안

| 대안 | 장점 | 제외하거나 선택한 이유 |
|---|---|---|
| 외부 결제를 포함한 단일 DB transaction | 코드 흐름이 단순해 보임 | 외부 시스템을 DB transaction에 포함할 수 없고 lock 보유 시간이 길어짐 |
| 오류 발생 즉시 모든 자원 해제 | 빠르게 실패 처리 | 늦게 도착한 결제 성공과 충돌하고 이미 완료된 단계 구분이 어려움 |
| 단계 상태 기록 후 종결 결과에 따라 보상 | 실패 지점과 복구 여부를 추적 가능 | 구현 복잡도를 감수하되 불명확한 결제 결과를 조회로 확정할 수 있어 선택 |

### 해결

주문 시점의 상품명·옵션·가격을 snapshot으로 저장하고 `재고 예약 → 쿠폰 예약 → 결제 → 배송 생성`을 주문 Process Manager가 조정합니다. timeout과 연결 실패는 결제 상태를 `UNKNOWN`으로 기록한 뒤 provider 조회로 확정합니다. 결제 거절은 예약 자원을 해제하고, 결제 완료 후 출고 전 취소는 판매 재고를 가용 재고로 되돌리고 사용 쿠폰과 준비 중 배송을 취소합니다.

각 거래 listener는 `(listener_id, event_id)` unique 처리 기록을 먼저 확보합니다. 같은 event ID가 재전달되면 이후 처리를 생략하며, listener transaction이 실패하면 처리 기록도 함께 rollback됩니다.

### 구현 중 발견한 결함

실패 매트릭스를 처음 실행했을 때 세 가지 실제 결함이 드러났습니다.

1. 결제 완료 뒤 취소가 예약 재고만 해제하고 이미 판매 확정된 재고를 복구하지 못했습니다.
2. Application Service에서 발생한 거절 예외를 listener가 잡아도 transaction은 rollback-only가 되어 거절 event가 유실됐습니다.
3. 스케줄러가 transaction 밖에서 결제 조회 결과를 발행해 후속 listener가 실행되지 않았습니다.

판매 취소 SQL과 배송 취소 상태를 추가하고, 예상 가능한 쿠폰 거절은 예외 대신 결과 값으로 반환했습니다. 결제 성공·실패 event는 결제 상태 변경 transaction 안에서 발행하도록 옮겼습니다.

### 검증 결과

[OrderConvergenceIntegrationTest](https://github.com/shAn-kor/banryeo-kkurumi-backend/blob/main/src/test/java/com/banryeokkurumi/ordering/OrderConvergenceIntegrationTest.java)는 Testcontainers MySQL 8.4·Redis 7.4에서 다음 7개 종결 조합과 34개 모듈 불변식을 확인합니다.

| 주입 조건 | 주문 | 재고 | 쿠폰 | 결제 | 배송 |
|---|---|---|---|---|---|
| 재고 부족 | `FAILED` | 예약·판매 0 | 미사용 | 생성 안 됨 | 생성 안 됨 |
| 존재하지 않는 쿠폰 | `FAILED` | 전량 가용 | 없음 | 생성 안 됨 | 생성 안 됨 |
| 결제 거절 | `FAILED` | 전량 가용 | `AVAILABLE` | `FAILED` | 생성 안 됨 |
| 결제 timeout | `FULFILLING` | 판매 확정 | `USED` | 조회 후 `SUCCEEDED` | `PREPARING` |
| provider 연결 실패 | `FULFILLING` | 판매 확정 | `USED` | 조회 후 `SUCCEEDED` | `PREPARING` |
| 결제 후 출고 전 취소 | `CANCELLED` | 전량 가용 | `AVAILABLE` | `CANCELLED` | `CANCELLED` |
| 동일 취소 event 10회 | `CANCELLED` 유지 | 추가 반영 0 | 추가 반영 0 | `CANCELLED` 유지 | 1건 유지 |

- 실패·취소 종결 뒤 `reserved_quantity > 0`: **0건**
- 동일 event 10회 전달 뒤 추가 재고·쿠폰·배송 반영: **0건**
- event 처리 기록: 대상 listener 3곳에서 각각 **1건**
- 전체 자동 테스트: **35건**

## 도메인 증가로 순환 의존과 변경 전파가 생기는 문제

### 비교한 대안

| 대안 | 판단 |
|---|---|
| 처음부터 microservices | 독립 배포보다 통신·운영·분산 정합성 비용이 더 큰 단계라 제외 |
| package 규칙과 review만 사용 | 위반을 merge 전에 자동 차단하지 못해 제외 |
| modular monolith + executable boundary test | 단일 배포를 유지하면서 의존 방향을 자동 검증할 수 있어 선택 |

모듈 간 협력은 공개 계약과 versioned event로 제한하고, 검색은 다른 모듈 테이블을 직접 조인하지 않는 projection을 소유합니다. 구조 검사 도입 중 **3건**을 발견했습니다.

- Spring Security의 repository 타입을 프로젝트 repository로 잘못 판별한 규칙 1건
- 실제 클래스가 없는 package pattern을 검사한 무효 규칙 1건
- 주문 모듈과 루트 암호화 seam 사이의 실제 순환 의존 1건

앞의 두 검사는 프로젝트 타입으로 범위를 좁히고 실제 도메인 타입을 지정하도록 교정했습니다. 암호화 seam은 개인정보 snapshot을 소유하는 주문 모듈 안으로 이동했습니다. `ApplicationModules.verify()`와 ArchUnit이 순환 의존, 내부 접근, Controller의 repository 직접 접근을 계속 차단합니다.
