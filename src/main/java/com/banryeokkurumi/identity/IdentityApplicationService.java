package com.banryeokkurumi.identity;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class IdentityApplicationService implements UserDetailsService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public IdentityApplicationService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public MemberView register(RegisterCommand command) {
        String loginId = command.loginId().strip().toLowerCase(Locale.ROOT);
        if (!loginId.matches("[a-z0-9][a-z0-9_-]{3,49}")) {
            throw new IllegalArgumentException("아이디 형식이 올바르지 않습니다.");
        }
        if (command.password().length() < 8 || command.password().length() > 72) {
            throw new IllegalArgumentException("비밀번호는 8자 이상 72자 이하여야 합니다.");
        }
        MemberEntity entity = new MemberEntity(UUID.randomUUID(), loginId, passwordEncoder.encode(command.password()),
                command.name().strip(), MemberRole.USER, Instant.now(clock));
        try {
            return MemberView.from(memberRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateLoginIdException();
        }
    }

    @Transactional(readOnly = true)
    public MemberView findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId.toLowerCase(Locale.ROOT))
                .map(MemberView::from)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다."));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        MemberEntity member = memberRepository.findByLoginId(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        return User.withUsername(member.loginId).password(member.encodedPassword).roles(member.role.name()).build();
    }

    @Transactional
    public void createInitialAdmin(String loginId, String rawPassword) {
        if (memberRepository.findByLoginId(loginId.toLowerCase(Locale.ROOT)).isPresent()) {
            return;
        }
        memberRepository.save(new MemberEntity(UUID.randomUUID(), loginId.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(rawPassword), "관리자", MemberRole.ADMIN, Instant.now(clock)));
    }

    public record RegisterCommand(String loginId, String password, String name) {
        public RegisterCommand {
            if (loginId == null || password == null || name == null || name.isBlank()) {
                throw new IllegalArgumentException("회원가입 필드는 필수입니다.");
            }
        }
        @Override public String toString() { return "RegisterCommand[loginId=" + loginId + ", password=***, name=" + name + "]"; }
    }

    public record MemberView(UUID id, String loginId, String name, MemberRole role) {
        static MemberView from(MemberEntity member) { return new MemberView(member.id, member.loginId, member.name, member.role); }
    }

    public static final class DuplicateLoginIdException extends RuntimeException {
        public DuplicateLoginIdException() { super("이미 사용 중인 아이디입니다."); }
    }
}
