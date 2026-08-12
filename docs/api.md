# API 표면

실행 중 생성되는 code-first OpenAPI는 `/v3/api-docs`, UI는 `/swagger-ui.html`입니다. CI는 `config/public-api-allowlist.txt`가 명시적으로 변경되지 않으면 새 공개 경로가 추가되지 않도록 검사합니다.

- `/api/v1/auth/**`: 가입, 로그인, 로그아웃, CSRF, 내 정보
- `/api/v1/products`, `/api/v1/search`, `/api/v1/recommendations`: 공개 탐색
- `/api/v1/cart`, `/api/v1/coupons`, `/api/v1/orders`, `/api/v1/reviews`: 인증 회원 기능
- `/api-admin/v1/**`: `ROLE_ADMIN` 명령

오류 응답은 RFC 9457 `ProblemDetail`입니다. 모든 상태 변경 요청에는 session cookie와 CSRF token이 필요합니다. 결제 API에는 카드번호나 실제 결제수단 필드가 없습니다.
