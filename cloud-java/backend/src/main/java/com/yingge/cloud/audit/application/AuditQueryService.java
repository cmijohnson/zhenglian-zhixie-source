package com.yingge.cloud.audit.application;

import com.yingge.cloud.audit.api.AuditController;
import com.yingge.cloud.audit.infrastructure.AuditLogRepository;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.permission.application.PermissionService;
import com.yingge.cloud.shared.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final AuditLogRepository repository;
    private final CurrentActorProvider actors;
    private final PermissionService permissions;

    public AuditQueryService(AuditLogRepository repository, CurrentActorProvider actors, PermissionService permissions) {
        this.repository = repository;
        this.actors = actors;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditController.AuditResponse> list(int page, int size) {
        var actor = actors.require();
        permissions.requireAdmin(actor);
        var result = repository.findByOrganizationIdOrderByCreatedAtDesc(
                actor.organizationId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
        ).map(row -> new AuditController.AuditResponse(row.getAction(), row.getEntityType(), row.getEntityId(),
                row.getOutcome(), row.getDetails(), row.getCreatedAt()));
        return PageResponse.from(result);
    }
}
