package com.yingge.cloud.wecom.application;

import com.yingge.cloud.auth.infrastructure.CloudPrincipal;
import com.yingge.cloud.auth.infrastructure.CloudUserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class WecomActorExecutor {
    private final CloudUserDetailsService users;

    public WecomActorExecutor(CloudUserDetailsService users) {
        this.users = users;
    }

    public <T> T as(UUID membershipId, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            CloudPrincipal principal = users.loadByMembershipId(membershipId);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, principal.getPassword(), principal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
