package com.yingge.cloud.auth.application;

import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.organization.infrastructure.UserAccountRepository;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CurrentActorProvider actors;
    private final AuditService audit;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, CurrentActorProvider actors, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.actors = actors;
        this.audit = audit;
    }

    @Transactional
    public void recordLogin() {
        var actor = actors.require();
        var user = users.findById(actor.userId()).orElseThrow();
        user.recordLogin(Instant.now());
        audit.record(actor, "AUTH_LOGIN", "USER", actor.userId().toString(), "SUCCESS", null);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        var actor = actors.require();
        var user = users.findById(actor.userId()).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw ApiException.badRequest("PASSWORD_REQUIRED", "新密码不能为空");
        }
        if (newPassword.length() > 200) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "新密码不能超过 200 个字符");
        }
        user.changePassword(passwordEncoder.encode(newPassword), Instant.now());
        audit.record(actor, "AUTH_PASSWORD_CHANGED", "USER", actor.userId().toString(), "SUCCESS", null);
    }
}
