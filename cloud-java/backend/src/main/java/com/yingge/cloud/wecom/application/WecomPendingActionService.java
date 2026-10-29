package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.mcp.oauth.McpOAuthService;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Only an authenticated human endpoint can resume these stored, immutable MCP arguments. */
@Service
public class WecomPendingActionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentActorProvider actors;
    private final WecomMcpAuthorizationService authorization;
    private final WecomAgentMcpTransport transport;
    private final WecomAgentCipher cipher;
    private final WecomAgentPolicyService policy;
    private final WecomAiBotBindingService bindings;
    private final WecomMessageOutbox outbox;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private volatile String expiredArgumentsTombstone;

    public WecomPendingActionService(JdbcTemplate jdbc,ObjectMapper mapper,CurrentActorProvider actors,
            WecomMcpAuthorizationService authorization,WecomAgentMcpTransport transport,WecomAgentCipher cipher,
            WecomAgentPolicyService policy,WecomAiBotBindingService bindings,WecomMessageOutbox outbox,
            AuditService audit,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.mapper=mapper;this.actors=actors;this.authorization=authorization;this.transport=transport;
        this.cipher=cipher;this.policy=policy;this.bindings=bindings;this.outbox=outbox;this.audit=audit;
        transactions=new TransactionTemplate(manager);
    }

    public PendingView register(UUID org,UUID member,String chat,String key,String tool,JsonNode arguments,JsonNode output) {
        ApprovalSignal signal=approvalSignal(output);
        if(signal==null)return null;
        var actor=actors.require();
        if(!actor.organizationId().equals(org)||!actor.membershipId().equals(member))throw ApiException.forbidden("身份不匹配");
        WecomNaturalLanguageService.rejectForbidden(arguments);
        if(key==null||key.isBlank()||key.length()>160||tool==null||tool.length()>180||!arguments.isObject())
            throw ApiException.badRequest("WECOM_PENDING_INVALID","待确认操作格式无效");
        return transactions.execute(status->{
            var token=authorization.requireAuthorization(org,member);
            validateBindingAndPolicy(org,member,chat);
            ApprovalRow approval=approval(signal.approvalId(),org,member,tool,argumentsHash(mapper,arguments));
            if(!"PENDING".equals(approval.status())||!approval.expires().isAfter(Instant.now()))throw invalidApproval();
            UUID id=UUID.randomUUID();
            jdbc.update("""
                    insert into wecom_agent_pending_actions(id,organization_id,membership_id,chat_id,grant_id,
                      execution_key,tool_name,encrypted_arguments,approval_id,status,expires_at)
                    values(?,?,?,?,?,?,?,?,?,'PENDING',?) on conflict(organization_id,membership_id,execution_key) do nothing
                    """,id,org,member,chat,token.grantId(),key,tool,cipher.encrypt(json(arguments)),signal.approvalId(),Timestamp.from(approval.expires()));
            Pending row=jdbc.query("select * from wecom_agent_pending_actions where organization_id=? and membership_id=? and execution_key=?",
                    rowMapper(),org,member,key).stream().findFirst().orElseThrow(()->ApiException.unavailable("WECOM_PENDING_SAVE_FAILED","操作未能保存，未执行任何危险业务"));
            if(!row.tool().equals(tool)||!row.grantId().equals(token.grantId())
                    ||!argumentsHash(mapper,arguments).equals(argumentsHash(mapper,decryptArguments(row))))
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED","同一操作标识不能用于不同参数");
            audit.record(actor,"WECOM_MCP_APPROVAL_HANDOFF","WECOM_PENDING_ACTION",row.id().toString(),"PENDING",Map.of("tool",tool));
            return view(row);
        });
    }

    public List<PendingView> current() {
        var actor=actors.require();
        return jdbc.query("""
                select id,tool_name,approval_id,status,expires_at from wecom_agent_pending_actions
                where organization_id=? and membership_id=? order by created_at desc limit 100
                """,(r,i)->new PendingView(r.getObject("id",UUID.class),r.getString("tool_name"),
                approvalUrl(r.getObject("approval_id",UUID.class)),displayStatus(r.getString("status"),r.getTimestamp("expires_at").toInstant()),r.getTimestamp("expires_at").toInstant()),
                actor.organizationId(),actor.membershipId());
    }

    /** No user/model supplied tool, arguments, approvalId or grant is accepted here. */
    public String resume(UUID id) {
        var actor=actors.require();
        Claim claim=transactions.execute(status->{
            Pending row=owned(id,true);
            if(!"PENDING".equals(row.status()))return new Claim(row,terminalMessage(row));
            try {
                checkLive(row);
                var approval=approval(row.approvalId(),actor.organizationId(),actor.membershipId(),row.tool(),
                        argumentsHash(mapper,decryptArguments(row)));
                if("PENDING".equals(approval.status()))throw ApiException.conflict("MCP_APPROVAL_NOT_APPROVED","请先在人工审批页确认，再点击继续执行");
                if(!"APPROVED".equals(approval.status())||!approval.expires().isAfter(Instant.now()))throw invalidApproval();
                int changed=jdbc.update("update wecom_agent_pending_actions set status='SENDING',updated_at=now() where id=? and status='PENDING'",row.id());
                if(changed!=1)throw ApiException.conflict("WECOM_PENDING_IN_PROGRESS","操作正在处理，请先核对状态");
                return new Claim(row,null);
            }catch(ApiException e) {
                if("MCP_APPROVAL_NOT_APPROVED".equals(e.code()))throw e;
                jdbc.update("update wecom_agent_pending_actions set status='REJECTED',updated_at=now() where id=? and status='PENDING'",row.id());
                audit.record(actor,"WECOM_MCP_RESUME_REJECTED","WECOM_PENDING_ACTION",row.id().toString(),"REJECTED",Map.of("code",e.code()));
                return new Claim(row,"操作已拒绝："+e.getMessage()+"；未执行业务写入。");
            }
        });
        if(claim.message()!=null)return claim.message();
        Pending row=claim.row();
        boolean requestStarted=false;
        try {
            checkLive(row);
            var token=authorization.requireAuthorization(row.org(),row.member());
            try(var session=transport.open(authorization.accessToken(row.org(),row.member()),Duration.ofSeconds(15))) {
                JsonNode registered=null;
                for(JsonNode tool:session.request("tools/list",Map.of(),Duration.ofSeconds(15)).path("tools"))
                    if(row.tool().equals(tool.path("name").asText()))registered=tool;
                if(registered==null||!registered.path("inputSchema").path("properties").has("approvalId"))
                    throw new ApiException(HttpStatus.FORBIDDEN,"WECOM_PENDING_TOOL_REVOKED","工具不可用或当前权限已变化");
                String scope=registered.path("_meta").path("requiredScope").asText(registered.path("meta").path("requiredScope").asText(""));
                authorization.requireScope(row.org(),row.member(),scope);
                if(!token.grantId().equals(row.grantId()))throw ApiException.forbidden("Agent 授权已变化，请重新发起操作");
                ObjectNode args=decryptArguments(row);
                WecomNaturalLanguageService.rejectForbidden(args);
                WecomNaturalLanguageService.validateArguments(registered,args);
                args.put("approvalId",row.approvalId().toString());
                if(registered.path("inputSchema").path("properties").has("idempotencyKey"))args.put("idempotencyKey",row.key());
                requestStarted=true;
                JsonNode output=session.request("tools/call",Map.of("name",row.tool(),"arguments",args),Duration.ofSeconds(90));
                String state=outcome(output);
                String message=safeResult(row,output,state);
                finish(row,state,output,message);
                return message;
            }
        }catch(RuntimeException e) {
            String state=requestStarted?"UNCERTAIN":"REJECTED";
            String message=requestStarted?"操作结果待核对，请查看 CRM 和待确认记录，勿重复执行。追踪号："+row.id()
                    :"操作未执行：当前授权、绑定、权限或连接不可用。追踪号："+row.id();
            finish(row,state,mapper.createObjectNode().put("code",requestStarted?"WECOM_RESUME_UNCERTAIN":"WECOM_RESUME_REJECTED"),message);
            return message;
        }
    }

    private void checkLive(Pending row) {
        var actor=actors.require();
        if(!actor.organizationId().equals(row.org())||!actor.membershipId().equals(row.member()))throw ApiException.forbidden("身份不匹配");
        if(!row.expires().isAfter(Instant.now()))throw ApiException.conflict("WECOM_PENDING_EXPIRED","操作确认已过期，请重新发起");
        validateBindingAndPolicy(row.org(),row.member(),row.chat());
        if(!authorization.requireAuthorization(row.org(),row.member()).grantId().equals(row.grantId()))throw ApiException.forbidden("Agent 授权已撤销或更换");
    }
    private void validateBindingAndPolicy(UUID org,UUID member,String chat) {
        if(!bindings.current().bound())throw ApiException.forbidden("企业微信绑定已失效");
        String type=chat.startsWith("single:")?"single":"group";
        if("single".equals(type)&&!member.equals(bindings.activeMembership(org,chat.substring(7))))throw ApiException.forbidden("企业微信身份已变化");
        if(!policy.allows(org,chat,type))throw ApiException.forbidden("业务群或 Agent 已停用");
    }
    private ApprovalRow approval(UUID id,UUID org,UUID member,String tool,String hash) {
        return jdbc.query("""
                select status,expires_at from mcp_approval_requests
                where id=? and organization_id=? and membership_id=? and client_id=? and tool_name=? and arguments_hash=?
                """,(r,i)->new ApprovalRow(r.getString("status"),r.getTimestamp("expires_at").toInstant()),
                id,org,member,McpOAuthService.WECOM_AGENT_CLIENT_ID,tool,hash).stream().findFirst().orElseThrow(WecomPendingActionService::invalidApproval);
    }
    private Pending owned(UUID id,boolean lock) {
        var actor=actors.require();
        return jdbc.query("select * from wecom_agent_pending_actions where id=? and organization_id=? and membership_id=?"+(lock?" for update":""),
                rowMapper(),id,actor.organizationId(),actor.membershipId()).stream().findFirst()
                .orElseThrow(()->ApiException.notFound("WECOM_PENDING_NOT_FOUND","待确认操作不存在"));
    }
    private void finish(Pending row,String state,JsonNode result,String message) {
        transactions.executeWithoutResult(status->{
            int changed=jdbc.update("""
                    update wecom_agent_pending_actions set status=?,encrypted_result=?,updated_at=now()
                    where id=? and organization_id=? and membership_id=? and status='SENDING'
                    """,state,cipher.encrypt(json(result)),row.id(),row.org(),row.member());
            if(changed!=1)throw ApiException.conflict("WECOM_PENDING_STATE_CHANGED","操作状态发生变化，请先核对结果");
            audit.record(actors.require(),"WECOM_MCP_RESUMED","WECOM_PENDING_ACTION",row.id().toString(),state,Map.of("tool",row.tool()));
            outbox.enqueue(row.org(),row.chat(),"pending-result:"+row.id(),message);
        });
    }
    private ObjectNode decryptArguments(Pending row) {
        try {JsonNode value=mapper.readTree(cipher.decrypt(row.args()));if(!value.isObject())throw new IllegalArgumentException();return (ObjectNode)value;}
        catch(Exception e){throw ApiException.unavailable("WECOM_PENDING_CORRUPT","待确认参数无法安全读取，请重新发起");}
    }
    private String terminalMessage(Pending row) {
        return switch(row.status()) {
            case "APPLIED"->"该操作已完成，不会重复执行。追踪号："+row.id();
            case "SENDING"->"操作正在处理，请勿重复提交。追踪号："+row.id();
            case "UNCERTAIN"->"操作结果待核对，请先查看 CRM，不自动重试。追踪号："+row.id();
            default->"该操作已拒绝或失效，请重新发起。追踪号："+row.id();
        };
    }
    private String safeResult(Pending row,JsonNode output,String state) {
        if("APPLIED".equals(state))return "已按本人审批执行「"+row.tool()+"」，请在 CRM 查看结果。追踪号："+row.id();
        if("REJECTED".equals(state))return "服务端拒绝执行「"+row.tool()+"」，请在 CRM 核对权限和当前业务状态。追踪号："+row.id();
        return "操作结果待核对，请查看 CRM，勿重复执行。追踪号："+row.id();
    }
    static String outcome(JsonNode output) {
        JsonNode data=output.path("structuredContent");
        // Contradictory or approval-bearing responses must never announce successful execution.
        if(data.path("approvalRequired").asBoolean(false))return "REJECTED";
        if(output.path("isError").asBoolean(false)||data.path("error").isObject())return "REJECTED";
        if(data.path("ok").isBoolean()&&data.path("ok").asBoolean())return "APPLIED";
        return "UNCERTAIN";
    }
    static ApprovalSignal approvalSignal(JsonNode output) {
        JsonNode data=output.path("structuredContent");
        if(!data.path("approvalRequired").asBoolean(false))return null;
        try {
            UUID id=UUID.fromString(data.path("approval").path("approvalId").asText());
            Instant expires=Instant.parse(data.path("approval").path("expiresAt").asText());
            return new ApprovalSignal(id,expires);
        }catch(Exception e){throw ApiException.unavailable("WECOM_APPROVAL_RESPONSE_INVALID","人工审批响应无法安全识别，未执行业务");}
    }
    static String argumentsHash(ObjectMapper mapper,JsonNode args) {
        try {
            Map<String,Object> normalized=new java.util.TreeMap<>(mapper.convertValue(args,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
            normalized.remove("approvalId");normalized.remove("idempotencyKey");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical(normalized))));
        }catch(Exception e){throw ApiException.badRequest("WECOM_PENDING_ARGUMENTS_INVALID","待确认参数格式无效");}
    }
    private static Object canonical(Object value) {
        if(value instanceof Map<?,?> map){Map<String,Object> sorted=new java.util.TreeMap<>();map.forEach((k,v)->sorted.put(String.valueOf(k),canonical(v)));return sorted;}
        if(value instanceof java.util.Collection<?> list)return list.stream().map(WecomPendingActionService::canonical).toList();
        return value;
    }
    @Scheduled(fixedDelay=60_000,initialDelay=60_000)
    public void staleExecutions() {
        jdbc.update("""
                update wecom_agent_pending_actions set status='UNCERTAIN',updated_at=now()
                where status='SENDING' and updated_at<now()-interval '5 minutes'
                """);
        if(cipher.configured()) {
            if(expiredArgumentsTombstone==null)expiredArgumentsTombstone=cipher.encrypt("{}");
            jdbc.update("""
                update wecom_agent_pending_actions set encrypted_arguments=?,encrypted_result=null,
                    status=case when status='PENDING' then 'REJECTED' else status end,updated_at=now()
                where created_at<now()-interval '24 hours' and encrypted_arguments<>?
                """,expiredArgumentsTombstone,expiredArgumentsTombstone);
        }
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Unable to encode pending action");}}
    private PendingView view(Pending row){return new PendingView(row.id(),row.tool(),approvalUrl(row.approvalId()),displayStatus(row.status(),row.expires()),row.expires());}
    static String displayStatus(String status,Instant expires){return "PENDING".equals(status)&&!expires.isAfter(Instant.now())?"EXPIRED":status;}
    private static String approvalUrl(UUID id){return "/agent/approvals/"+id;}
    private static ApiException invalidApproval(){return ApiException.conflict("MCP_APPROVAL_INVALID","审批已拒绝、失效或与当前账号及操作参数不匹配");}
    private RowMapper<Pending> rowMapper(){return (r,i)->new Pending(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),
            r.getObject("membership_id",UUID.class),r.getString("chat_id"),r.getObject("grant_id",UUID.class),r.getString("execution_key"),
            r.getString("tool_name"),r.getString("encrypted_arguments"),r.getObject("approval_id",UUID.class),r.getString("status"),r.getTimestamp("expires_at").toInstant());}
    public record PendingView(UUID id,String toolName,String approvalUrl,String status,Instant expiresAt){}
    static record ApprovalSignal(UUID approvalId,Instant expiresAt){}
    private record ApprovalRow(String status,Instant expires){}
    private record Claim(Pending row,String message){}
    private record Pending(UUID id,UUID org,UUID member,String chat,UUID grantId,String key,String tool,String args,UUID approvalId,String status,Instant expires) {
        @Override public String toString(){return "Pending[id="+id+",status="+status+"]";}
    }
}
