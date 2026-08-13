package com.banryeokkurumi.payment.internal

import com.banryeokkurumi.payment.PaymentApplicationService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payment")
internal class PaymentController(private val service: PaymentApplicationService) {
    @GetMapping
    fun get(authentication: Authentication, @PathVariable orderId: UUID): PaymentApplicationService.PaymentView {
        val view = service.find(orderId)
        if (view.memberLoginId != authentication.name) {
            throw SecurityException("다른 회원의 결제입니다.")
        }
        return view
    }
}
