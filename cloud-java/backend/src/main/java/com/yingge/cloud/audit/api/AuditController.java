package com.yingge.cloud.audit.api;

import com.yingge.cloud.audit.infrastructure.AuditLogRepository;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.permission.application.PermissionService;
import com.yingge.cloud.shared.api.PageResponse;
import com.yingge.cloud.audit.application.AuditQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditController {
    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return service.list(page, size);
    }

    public record AuditResponse(String action, String entityType, String entityId, String outcome,
                                Map<String, Object> details, Instant createdAt) {}
}
