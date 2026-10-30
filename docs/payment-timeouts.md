# 결제 상태 조회 주기와 대사 시한

결제 승인 결과를 모르는 경우(timeout, 연결 실패) 시스템은 PG에 상태를 다시 묻습니다. 이 문서는 그 주기와 시한, 시한 뒤에 PG 성공이 확인되는 경우의 동작을 코드 기준으로 기록합니다. 작업 기록은 [issue #21](https://github.com/shAn-kor/banryeo-kkurumi-backend/issues/21)에 있습니다.

## 현재 값

| 설정                                                          | 값    | 위치                          | 의미                                                           |
| ------------------------------------------------------------- | ----- | ----------------------------- | -------------------------------------------------------------- |
| `app.payment.reconciliation-delay`                            | `10s` | `application.yml`             | scheduler 실행 간격(`fixedDelay`)이자 다음 조회 예정 시각 간격 |
| `app.payment.reconciliation-window`                           | `10m` | `application.yml`             | 결제가 `UNKNOWN`이 된 시각부터 대사 시한까지의 길이            |
| `resilience4j.timelimiter...paymentProvider.timeout-duration` | `2s`  | `application.yml`             | 승인 요청 1회의 대기 한도                                      |
| `resilience4j.retry...paymentProvider.max-attempts`           | `2`   | `application.yml`             | 승인·조회·취소 시도 횟수 (대기 `200ms`)                        |
| 재고 예약 `expires_at`                                        | `15m` | `InventoryApplicationService` | 저장만 하고 만료 처리에 쓰지 않음                              |

## 값의 근거

**기록된 근거는 없습니다.** 두 값은 첫 commit `1e3d75f`(2026-08-13)에 설명 없이 들어왔습니다. [주문 수렴](/order-convergence) 문서도 "10초 간격으로 조회하고 10분 안에 확정되지 않으면 취소"라고 동작만 적었습니다.

코드에서 확인되는 제약은 다음과 같습니다.

- 시한 10분은 재고 예약의 `expires_at` 15분보다 짧습니다. 하지만 `expires_at`을 읽는 만료 작업이 없으므로 이 순서가 실제 동작을 바꾸지는 않습니다.
- 결제가 `UNKNOWN`인 동안 주문은 `PAYMENT_PENDING`이고, 재고와 쿠폰 예약도 유지됩니다. 시한이 길수록 미확정 주문이 자원을 오래 잡습니다.

실제 PG를 붙이면 PG의 승인 취소 가능 시간과 상태 조회 API 호출 한도에 맞춰 다시 정해야 합니다.

## 코드 경로

1. `PaymentEventHandler.on(PaymentRequested)`가 `provider.authorize(...).join()`을 호출합니다.
2. 승인 결과가 오지 않으면 `TimeLimiter`가 2초 뒤 실패시키고 `Retry`가 1번 더 시도합니다. 두 번 모두 실패하면 `service.unknown()`이 상태를 `UNKNOWN`으로 바꿉니다. 이때 `next_reconciliation_at = now + 10s`, `reconciliation_deadline = now + 10m`을 저장합니다.
3. `PaymentEventHandler.reconcile()`이 이전 실행 종료 10초 뒤마다 돌며 `status='UNKNOWN' AND next_reconciliation_at<=now`인 결제를 읽습니다.
4. 시한 전이면 `provider.query()`로 상태를 묻습니다.
   - `SUCCEEDED`: `service.success()`가 `SUCCEEDED`로 바꾸고 `PaymentSucceeded`를 발행합니다.
   - `DECLINED`: `service.fail()`이 `FAILED`로 바꾸고 `PaymentFailed`를 발행합니다.
   - `CANCELLED`: 아무것도 바꾸지 않습니다. 다음 실행에서 다시 묻습니다.
   - 예외: `next_reconciliation_at`을 10초 뒤로 미룹니다.
5. 시한이 지났으면 `provider.cancel()`로 PG에 취소를 요청한 **뒤** `service.fail(orderId, "RECONCILIATION_TIMEOUT")`을 호출합니다. `PaymentFailed`를 받은 ordering은 주문을 `FAILED`로 바꾸고, inventory와 promotion은 각자 예약을 해제합니다.

## 시한 뒤 PG 성공

시나리오: 승인 요청이 timeout 났지만 PG는 실제로 승인했습니다. 10분 동안 조회로 성공을 확인하지 못했습니다.

현재 동작은 다음과 같습니다.

1. 시한이 지난 첫 `reconcile()` 실행에서 PG 상태를 다시 묻지 않습니다. 바로 PG에 취소를 요청합니다. PG가 승인한 결제라면 이 요청이 승인 취소가 됩니다.
2. 취소 요청이 성공하면 결제를 `FAILED`(`RECONCILIATION_TIMEOUT`)로 바꿉니다.
3. 그 뒤 성공 결과가 들어와도 `service.success()`는 상태를 바꾸지 않습니다. 성공 전이 SQL이 `status IN ('REQUESTED','UNKNOWN')`만 허용하기 때문입니다. `PaymentSucceeded`도 발행하지 않습니다.
4. 취소 요청이 예외로 끝나면 `service.fail()`을 호출하지 않습니다. 결제는 `UNKNOWN`으로 남고 다음 실행에서 취소를 다시 요청합니다.

정리하면 "PG에서는 승인, 우리 쪽은 실패"로 끝나는 경로는 취소 요청이 성공한 뒤에만 열립니다. 승인된 돈을 환불 없이 실패 처리하는 경로는 현재 코드에 없습니다. 단, 아래 한계 1번은 예외입니다.

### 고정 테스트

`PaymentModuleIntegrationTest`(MySQL 8.4 Testcontainers)가 이 동작을 고정합니다.

| 테스트                                                                                | 확인 내용                                                                                                                                      |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `reconcile_시한전UNKNOWN_PG조회성공으로수렴하고취소하지않는다`                        | 시한 전 `UNKNOWN` 결제가 조회 성공으로 `SUCCEEDED`가 되고 `PaymentSucceeded` 1건, `PaymentFailed` 0건                                          |
| `reconcile_시한지난UNKNOWN_PG가성공이어도취소요청뒤실패로종결하고_늦은성공은무시한다` | PG 조회가 `SUCCEEDED`인 결제도 시한이 지나면 PG 호출 1건(취소) 뒤 `FAILED`. 이후 `success()` 호출은 `FAILED`를 유지하고 `PaymentSucceeded` 0건 |

데모 PG는 `TIMEOUT` 시나리오의 조회에 `SUCCEEDED`를 돌려줍니다. 그래서 두 번째 테스트의 결제는 "PG가 늦게 승인한 결제"입니다.

## 한계

검증하지 않았거나 현재 코드가 다루지 않는 경우입니다.

1. **취소 응답을 확인하지 않습니다.** `provider.cancel()`의 반환값을 버립니다. 실제 PG가 예외 없이 "취소 불가"를 돌려주면 돈은 승인된 채 결제는 `FAILED`가 됩니다. 데모 PG의 취소는 항상 `CANCELLED`라 테스트로 재현할 수 없습니다.
2. **취소 재시도에 상한과 알림이 없습니다.** 취소가 계속 예외로 끝나면 결제와 주문이 미확정으로 남습니다. 그 예외는 `reconcile()` 밖으로 나가므로 같은 실행의 나머지 결제도 다음 실행으로 밀립니다.
3. **PG 통지(webhook) 경로가 없습니다.** 늦은 성공은 조회로만 알 수 있습니다. `FAILED` 뒤에는 조회하지 않습니다.
4. **여러 instance를 고려하지 않았습니다.** scheduler에 분산 lock이 없습니다. instance마다 같은 결제를 조회·취소할 수 있습니다. 상태 전이는 조건부 UPDATE라 한 번만 반영되지만, PG 호출은 중복됩니다.
5. 실행 간격은 `fixedDelay`입니다. 첫 조회는 `UNKNOWN` 뒤 10~20초 사이에 일어나고, 실제 종결은 시한 뒤 최대 약 10초 늦습니다.
