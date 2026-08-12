package com.banryeokkurumi.ordering;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void 개인정보를_AES_GCM으로_왕복한다() {
        PiiCipher cipher = new PiiCipher(KEY, new MockEnvironment());

        String encrypted = cipher.encrypt("서울시 테스트로 1");

        assertThat(encrypted).doesNotContain("서울시");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("서울시 테스트로 1");
    }

    @Test
    void 같은평문도_매번_다른_IV를_사용한다() {
        PiiCipher cipher = new PiiCipher(KEY, new MockEnvironment());

        assertThat(cipher.encrypt("주소")).isNotEqualTo(cipher.encrypt("주소"));
    }

    @Test
    void public_프로필은_키가없으면_기동을_거부한다() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("public");

        assertThatThrownBy(() -> new PiiCipher("", environment)).isInstanceOf(IllegalStateException.class);
    }
}
