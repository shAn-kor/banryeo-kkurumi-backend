# 반려꾸러미 백엔드

반려동물 용품 쇼핑 흐름을 하나의 배포물 안에서 도메인 모듈로 분리한 공개 백엔드입니다. 상품 탐색부터 장바구니, 쿠폰, 재고 예약, 데모 결제, 배송, 구매확정, 리뷰와 비개인화 추천까지 상태가 수렴하는 과정을 다룹니다.

> 이 저장소에는 라이선스가 부여되어 있지 않습니다. 공개 열람 외 복제·수정·배포·상업적 이용을 허가한다는 뜻이 아니며, 별도 재사용 허가는 제공되지 않습니다.

## 기술 기준선

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

- [모듈 지도](docs/modules.md)
- [주문 상태수렴](docs/order-convergence.md)
- [API 표면](docs/api.md)
- [검증 기준](docs/verification.md)

별도 프론트엔드 저장소는 공개 준비가 끝난 뒤 링크로만 연결합니다. 이 저장소에는 프론트엔드 소스를 포함하지 않습니다.
