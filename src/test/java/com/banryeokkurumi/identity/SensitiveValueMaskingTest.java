package com.banryeokkurumi.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueMaskingTest {

    @Test
    void 회원가입명령은_비밀번호를_문자열에_노출하지않는다() {
        var command = new IdentityApplicationService.RegisterCommand("member", "never-log-this", "회원");

        assertThat(command.toString())
                .doesNotContain("never-log-this")
                .contains("password=***");
    }
}
