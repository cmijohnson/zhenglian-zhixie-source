package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.api.CustomerDtos;
import com.yingge.cloud.customer.application.CustomerService;
import com.yingge.cloud.deal.application.DealConfirmationService;
import com.yingge.cloud.feishu.api.FeishuDealBotDtos;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.MDC;
import java.util.Set;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class WecomAiBotCommandService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("M-d HH:mm").withZone(BUSINESS_ZONE);
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final WecomAiBotBindingService bindings;
    private final WecomActorExecutor actorExecutor;
    private final CustomerService customers;
    private final DealConfirmationService deals;
    private final WecomAiBotSettings settings;
    private final WecomAiBotRuntimeStatus runtime;
    private final WecomAgentPolicyService policy;
    private final WecomAgentCipher cipher;
    private final WecomMcpAuthorizationService authorization;
    private final WecomDealConversationService conversations;
    private final WecomNaturalLanguageService naturalLanguage;
    private final TransactionTemplate transactions;

    public WecomAiBotCommandService(JdbcTemplate jdbc, CurrentActorProvider actors,
                                    WecomAiBotBindingService bindings,
                                    WecomActorExecutor actorExecutor, CustomerService customers,
                                    DealConfirmationService deals, WecomAiBotSettings settings,
                                    WecomAiBotRuntimeStatus runtime, WecomAgentPolicyService policy,
                                    WecomAgentCipher cipher, WecomMcpAuthorizationService authorization,
                                    WecomDealConversationService conversations, WecomNaturalLanguageService naturalLanguage,
                                    org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.actors = actors;
        this.bindings = bindings;
        this.actorExecutor = actorExecutor;
        this.customers = customers;
        this.deals = deals;
        this.settings = settings;
        this.runtime = runtime;
        this.policy = policy; this.cipher = cipher; this.authorization = authorization;
        this.conversations = conversations; this.naturalLanguage = naturalLanguage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Optional<String> handle(UUID organizationId, JsonNode body) {
        return handle(organizationId, body, false);
    }

    // Only the authenticated, bot-scoped official socket adapter calls this entry point.
    // WeCom may omit the @ marker from callback text; never infer this trust from a body field.
    public Optional<String> handleAuthenticatedCallback(UUID organizationId, JsonNode body) {
        return handle(organizationId, body, true);
    }

    public boolean maySendProgress(UUID organizationId, JsonNode body) {
        if (organizationId == null || !"text".equals(text(body, "msgtype"))
                || !settings.botId().equals(text(body, "aibotid"))) return false;
        String type = chatType(body);
        String user = body.path("from").path("userid").asText("");
        if (user.isBlank() || user.length() > 160 || text(body, "msgid").isBlank()) return false;
        String chat = "single".equals(type) ? "single:" + user : text(body, "chatid");
        return chat.length() <= 256 && policy.allows(organizationId, chat, type);
    }

    public boolean maySendCardProgress(UUID organizationId, JsonNode body) {
        if (organizationId == null || !settings.botId().equals(text(body, "aibotid"))
                || !"event".equals(text(body, "msgtype"))
                || !"template_card_event".equals(body.path("event").path("eventtype").asText())) return false;
        String task = body.path("event").path("task_id").asText("");
        if (!task.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || !body.path("event").path("event_key").asText("").equals("confirm:" + task)) return false;
        String type = chatType(body);
        String user = body.path("from").path("userid").asText("");
        if (user.isBlank() || user.length() > 160 || text(body, "msgid").isBlank()) return false;
        String chat = "single".equals(type) ? "single:" + user : text(body, "chatid");
        return chat.length() <= 256 && policy.allows(organizationId, chat, type);
    }

    private Optional<String> handle(UUID organizationId, JsonNode body, boolean authenticatedBotCallback) {
        runtime.messageReceived();
        String messageId = text(body, "msgid");
        String userId = body.path("from").path("userid").asText("");
        String chatType = chatType(body);
        String chatId = "single".equals(chatType) ? "single:" + userId : text(body, "chatid");
        if (organizationId == null || messageId.isBlank() || userId.isBlank() || messageId.length() > 256
                || userId.length() > 160 || chatId.length() > 256) return Optional.empty();
        if (!body.path("aibotid").asText("").equals(settings.botId())) return Optional.empty();
        String command = normalizeCommand(stripBotMention(body.path("text").path("content").asText(""), settings.botId()));
        boolean cardEvent = "event".equals(body.path("msgtype").asText())
                && "template_card_event".equals(body.path("event").path("eventtype").asText());
        String raw = body.path("text").path("content").asText("").strip();
        if ("group".equals(chatType) && !cardEvent && !authenticatedBotCallback
                && raw.equals(stripBotMention(raw, settings.botId()))) return Optional.empty();
        if (cardEvent) {
            String key = body.path("event").path("event_key").asText("");
            String task = body.path("event").path("task_id").asText("");
            if (!key.matches("confirm:[0-9a-fA-F-]{36}") || !key.substring(8).equals(task)) return Optional.empty();
            command = "/confirm-token " + key.substring(8);
        }
        String verb = verb(command);
        if ("/bind".equals(verb)) {
            if (!"single".equals(chatType)) return Optional.of("请私聊机器人发送绑定码，不要在群内公开。");
            try { return Optional.of("✅ 已绑定「" + bindings.consumeCode(organizationId, userId, args(command))
                    + "」。请在英格云账户设置授予 Agent 权限。"); }
            catch (ApiException exception) { return Optional.of(exception.getMessage()); }
        }
        if (!policy.allows(organizationId, chatId, chatType)) {
            // Keep only authenticated routing metadata so an administrator can identify the
            // exact chat_id before enabling a group. Never retain the message body here.
            if (authenticatedBotCallback && "group".equals(chatType) && !chatId.isBlank()) {
                UUID ignored = remember(organizationId, messageId, text(body, "req_id"),
                        chatId, chatType, userId);
                if (ignored != null) finish(ignored, "IGNORED", "UNREGISTERED_CHAT", "/discovery");
            }
            return Optional.empty();
        }
        if (!"text".equals(body.path("msgtype").asText()) && !cardEvent) {
            return Optional.of("当前支持文字和 Slash。文件、图片、语音请在 CRM 核实后录入。");
        }
        UUID membershipId = bindings.activeMembership(organizationId, userId);
        if (membershipId == null) return Optional.of("尚未绑定或账号已停用。请登录英格云账户设置生成绑定码，并私聊机器人绑定。");
        if (command.length() > 8000) return Optional.of("消息过长，请缩短到 8000 字以内或分步处理。");
        String routedCommand = command;
        return actorExecutor.as(membershipId, () -> process(organizationId, membershipId, userId, chatId, chatType, messageId, body, routedCommand));
    }

    private Optional<String> process(UUID org, UUID member, String userId, String chat,
                                     String chatType, String messageId, JsonNode body, String command) {
        String verb = verb(command);
        try {
            if ("/help".equals(verb)) return Optional.of(help());
            if ("/crm".equals(verb)) return Optional.of(entry("/wecom/crm", "打开完整 CRM"));
            if ("/deal-web".equals(verb)) return Optional.of(entry("/wecom/deals", "打开 CRM 成单入口"));
            if ("/status".equals(verb)) return Optional.of("账号已绑定；Agent 授权：" + (authorization.current().active() ? "有效" : "未授权或已失效")
                    + "\n" + entry("/settings", "检查授权"));
            authorization.requireAuthorization(org, member);
            if (command.startsWith("/")) authorization.requireScope(org, member, readCommand(verb) ? "yingge.read" : "yingge.write");
            var existing = jdbc.query("select id,reply_ciphertext from wecom_ai_bot_event_inbox where organization_id=? and message_id=?",
                    (rs, i) -> new Inbox(rs.getObject(1, UUID.class), rs.getBytes(2)), org, messageId);
            if (!existing.isEmpty()) {
                Inbox row = existing.getFirst();
                return Optional.of(row.reply() == null ? "此消息已接收，正在处理或结果待核对。使用 /current 查看，请勿重复提交。追踪号：" + row.id()
                        : cipher.decryptBytes(row.reply()));
            }
            UUID inboxId = UUID.randomUUID();
            int inserted = jdbc.update("""
                    insert into wecom_ai_bot_event_inbox
                      (id,organization_id,message_id,request_id,chat_id,chat_type,wecom_userid,status,processing_started_at)
                    values (?,?,?,?,?,?,?,'PROCESSING',now()) on conflict (organization_id,message_id) do nothing
                    """, inboxId, org, messageId, text(body,"req_id"), chat, chatType, userId);
            if (inserted == 0) return Optional.empty();
            String previous = MDC.get("wecomEventId"); MDC.put("wecomEventId", inboxId.toString());
            String previousTrace = MDC.get("traceId"); MDC.put("traceId", inboxId.toString().replace("-", ""));
            try {
                String result;
                if (command.startsWith("/")) {
                    result = transactions.execute(status -> {
                        String reply = executeSlash(org, member, chat, command);
                        finishReply(inboxId, "APPLIED", null, verb, reply);
                        return reply;
                    });
                } else {
                    result = naturalLanguage.respond(org, member, chat, command, inboxId.toString(),
                            proposal -> executeModelCommand(org, member, chat, proposal));
                    finishReply(inboxId, "APPLIED", null, "NATURAL", result);
                }
                bindings.touch(org, userId);
                return Optional.of(limit(result));
            } catch (ApiException exception) {
                String reply = exception.getMessage() + "\n追踪号：" + inboxId;
                finishReply(inboxId,"REJECTED",exception.code(),verb,reply);
                return Optional.of(reply);
            } catch (RuntimeException exception) {
                String reply = "执行未正常结束，请先使用 /current 或 CRM 核对结果，勿重复写入。追踪号：" + inboxId;
                finishReply(inboxId,"UNCERTAIN","WECOM_AGENT_EXECUTION_UNCERTAIN",verb,reply);
                return Optional.of(reply);
            } finally {
                if (previous == null) MDC.remove("wecomEventId"); else MDC.put("wecomEventId",previous);
                if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId",previousTrace);
            }
        } catch (ApiException exception) { return Optional.of(exception.getMessage() + "\n" + entry("/settings","检查授权")); }
    }

    private String executeSlash(UUID org, UUID member, String chat, String command) {
        return conversations.execute(org, member, chat, command);
    }

    private String executeModelCommand(UUID org, UUID member, String chat, String proposal) {
        String command = normalizeCommand(proposal);
        String verb = verb(command);
        if (Set.of("/confirm","/confirm-token","/new-confirm").contains(verb)) {
            return "此操作必须本人输入命令确认或通过受保护审批页执行，模型不能代为确认。";
        }
        authorization.requireScope(org,member,readCommand(verb) ? "yingge.read" : "yingge.write");
        return actorExecutor.as(member, () -> transactions.execute(status -> executeSlash(org,member,chat,command)));
    }

    private static boolean readCommand(String verb) {
        return "yingge.read".equals(WecomDealConversationService.requiredScope(verb));
    }

    private void finishReply(UUID id,String state,String code,String command,String reply) {
        jdbc.update("update wecom_ai_bot_event_inbox set status=?,error_code=?,command_name=?,reply_ciphertext=?,processed_at=now() where id=?",
                state,code,command,cipher.encryptBytes(limit(reply)),id);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
    public void purgePrivateReplies() {
        jdbc.update("update wecom_ai_bot_event_inbox set reply_ciphertext=null where received_at < now()-interval '24 hours' and reply_ciphertext is not null");
    }

    private record Inbox(UUID id,byte[] reply) {}

    private String search(String query) {
        String clean = query == null ? "" : query.trim();
        if (clean.isEmpty() || clean.length() > 100) {
            throw ApiException.badRequest("WECOM_CUSTOMER_SEARCH_INVALID", "用法：/search 客户姓名、学校或联系方式关键词");
        }
        List<CustomerDtos.Response> hits = customers.search(clean, null, null, null, null, null,
                List.of(), false, null, null, null, 0, 5, "updatedDesc").items();
        if (hits.isEmpty()) return "未找到当前账号有权查看的客户。";
        StringBuilder text = new StringBuilder("客户搜索结果（最多 5 条）：\n");
        for (int i = 0; i < hits.size(); i++) {
            CustomerDtos.Response hit = hits.get(i);
            text.append(i + 1).append(". ").append(safe(hit.fullName()))
                    .append(" · ").append(blank(hit.school(), "未填学校"))
                    .append(" · 负责人 ").append(blank(hit.ownerDisplayName(), "未知"))
                    .append(" · 最近跟进 ").append(hit.lastContactAt() == null ? "暂无" : TIME.format(hit.lastContactAt()))
                    .append("\n");
        }
        text.append("\n完整筛选与编辑请使用 /crm。");
        return text.toString();
    }

    private String leaderboard(String value) {
        try {
            LocalDate date = value == null || value.isBlank()
                    ? LocalDate.now(BUSINESS_ZONE) : YearMonth.parse(value.trim()).atDay(1);
            return deals.leaderboardForCurrentActor(date).previewText();
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("DEAL_MONTH_INVALID", "月份格式应为 YYYY-MM");
        }
    }

    private String mine(String value) {
        try {
            LocalDate date = value == null || value.isBlank()
                    ? LocalDate.now(BUSINESS_ZONE) : YearMonth.parse(value.trim()).atDay(1);
            Instant from = date.withDayOfMonth(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            Instant to = date.plusMonths(1).withDayOfMonth(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            var actor = actors.require();
            return jdbc.query("""
                    select count(*) deal_count,coalesce(sum(request.order_amount_cents),0) order_total,
                           coalesce(sum(participant.bonus_cents),0) bonus_total
                    from customer_deal_participants participant
                    join customer_deal_requests request on request.id=participant.request_id
                    where participant.organization_id=? and participant.membership_id=?
                      and participant.participant_role='PRIMARY' and request.status='APPROVED'
                      and request.confirmed_at>=? and request.confirmed_at<?
                    """, (rs, index) -> "我的 " + date.getYear() + " 年 " + date.getMonthValue() + " 月成单\n"
                    + "主成单：" + rs.getLong("deal_count") + " 单\n"
                    + "主成单成交额：¥" + formatCents(rs.getLong("order_total")) + "\n"
                    + "主成单奖金：¥" + formatCents(rs.getLong("bonus_total")),
                    actor.organizationId(), actor.membershipId(), java.sql.Timestamp.from(from),
                    java.sql.Timestamp.from(to)).getFirst();
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("DEAL_MONTH_INVALID", "月份格式应为 YYYY-MM");
        }
    }

    private String entry(String path, String label) {
        if (settings.publicBaseUrl().isBlank()) {
            return "管理员尚未配置企业微信 CRM 公网入口，请先使用浏览器访问英格云。";
        }
        return label + "：\n" + settings.publicBaseUrl() + path;
    }

    private String help() {
        return """
                英格云助手 · 自然语言 / Slash 双模式
                可直接说：找一下张同学；给刚才客户记沟通；准备成单。
                /search 关键词  /select 序号  /next  /prev
                /new 客户名 [@负责人]  /new-confirm 原因
                /current  /detail  /members 关键词
                /owner @成员（转接需独立确认）
                /deal  /primary @成员  /assist @成员1 @成员2（清空用 none）
                /order 金额  /bonus 金额  /split @成员=金额 ...
                /set primary=@成员 assist=@成员 order=金额 bonus=金额 split=...
                /confirm（确认最新预览）  /cancel（取消草稿不删除客户）
                /mine [YYYY-MM]  /leaderboard [YYYY-MM]
                /crm  /deal-web  /status  /help
                人员可使用 /members 返回的成员 ID；授权范围与实际账号权限共同生效。
                普通明确写入直接执行；成单、转接和危险管理操作必须本人确认。
                """;
    }

    private UUID remember(UUID organizationId, String messageId, String requestId,
                          String chatId, String chatType, String userId) {
        if (messageId == null || messageId.isBlank()) return null;
        return jdbc.query("""
                insert into wecom_ai_bot_event_inbox
                  (id,organization_id,message_id,request_id,chat_id,chat_type,wecom_userid)
                values (?,?,?,?,?,?,?) on conflict (organization_id,message_id) do nothing returning id
                """, (rs, index) -> rs.getObject(1, UUID.class), UUID.randomUUID(), organizationId,
                messageId, requestId, chatId, chatType, userId).stream().findFirst().orElse(null);
    }

    private void finish(UUID id, String status, String errorCode, String command) {
        jdbc.update("""
                update wecom_ai_bot_event_inbox
                set status=?,error_code=?,command_name=?,processed_at=now() where id=?
                """, status, errorCode, command, id);
    }

    static String stripBotMention(String value, String botId) {
        String clean = value == null ? "" : value.strip();
        for (String prefix : new String[]{"<@" + botId + ">", "@英格云小格同学", "@机器人", "@英格云", "@yingge"}) {
            if (clean.startsWith(prefix) && (clean.length() == prefix.length()
                    || Character.isWhitespace(clean.charAt(prefix.length())))) return clean.substring(prefix.length()).stripLeading();
        }
        return clean;
    }

    static String normalizeCommand(String value) {
        String clean = stripBotMention(value, "");
        for (String[] alias : List.of(
                new String[]{"修改 成单人", "/primary"}, new String[]{"修改 协助", "/assist"},
                new String[]{"修改 订单", "/order"}, new String[]{"修改 奖金", "/bonus"},
                new String[]{"修改 分配", "/split"}, new String[]{"搜索", "/search"},
                new String[]{"选择", "/select"}, new String[]{"新建", "/new"})) {
            if (clean.equals(alias[0])) return alias[1];
            if (clean.startsWith(alias[0]) && clean.length() > alias[0].length()
                    && Character.isWhitespace(clean.charAt(alias[0].length()))) {
                return alias[1] + " " + clean.substring(alias[0].length()).strip();
            }
        }
        return switch (clean) {
            case "打开CRM", "打开crm", "客户系统" -> "/crm";
            case "打开成单页面", "快捷成单" -> "/deal-web";
            case "帮助", "教程" -> "/help";
            case "成单" -> "/deal";
            case "确认", "确认成单" -> "/confirm";
            case "取消" -> "/cancel";
            case "当前" -> "/current";
            case "详情" -> "/detail";
            case "榜单" -> "/leaderboard";
            case "我的成单" -> "/mine";
            default -> clean.isBlank() ? "/help" : clean;
        };
    }

    static String verb(String command) { return command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT); }
    static String args(String command) { String[] parts = command.split("\\s+", 2); return parts.length == 1 ? "" : parts[1].strip(); }

    private String text(JsonNode node, String field) { return node.path(field).asText(""); }
    private String chatType(JsonNode body) {
        String value = text(body, "chattype");
        if (value.isBlank() && "event".equals(text(body, "msgtype"))
                && "template_card_event".equals(body.path("event").path("eventtype").asText())) {
            return text(body, "chatid").isBlank() ? "single" : "group";
        }
        return value;
    }
    private String safe(String value) { return value == null ? "" : value.replace("<", "＜").replace(">", "＞"); }
    private String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : safe(value); }
    static String limit(String value) {
        if (value == null) return "";
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 20000) return value;
        int end = Math.min(value.length(), 6600);
        while (value.substring(0,end).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 19800) end--;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end-1))) end--;
        return value.substring(0,end) + "\n内容较长，请在 CRM 查看完整结果。";
    }
    private String formatCents(long cents) { return java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString(); }
}
