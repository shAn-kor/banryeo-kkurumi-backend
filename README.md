# 반려꾸러미 백엔드

결제 결과가 늦거나 같은 이벤트가 다시 도착해도 주문·재고·쿠폰·배송이 서로 다른 상태로 남지 않게 만든 반려동물 커머스 백엔드입니다.

> 이 저장소에는 라이선스가 부여되어 있지 않습니다. 공개 열람 외 복제·수정·배포·상업적 이용을 허가한다는 뜻이 아니며, 별도 재사용 허가는 제공되지 않습니다.

## 해결한 문제

### 결제 실패 뒤 예약 자원이 남는 문제

외부 결제를 하나의 DB 트랜잭션에 묶을 수 없고, timeout 직후 재고와 쿠폰을 해제하면 뒤늦게 확인된 결제 성공과 충돌합니다. 예외 즉시 해제하는 방식 대신 단계별 상태를 기록하고 종결 결과에 따라 보상하는 Process Manager를 선택했습니다.

구현 후 실제 MySQL 8.4·Redis 7.4 환경에서 재고 부족, 쿠폰 거절, 결제 거절, timeout, 연결 실패, 결제 후 출고 전 취소, 중복 이벤트까지 **7개 시나리오와 34개 종결 불변식**을 검증했습니다. 동일 취소 이벤트를 **10회 전달**해도 재고·쿠폰·배송의 추가 반영은 **0건**이었고, 실패·취소 흐름의 잔여 예약 재고도 **0건**이었습니다.

### 도메인이 늘면서 경계가 무너지는 문제

13개 도메인을 처음부터 마이크로서비스로 나누면 개인 프로젝트에서 배포·통신·정합성 비용이 지나치게 커집니다. 단일 배포를 유지하되 다른 모듈의 저장소와 내부 구현에는 접근할 수 없는 모듈러 모놀리스를 선택했습니다.

초기 구조 검사에서 저장소 판별 오류, 무효한 패키지 규칙, 주문과 공통 암호화 객체의 실제 순환 의존 등 **3건**을 발견했습니다. 검사 규칙 2건을 교정하고 암호화 seam을 주문 모듈로 옮겨 순환을 제거했습니다. 현재는 같은 위반이 다시 생기면 빌드가 실패합니다.

구체적인 대안 비교와 테스트 행렬은 [문제 해결 사례](docs/case-study.md)에 정리했습니다.

## 구현 범위

- Java 25, Spring Boot 4.0, Spring Modulith 2.0, Gradle Kotlin DSL
- MySQL 8.4: 트랜잭션 정본, 검색 문서, 이벤트 publication
- Redis 7.4: 세션, 장바구니 캐시, 추천 가속 계층
- Spring Security session cookie + CSRF, BCrypt
- Flyway, Testcontainers, ArchUnit, Resilience4j

Kafka, 외부 검색엔진, 실제 결제수단, 택배사 연동, 부분취소, 반품과 환불은 포함하지 않습니다. 결제와 배송은 실패·재시도·상태 조회를 재현할 수 있는 데모 adapter입니다.

## 시작하기

필수 도구는 JDK 25와 Docker입니다.

```bash
cp .env.example .env
docker compose --env-file .env up -d
set -a && source .env && set +a
./gradlew bootRun
```

`PII_ENCRYPTION_KEY`는 Base64로 인코딩한 32바이트 AES 키입니다. `public` 프로필에서는 이 값이 없으면 애플리케이션이 기동하지 않습니다. 관리자는 `ADMIN_LOGIN_ID`와 `ADMIN_PASSWORD`가 모두 있을 때만 최초 한 번 생성됩니다.

브라우저 클라이언트는 먼저 `GET /api/v1/auth/csrf`로 토큰을 받은 뒤 모든 상태 변경 요청에 세션 쿠키와 CSRF 헤더를 함께 보냅니다. OpenAPI UI는 `/swagger-ui.html`, 명세는 `/v3/api-docs`에서 확인할 수 있습니다.

## 검증

```bash
./gradlew test
./scripts/check-api-contract.sh
./scripts/check-public-hygiene.sh
npm ci
npm run docs:build
```

통합 테스트에는 Docker가 필요합니다. 모듈 구조 테스트는 순환 의존, 허용하지 않은 모듈 접근, Controller의 repository 직접 접근을 실패 처리합니다. 고정 데이터셋 성능 측정은 [검증 문서](docs/verification.md)에 조건과 한계를 함께 기록합니다.

## 문서

- [문제 해결 사례](docs/case-study.md)
- [모듈 지도](docs/modules.md)
- [주문 상태수렴](docs/order-convergence.md)
- [API 표면](docs/api.md)
- [검증 기준](docs/verification.md)

별도 프론트엔드 저장소는 공개 준비가 끝난 뒤 링크로만 연결합니다. 이 저장소에는 프론트엔드 소스를 포함하지 않습니다.
