# 검증 기준

## 자동 게이트

- 도메인 단위 테스트와 민감정보 암호화·마스킹 테스트
- ArchUnit 및 Spring Modulith 구조 검증
- Testcontainers MySQL 8.4·Redis 7.4 context/migration 검증
- 공개 API allowlist, 공개 위생 검사
- VitePress 정적 문서 build
- GitHub secret scan과 dependency review

## 성능 측정 원칙

`performance/`의 k6 시나리오는 고정된 30만 상품 dataset을 별도로 적재한 환경에서만 실행합니다. 결과를 기록할 때 CPU, 메모리, Docker 버전, JVM 옵션, warm-up, 동시 사용자, 실행 시간을 함께 남깁니다. 이 수치는 운영 SLA나 실제 트래픽 성능을 의미하지 않습니다.

검색은 MySQL `EXPLAIN ANALYZE` 실행 계획과 cursor 중복·누락 여부를 함께 확인합니다. MySQL이 측정 기준을 충족하지 못할 때에만 `ProductSearch` 경계 뒤의 외부 검색 adapter를 후속 검토합니다.
