# 검증 기준

## 자동 게이트

- 도메인 단위 테스트와 민감정보 암호화·마스킹 테스트
- ArchUnit 및 Spring Modulith 구조 검증
- Testcontainers MySQL 8.4·Redis 7.4 context/migration 검증
- 공개 API allowlist, 공개 위생 검사
- Kotlin 전환 전후 결제 OpenAPI field snapshot 검사
- VitePress 정적 문서 build
- GitHub secret scan과 dependency review

## 주문 실패 매트릭스

주문 통합 테스트는 실제 MySQL 8.4·Redis 7.4에서 **7개 종결 시나리오와 34개 모듈 불변식**을 검사합니다. 재고 부족·쿠폰 거절·결제 거절·timeout·연결 실패·결제 후 출고 전 취소·동일 event 10회 전달을 포함합니다.

결과는 [문제 해결 사례](/case-study#검증-결과)와 테스트 코드에 함께 기록합니다. 테스트 행렬을 변경하지 않고 수치만 수정하지 않습니다.

## 성능 측정 원칙

`performance/`의 k6 시나리오는 고정된 30만 상품 dataset을 별도로 적재한 환경에서만 실행합니다. 결과를 기록할 때 CPU, 메모리, Docker 버전, JVM 옵션, warm-up, 동시 사용자, 실행 시간을 함께 남깁니다. 이 수치는 운영 SLA나 실제 트래픽 성능을 의미하지 않습니다.

검색은 MySQL `EXPLAIN ANALYZE` 실행 계획과 cursor 중복·누락 여부를 함께 확인합니다. MySQL이 측정 기준을 충족하지 못할 때에만 `ProductSearch` 경계 뒤의 외부 검색 adapter를 후속 검토합니다.

재고 경합은 재고 50개에 서로 다른 주문 100개를 동시에 예약하고 이를 10회 반복합니다. 각 회차마다 성공 50건·실패 50건, 음수 재고 0건, 해제 후 가용 재고 50개 복원을 자동 검사합니다.
