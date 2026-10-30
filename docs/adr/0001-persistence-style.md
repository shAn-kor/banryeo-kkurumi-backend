# ADR 0001: 모듈별 영속성 방식

- 상태: 채택 (2026-10-30 사후 기록)
- 관련 이슈: [#17](https://github.com/shAn-kor/banryeo-kkurumi-backend/issues/17)

## 배경

모듈마다 영속성 방식이 다릅니다. 구현할 때 선택 기준을 기록하지 않았고, `docs/modules.md`에는 "JPA가 기본"이라고 적혀 있었습니다. 실제 코드는 그 설명과 달랐습니다.

| 방식                    | 모듈                                              | 코드에서 확인한 쓰기 성격                                                                 |
| ----------------------- | ------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Spring Data JPA         | identity, catalog, cart, display                  | 한 aggregate를 저장하고 다시 읽는 CRUD                                                    |
| `JdbcClient` 명시적 SQL | inventory, promotion, ordering, payment, shipping | `WHERE status=:expected` 조건부 상태 전이, `available_quantity>=:quantity` 같은 원자 증감 |
| `JdbcClient` 명시적 SQL | review, search                                    | `ON DUPLICATE KEY UPDATE` upsert, 소유자 조건이 붙은 UPDATE·DELETE                        |
| `JdbcClient` 명시적 SQL | recommendation                                    | 시간 감쇠 점수를 SQL `SUM`·`GROUP BY`로 집계                                              |

event를 소비하는 모듈(inventory, promotion, ordering, payment, shipping)은 중복 전달을 막으려고 `INSERT IGNORE INTO processed_event`도 SQL로 실행합니다.

이 문서는 코드에서 확인되는 기준을 뒤늦게 결정으로 고정합니다. 구현 당시 이 기준을 의식했다는 기록은 없습니다.

## 결정

1. 동시 갱신 없이 aggregate 하나를 저장·조회하는 모듈은 JPA를 씁니다.
2. 다음 중 하나라도 필요하면 `JdbcClient`로 SQL을 직접 씁니다.
   - 영향 행 수로 성공을 판단하는 조건부 UPDATE (상태 전이, 재고·쿠폰 차감)
   - upsert와 `INSERT IGNORE` 같은 DB 제약 기반 멱등 처리
   - 집계·투영 조회
3. 한 모듈 안에서는 한 방식만 씁니다.
4. 지금 모듈을 다른 방식으로 옮기지 않습니다.

## 이유

- 조건부 UPDATE는 "읽고 검사하고 쓰기"를 SQL 한 문장으로 처리합니다. 영향 행 수로 경쟁 결과를 바로 판단할 수 있습니다. JPA로 같은 의미를 만들려면 `@Modifying` JPQL이나 lock을 써야 합니다. 그러면 영속성 컨텍스트와 DB 상태가 어긋나지 않게 관리하는 일이 추가됩니다.
- 재고 예약 방식 비교([#9](https://github.com/shAn-kor/banryeo-kkurumi-backend/issues/9))에서 조건부 UPDATE는 비관적·낙관적 lock보다 처리량이 높거나 차이가 불명확했습니다. 정합성 검사도 모두 통과했습니다.
- 회원·상품·장바구니·전시 정보는 동시 경쟁이 작고 필드가 많은 CRUD입니다. JPA mapping이 SQL과 row mapper보다 짧습니다.

## 결과

- 두 방식을 모두 알아야 코드를 읽을 수 있습니다.
- 명시적 SQL 모듈은 lazy loading이 없는 대신 조회 횟수를 직접 관리해야 합니다. 주문 목록의 2N+1 조회([#4](https://github.com/shAn-kor/banryeo-kkurumi-backend/issues/4))가 그 비용의 예입니다.
- JPA 모듈은 `ddl-auto: validate`로 Flyway schema와 entity mapping이 맞는지 기동 시점에 검사합니다. SQL 모듈에는 이 검사가 없고, 통합 테스트가 그 역할을 합니다.
- 새 모듈은 위 결정 1·2로 방식을 고르고, 다르게 고르면 이 ADR을 갱신합니다.
