# Repository Rules

## Modules

루트 패키지는 `com.banryeokkurumi`다. `identity`, `catalog`, `display`, `search`, `inventory`, `cart`, `promotion`, `ordering`, `payment`, `shipping`, `review`, `recommendation`은 각자 테이블과 repository를 소유한다. `contracts`에는 immutable command/event record만 둔다.

- 다른 모듈의 repository, entity, 내부 package를 import하지 않는다.
- 모듈 협력은 공개 API 또는 `contracts`의 versioned event를 사용한다.
- Facade는 둘 이상의 Application Service를 조합할 때만 사용한다.
- Controller는 단일 Application Service를 직접 호출할 수 있지만 조합 로직과 repository 접근을 갖지 않는다.
- Application Service는 자기 모듈 repository와 순수 Domain Service만 사용한다.
- Application Service끼리 직접 호출하지 않는다. 크로스 모듈 흐름은 Facade 또는 event로 조정한다.
- `@Transactional`은 Application Service에만 둔다. Domain Service와 Facade에는 두지 않는다.
- Domain Service는 저장, 외부 I/O, transaction을 수행하지 않는다.
- 유일성은 DB constraint로 보장하고 중복키는 HTTP 409로 변환한다.

## Security and data

- 비밀번호 정책은 raw password만 검사하고 BCrypt로 저장한다.
- password, token, secret과 개인정보는 로그와 `toString()`에 노출하지 않는다.
- 배송 주소는 AES-GCM adapter로 암호화하며 public profile에 키가 없으면 기동하지 않는다.
- 문자열 정규화는 `Locale.ROOT`를 사용한다.
- 상태 변경 HTTP 요청은 session cookie와 CSRF를 검증한다.

## Completion gates

`./gradlew test`, API allowlist, 공개 위생 검사, VitePress build가 모두 성공해야 한다. 성능 수치는 dataset, 실행 환경, 명령을 함께 남기며 운영 성능으로 표현하지 않는다.
