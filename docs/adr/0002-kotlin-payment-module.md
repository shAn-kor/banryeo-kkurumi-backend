# ADR 0002: 결제 모듈만 Kotlin으로 구현

- 상태: 채택 (2026-08-14 전환, 2026-10-30 기록 정리)
- 관련 이슈: [#18](https://github.com/shAn-kor/banryeo-kkurumi-backend/issues/18)
- 전환 commit: `9d99328`

## 배경

12개 업무 모듈 중 `payment`만 Kotlin입니다. main 코드 4,905줄 중 553줄입니다. 전환 이유는 `docs/modules.md`에 한 문단으로만 있었고, 대안과 비용은 기록되지 않았습니다.

## 결정

1. `payment` 모듈은 Kotlin으로 유지합니다.
2. Kotlin 범위는 `com.banryeokkurumi.payment` 패키지 안으로 제한합니다. `contracts`의 event record와 주문 Process Manager는 Java로 둡니다.
3. 다른 모듈을 Kotlin으로 옮기거나 결제 모듈을 Java로 되돌리는 일은 하지 않습니다. 바꾸려면 이 ADR을 새 결정으로 대체합니다.

## 이유

`docs/modules.md`에 기록된 이유는 다음과 같습니다.

- 결제 provider 결과에는 거절·timeout·연결 실패처럼 값이 비는 경우가 많습니다. Kotlin nullable 타입으로 이 경우를 컴파일 시점에 드러냅니다.
- provider 호출은 `CompletableFuture`를 돌려주는 비동기 경계입니다.

코드에서 확인한 사실은 다음과 같습니다.

- provider 결과는 `enum class Status { SUCCEEDED, DECLINED, CANCELLED }`와 nullable 필드를 가진 `@JvmRecord data class Result`로 표현합니다.
- 언어 전환으로 공개 계약이 바뀌지 않는지 두 테스트가 고정합니다.
  - `PaymentKotlinInteropTest`: Kotlin `@JvmRecord`가 Java record accessor를 제공하는지 확인합니다.
  - `OpenApiContractIntegrationTest`: 결제 조회 API의 OpenAPI field를 `config/openapi-payment-snapshot.json`과 비교합니다.

기록되지 않은 사실도 있습니다. 같은 nullable 모델은 Java의 `Optional`이나 sealed interface로도 만들 수 있습니다. 왜 Java 대안 대신 언어를 바꿨는지는 기록이 없습니다. 이 저장소에서 Kotlin·Java 혼합 빌드를 보여 주려는 목적도 있었던 것으로 추정합니다. 근거는 같은 commit에 상호운용 테스트와 OpenAPI snapshot이 함께 추가된 점입니다.

## 결과

### 알려진 불일치

- 한 저장소에 두 언어가 있습니다. 빌드에 Kotlin compiler, `kotlin-reflect`, `jackson-module-kotlin`, ktlint(Spotless)가 추가로 필요합니다.
- 모듈 경계 규칙이 언어마다 다르게 보입니다. 결제의 repository·listener·adapter는 `payment.internal`에 있고, Kotlin `internal` 키워드가 아니라 Spring Modulith package 경계로 접근을 막습니다. Java 모듈은 대부분 package-private로 막습니다.
- ArchUnit 규칙은 Kotlin class에도 적용됩니다. 하지만 Kotlin 전용 관례(예: `object`, 확장 함수)를 검사하는 규칙은 없습니다.

### 유지 조건

- 결제 공개 API와 `contracts` event는 Java에서 그대로 쓸 수 있어야 합니다. 위 두 테스트가 깨지면 전환 범위를 넘은 것입니다.
- 새 모듈은 Java로 작성합니다.
