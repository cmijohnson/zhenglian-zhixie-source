package com.yingge.cloud.wecom.application;

import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.permission.domain.RoleCode;
import com.yingge.cloud.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WecomAgentPolicyServiceTest {
    @Test void missingConfigurationDefaultsToDisabledAndDeniesEveryChat() {
        var fixture=new Fixture(RoleCode.ORG_ADMIN);
        assertThat(fixture.service.current(fixture.org)).isEqualTo(new WecomAgentPolicyService.Policy(false,"","",false,0));
        assertThat(fixture.service.allows(fixture.org,"business","group")).isFalse();
        assertThat(fixture.service.allows(fixture.org,"single:user","single")).isFalse();
    }

    @Test void enabledAndAcceptedPolicyAllowsOnlyBusinessGroupOrPrivateChat() {
        var fixture=new Fixture(RoleCode.ORG_ADMIN);
        var service=spy(fixture.service);
        doReturn(new WecomAgentPolicyService.Policy(true,"business","broadcast",true,1)).when(service).current(fixture.org);
        assertThat(service.allows(fixture.org,"business","group")).isTrue();
        assertThat(service.allows(fixture.org,"broadcast","group")).isFalse();
        assertThat(service.allows(fixture.org,"unregistered","group")).isFalse();
        assertThat(service.allows(fixture.org,"single:user","single")).isTrue();
        assertThat(service.allows(fixture.org,"business","unknown")).isFalse();
        doReturn(new WecomAgentPolicyService.Policy(true,"business","broadcast",false,1)).when(service).current(fixture.org);
        assertThat(service.allows(fixture.org,"business","group")).isFalse();
        assertThat(service.allows(fixture.org,"single:user","single")).isFalse();
        doReturn(new WecomAgentPolicyService.Policy(false,"business","broadcast",true,1)).when(service).current(fixture.org);
        assertThat(service.allows(fixture.org,"business","group")).isFalse();
    }

    @Test void membersViewersAndTeamManagersCannotReadOrWriteControlPlane() {
        for(RoleCode role:List.of(RoleCode.MEMBER,RoleCode.TEAM_MANAGER,RoleCode.VIEWER)) {
            var fixture=new Fixture(role);
            assertThatThrownBy(fixture.service::administratorView).isInstanceOf(ApiException.class)
                    .satisfies(error->assertThat(((ApiException)error).code()).isEqualTo("FORBIDDEN"));
            assertThatThrownBy(()->fixture.service.update(new WecomAgentPolicyService.Update(false,"","",false,0)))
                    .isInstanceOf(ApiException.class);
            assertThat(fixture.jdbc.updates).isZero();
            verifyNoInteractions(fixture.audit);
        }
    }

    @Test void enableRequiresSeparateGroupsRiskAcceptanceAndConfiguredCipher() {
        var fixture=new Fixture(RoleCode.ORG_ADMIN);
        assertCode(()->fixture.service.update(new WecomAgentPolicyService.Update(true,"same","same",true,0)),"WECOM_GROUPS_INVALID");
        assertCode(()->fixture.service.update(new WecomAgentPolicyService.Update(true,"business","broadcast",false,0)),"WECOM_GROUP_RISK_CONFIRMATION_REQUIRED");
        when(fixture.cipher.configured()).thenReturn(false);
        assertCode(()->fixture.service.update(new WecomAgentPolicyService.Update(true,"business","broadcast",true,0)),"WECOM_ENCRYPTION_UNAVAILABLE");
        assertCode(()->fixture.service.update(new WecomAgentPolicyService.Update(false,"group\nunsafe","",false,0)),"WECOM_CHAT_ID_INVALID");
        assertThat(fixture.jdbc.updates).isZero();
        verifyNoInteractions(fixture.audit);
    }

    @Test void staleVersionNeverRecordsSuccessfulPolicyAudit() {
        var fixture=new Fixture(RoleCode.ORG_ADMIN);
        when(fixture.cipher.configured()).thenReturn(true);
        fixture.jdbc.changed=0;
        assertCode(()->fixture.service.update(new WecomAgentPolicyService.Update(true,"business","broadcast",true,9)),"STALE_VERSION");
        assertThat(fixture.jdbc.lastArgs[fixture.jdbc.lastArgs.length-1]).isEqualTo(9L);
        verifyNoInteractions(fixture.audit);
    }

    private void assertCode(Runnable action,String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .satisfies(error->assertThat(((ApiException)error).code()).isEqualTo(code));
    }
    private static final class Fixture {
        final UUID org=UUID.randomUUID();
        final PolicyJdbc jdbc=new PolicyJdbc();
        final AuditService audit=mock(AuditService.class);
        final WecomAgentCipher cipher=mock(WecomAgentCipher.class);
        final WecomAgentPolicyService service;
        Fixture(RoleCode role) {
            var actors=mock(CurrentActorProvider.class);
            when(actors.require()).thenReturn(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),org,"test","test",Set.of(role),Set.of(),Set.of()));
            service=new WecomAgentPolicyService(jdbc,actors,audit,cipher);
        }
    }
    private static final class PolicyJdbc extends JdbcTemplate {
        int changed=1,updates;
        Object[] lastArgs;
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object...args) { return List.of(); }
        @Override public int update(String sql,Object...args) { updates++;lastArgs=args;return changed; }
    }
}
