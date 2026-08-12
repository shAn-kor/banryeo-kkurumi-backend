# 주문 상태수렴

```mermaid
stateDiagram-v2
  [*] --> ACCEPTED: 주문 snapshot 저장 (202)
  ACCEPTED --> STOCK_RESERVED: 재고 예약
  STOCK_RESERVED --> COUPON_RESERVED: 쿠폰 예약 또는 생략
  COUPON_RESERVED --> PAYMENT_PENDING: 결제 authorize
  PAYMENT_PENDING --> PAID: 승인
  PAYMENT_PENDING --> PAYMENT_PENDING: timeout 후 query
  PAYMENT_PENDING --> CANCELLED: 거절 또는 10분 미확정
  PAID --> SHIPPING_PREPARING: 배송건 생성
  SHIPPING_PREPARING --> SHIPPED
  SHIPPED --> DELIVERED
  DELIVERED --> CONFIRMED: 직접 또는 7일 자동 구매확정
  ACCEPTED --> CANCELLED: 보상
  STOCK_RESERVED --> CANCELLED: 재고·쿠폰 멱등 해제
```

주문 요청은 가격, 상품명, SKU와 배송지 snapshot을 저장한 뒤 `202 Accepted`를 반환합니다. listener는 event ID 처리 기록과 aggregate idempotency key를 사용해 적어도 한 번 전달의 중복을 무해하게 만듭니다. 결제 결과가 불명확하면 10초 간격으로 조회하고 10분 안에 확정되지 않으면 취소로 수렴합니다.

출고 전 전체 주문 취소만 지원합니다. 부분취소, 반품과 환불은 범위 밖입니다.
