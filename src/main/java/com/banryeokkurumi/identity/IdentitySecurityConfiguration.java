package com.banryeokkurumi.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Clock;

@Configuration
@EnableMethodSecurity
class IdentitySecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    AuthenticationManager authenticationManager(IdentityApplicationService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/csrf", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**",
                                "/api/v1/search", "/api/v1/search/**", "/api/v1/recommendations",
                                "/api/v1/recommendations/**").permitAll()
                        .requestMatchers("/api-admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true))
                .build();
    }

    @Bean
    ApplicationRunner initialAdmin(IdentityApplicationService service,
                                   @Value("${app.admin.login-id:}") String loginId,
                                   @Value("${app.admin.password:}") String password) {
        return arguments -> {
            if (!loginId.isBlank() && !password.isBlank()) service.createInitialAdmin(loginId, password);
        };
    }
}
