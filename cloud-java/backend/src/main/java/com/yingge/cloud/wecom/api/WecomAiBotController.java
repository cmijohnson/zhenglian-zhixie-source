package com.yingge.cloud.wecom.api;

import com.yingge.cloud.permission.domain.RoleCode;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.shared.api.ApiException;
import com.yingge.cloud.wecom.application.WecomAiBotBindingService;
import com.yingge.cloud.wecom.application.WecomAiBotRuntimeStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wecom/aibot")
public class WecomAiBotController {
    private final WecomAiBotBindingService bindings;
    private final WecomAiBotRuntimeStatus status;
    private final CurrentActorProvider actors;

    public WecomAiBotController(WecomAiBotBindingService bindings, WecomAiBotRuntimeStatus status,
                                CurrentActorProvider actors) {
        this.bindings = bindings;
        this.status = status;
        this.actors = actors;
    }

    @GetMapping("/binding")
    public WecomAiBotDtos.BindingResponse binding() { return bindings.current(); }

    @PostMapping("/binding-code")
    public WecomAiBotDtos.BindingCodeResponse bindingCode() { return bindings.issueCode(); }

    @DeleteMapping("/binding")
    public void unbind(@Valid @RequestBody WecomAiBotDtos.UnbindRequest body) {
        bindings.unbind(body.currentPassword());
    }

    @GetMapping("/status")
    public WecomAiBotDtos.StatusResponse status() {
        if (!actors.require().hasRole(RoleCode.ORG_ADMIN)) throw ApiException.forbidden("仅组织管理员可查看机器人运行状态");
        return status.response();
    }
}
