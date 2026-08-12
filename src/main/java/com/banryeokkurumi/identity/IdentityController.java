package com.banryeokkurumi.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class IdentityController {
    private final IdentityApplicationService identityService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    IdentityController(IdentityApplicationService identityService, AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository) {
        this.identityService = identityService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    IdentityApplicationService.MemberView register(@Valid @RequestBody RegisterRequest request) {
        return identityService.register(new IdentityApplicationService.RegisterCommand(request.loginId(), request.password(), request.name()));
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                        HttpServletResponse servletResponse, CsrfToken csrfToken) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.loginId(), request.password()));
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return new LoginResponse(authentication.getName(), csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken()); }

    @GetMapping("/me")
    IdentityApplicationService.MemberView me(Authentication authentication) { return identityService.findByLoginId(authentication.getName()); }

    record RegisterRequest(@NotBlank String loginId, @NotBlank String password, @NotBlank String name) {}
    record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
        @Override public String toString() { return "LoginRequest[loginId=" + loginId + ", password=***]"; }
    }
    record LoginResponse(String loginId, String csrfHeaderName, String csrfToken) {}
    record CsrfResponse(String headerName, String parameterName, String token) {}
}
