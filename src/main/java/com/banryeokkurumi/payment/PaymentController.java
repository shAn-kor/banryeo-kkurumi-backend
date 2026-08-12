package com.banryeokkurumi.payment;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payment")
class PaymentController {
    private final PaymentApplicationService service;
    PaymentController(PaymentApplicationService service){this.service=service;}
    @GetMapping PaymentApplicationService.PaymentView get(Authentication auth,@PathVariable UUID orderId){
        PaymentApplicationService.PaymentView view=service.find(orderId);if(!view.memberLoginId().equals(auth.getName()))throw new SecurityException("다른 회원의 결제입니다.");return view;
    }
}
