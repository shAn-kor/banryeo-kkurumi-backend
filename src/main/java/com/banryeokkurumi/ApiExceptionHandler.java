package com.banryeokkurumi;

import com.banryeokkurumi.catalog.CatalogApplicationService;
import com.banryeokkurumi.display.DisplayApplicationService;
import com.banryeokkurumi.identity.IdentityApplicationService;
import com.banryeokkurumi.inventory.InsufficientStockException;
import com.banryeokkurumi.ordering.OrderStateException;
import com.banryeokkurumi.promotion.CouponSoldOutException;
import com.banryeokkurumi.review.ReviewNotAllowedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception) { return problem(HttpStatus.BAD_REQUEST, "잘못된 요청", exception.getMessage()); }
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail unauthorized(BadCredentialsException exception) { return problem(HttpStatus.UNAUTHORIZED, "인증 실패", "아이디 또는 비밀번호가 올바르지 않습니다."); }
    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    ProblemDetail forbidden(RuntimeException exception) { return problem(HttpStatus.FORBIDDEN, "접근 거부", exception.getMessage()); }
    @ExceptionHandler(CatalogApplicationService.CatalogNotFoundException.class)
    ProblemDetail notFound(RuntimeException exception) { return problem(HttpStatus.NOT_FOUND, "리소스 없음", exception.getMessage()); }
    @ExceptionHandler({IdentityApplicationService.DuplicateLoginIdException.class, CouponSoldOutException.class,
            InsufficientStockException.class, OrderStateException.class, ReviewNotAllowedException.class,
            DisplayApplicationService.OfferUnavailableException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(Exception exception) { return problem(HttpStatus.CONFLICT, "상태 충돌", exception.getMessage()); }
    ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        return problem;
    }
}
