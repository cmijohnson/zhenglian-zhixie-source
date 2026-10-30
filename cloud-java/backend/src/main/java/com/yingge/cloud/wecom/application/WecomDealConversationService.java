package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.api.CustomerDtos;
import com.yingge.cloud.customer.application.CustomerService;
import com.yingge.cloud.deal.application.DealConfirmationService;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Deterministic workflow shared by slash commands and model-proposed draft patches. */
@Service
public class WecomDealConversationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SET_FIELD = Pattern.compile("(?:^|\\s)(primary|assist|order|bonus|split)=");
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final CustomerService customers;
    private final DealConfirmationService deals;
    private final ObjectMapper json;
    private final WecomAgentCipher cipher;
    private final WecomAgentPolicyService policy;

    public WecomDealConversationService(JdbcTemplate jdbc, CurrentActorProvider actors, CustomerService customers,
                                       DealConfirmationService deals, ObjectMapper json, WecomAgentCipher cipher,
                                       WecomAgentPolicyService policy) {
        this.jdbc = jdbc;
        this.actors = actors;
        this.customers = customers;
        this.deals = deals;
        this.json = json;
        this.cipher = cipher;
        this.policy = policy;
    }

    @Transactional
    public String execute(UUID organizationId, UUID membershipId, String chatId, String command) {
        CurrentActor actor = actors.require();
        if (!actor.organizationId().equals(organizationId) || !actor.membershipId().equals(membershipId)) {
            throw ApiException.forbidden("企业微信会话身份与当前账号不一致");
        }
        if (chatId == null || chatId.isBlank() || chatId.length() > 256) {
            throw ApiException.badRequest("WECOM_CHAT_INVALID", "会话标识无效");
        }
        String clean = command == null ? "" : command.trim();
        String[] parts = clean.split("\\s+", 2);
        String verb = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length < 2 ? "" : parts[1].trim();
        if ("yingge.write".equals(requiredScope(verb)) && !actor.canWriteCustomers()) {
            throw ApiException.forbidden("当前账号没有客户维护权限");
        }
        Stored stored = load(organizationId, membershipId, chatId);
        State state = stored.state;
        String result;
        switch (verb) {
            case "/search" -> result = search(state, args, 0);
            case "/next" -> result = search(state, required(state.query, "请先 /search 关键词"), state.page + 1);
            case "/prev" -> result = search(state, required(state.query, "请先 /search 关键词"), Math.max(0, state.page - 1));
            case "/select" -> result = select(state, args);
            case "/new" -> result = quickCreate(state, args, false, actor);
            case "/new-confirm" -> result = quickCreate(state, args, true, actor);
            case "/members" -> result = directory(organizationId, args);
            case "/owner" -> result = owner(state, args, actor);
            case "/deal" -> {
                requireCustomer(state);
                editable(state.customerId);
                if (state.confirmedRequestId != null) throw ApiException.conflict("CUSTOMER_ALREADY_WON", "该客户已成单");
                result = preview(state);
            }
            case "/current" -> result = preview(state);
            case "/detail" -> result = detail(state);
            case "/primary", "/assist", "/order", "/bonus", "/split", "/set" -> {
                requireCustomer(state);
                editable(state.customerId);
                ensureCanRevise(state);
                State before = copy(state);
                if ("/set".equals(verb)) applySet(state, args, actor); else patch(state, verb.substring(1), args, actor);
                validatePeople(state, organizationId);
                if (before.firstCompleteAt != null) state.revisions++;
                state.version++;
                state.pendingOwnerId = null;
                invalidatePreview(state);
                if (complete(state) && state.firstCompleteAt == null) state.firstCompleteAt = Instant.now().toString();
                result = preview(state);
            }
            case "/confirm" -> result = confirm(state, args, actor);
            case "/confirm-token" -> {
                if (state.previewToken==null || !state.previewToken.equals(args)) {
                    throw ApiException.conflict("WECOM_CONFIRMATION_STALE","确认卡片已失效，请 /current 重新预览");
                }
                result=confirm(state,Long.toString(state.version)+" "+args,actor);
            }
            case "/cancel" -> {
                state = new State();
                result = "已取消当前会话和草稿；已经创建的正式客户仍保留，已确认成单不会被撤销。";
            }
            case "/mine" -> result = mine(actor, args);
            case "/leaderboard" -> result = deals.leaderboardForCurrentActor(month(args)).previewText();
            default -> throw ApiException.badRequest("WECOM_COMMAND_UNKNOWN", "未知命令，请发送 /help 查看教程。相近操作：/search、/deal、/current、/confirm");
        }
        save(stored.id, organizationId, membershipId, chatId, state);
        return result;
    }

    public static String requiredScope(String command) {
        String verb = command == null ? "" : command.trim().split("\\s+", 2)[0];
        return switch (verb) {
            case "/new", "/new-confirm", "/owner", "/deal", "/primary", "/assist", "/order", "/bonus", "/split", "/set", "/confirm", "/confirm-token", "/cancel" -> "yingge.write";
            default -> "yingge.read";
        };
    }

    private Stored load(UUID org, UUID member, String chat) {
        // The lock key is identity-scoped, not controlled by a model-provided organization.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                org + ":" + member + ":" + chat);
        return jdbc.query("""
                select id,state_ciphertext,expires_at from wecom_deal_conversations
                where organization_id=? and membership_id=? and chat_id=? for update
                """, (rs, index) -> {
            State state = rs.getTimestamp("expires_at").toInstant().isAfter(Instant.now())
                    ? read(cipher.decrypt(rs.getString("state_ciphertext"))) : new State();
            return new Stored(rs.getObject("id", UUID.class), state);
        }, org, member, chat).stream().findFirst().orElseGet(() -> new Stored(UUID.randomUUID(), new State()));
    }

    private void save(UUID id, UUID org, UUID member, String chat, State state) {
        jdbc.update("""
                insert into wecom_deal_conversations(id,organization_id,membership_id,chat_id,state_ciphertext,expires_at)
                values(?,?,?,?,?,now()+interval '30 minutes')
                on conflict(organization_id,membership_id,chat_id) do update
                set state_ciphertext=excluded.state_ciphertext,expires_at=excluded.expires_at,
                    updated_at=now(),version=wecom_deal_conversations.version+1
                """, id, org, member, chat, cipher.encrypt(write(state)));
    }

    private String search(State state, String query, int page) {
        query = required(query, "用法：/search 姓名、昵称、学校或联系方式关键词");
        if (query.length() > 100) throw ApiException.badRequest("WECOM_SEARCH_INVALID", "搜索关键词不能超过100字");
        var hits = customers.search(query, null, null, null, null, null, List.of(), false,
                null, null, null, page, 5, "updatedDesc");
        state.query = query;
        state.page = page;
        state.searchIds = new ArrayList<>();
        StringBuilder result = new StringBuilder("可编辑客户搜索（第" + (page + 1) + "页）：\n");
        for (var customer : hits.items()) {
            if (actors.require().canWriteCustomers() && !customers.collaboration(customer.id()).canEditProfile()) continue;
            state.searchIds.add(customer.id());
            result.append(state.searchIds.size()).append(". ").append(customer.fullName())
                    .append(" · ").append(or(customer.school(), "未填学校"))
                    .append(" · 负责人 ").append(or(customer.ownerDisplayName(), "未知"))
                    .append(" · 最近跟进 ").append(customer.lastContactAt() == null ? "暂无" : customer.lastContactAt())
                    .append("\n");
        }
        if (state.searchIds.isEmpty()) result.append("本页没有可编辑客户。\n");
        return result.append("使用 /select 序号；/next、/prev 翻页。").toString();
    }

    private String select(State state, String args) {
        int selection;
        try { selection = Integer.parseInt(args); }
        catch (NumberFormatException exception) { throw ApiException.badRequest("WECOM_SELECTION_INVALID", "用法：/select 搜索结果序号"); }
        if (selection < 1 || selection > state.searchIds.size()) throw ApiException.badRequest("WECOM_SELECTION_INVALID", "序号不在当前搜索结果中");
        UUID id = state.searchIds.get(selection - 1);
        customers.get(id);
        switchCustomer(state, id);
        return preview(state);
    }

    private String quickCreate(State state, String args, boolean override, CurrentActor actor) {
        UUID owner = actor.membershipId();
        String name;
        String reason = null;
        if (override) {
            name = required(state.pendingNewName, "当前没有待确认的新客户，请先 /new 客户姓名");
            reason = required(args, "用法：/new-confirm 同名但继续新建的原因");
            if (reason.length() > 500) throw ApiException.badRequest("DUPLICATE_REASON_INVALID", "继续新建原因不能超过500字");
            owner = state.pendingNewOwner == null ? owner : state.pendingNewOwner;
        } else {
            name = required(args, "用法：/new 客户姓名 [@负责人]");
            int mention = Math.max(name.lastIndexOf(" @"), name.lastIndexOf(" <@"));
            if (mention >= 0) {
                owner = member(actor.organizationId(), name.substring(mention + 1));
                name = name.substring(0, mention).trim();
            }
            if (name.length() > 160) throw ApiException.badRequest("CUSTOMER_NAME_TOO_LONG", "客户姓名不能超过160字");
        }
        var duplicate = customers.duplicateCheck(new CustomerDtos.DuplicateRequest(name,null,null,null,null,null));
        if (duplicate.blocking()) throw ApiException.conflict("CUSTOMER_DUPLICATE_BLOCKING", "存在不可绕过的重复客户，请复用已有档案");
        String matchName = name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,
                actor.organizationId()+":quick-customer:"+matchName);
        Long sameNameCount = jdbc.queryForObject("""
                select count(*) from customers where organization_id=?
                and lower(regexp_replace(full_name,'[[:space:]]','','g'))=?
                """,Long.class,actor.organizationId(),matchName);
        if ((duplicate.requiresConfirmation() || (sameNameCount != null && sameNameCount > 0)) && !override) {
            state.pendingNewName = name;
            state.pendingNewOwner = owner;
            return "发现同名客户，请 /search " + name + " 后选择复用；确需新增时发送 /new-confirm 原因。"
                    + "同名客户数量：" + (sameNameCount == null ? duplicate.matches().size()+duplicate.hiddenExactMatches() : sameNameCount)
                    + "；仅在搜索中展示当前账号可见可选。";
        }
        if (state.customerId != null) requireNoOpenDraft(state);
        var created = customers.createFromWecomQuickEntry(name, owner, reason);
        switchCustomer(state, created.id());
        state.pendingNewName = null;
        state.pendingNewOwner = null;
        return "已创建正式客户。取消草稿不会删除此客户。\n" + preview(state);
    }

    private void switchCustomer(State state, UUID customerId) {
        if (state.customerId != null && !state.customerId.equals(customerId)) requireNoOpenDraft(state);
        if (!customerId.equals(state.customerId)) {
            state.customerId = customerId;
            state.primaryId = null;
            state.assistantIds.clear();
            state.orderCents = 0;
            state.bonusCents = 0;
            state.allocations.clear();
            state.firstCompleteAt = null;
            state.revisions = 0;
            state.confirmedRequestId = null;
            state.pendingOwnerId = null;
            state.version++;
            invalidatePreview(state);
        }
    }

    private void requireNoOpenDraft(State state) {
        if (state.confirmedRequestId == null && (state.primaryId != null || state.orderCents > 0 || state.bonusCents > 0)) {
            throw ApiException.conflict("WECOM_OPEN_DRAFT_EXISTS", "当前已有成单草稿，请先 /confirm 或 /cancel，再切换客户");
        }
    }

    private String owner(State state, String args, CurrentActor actor) {
        requireCustomer(state);
        var customer = editable(state.customerId);
        UUID target = member(actor.organizationId(), required(args,"用法：/owner @成员"));
        if (target.equals(customer.ownerMembershipId())) throw ApiException.badRequest("CUSTOMER_OWNER_UNCHANGED", "该成员已经是负责人");
        if (customers.collaboration(state.customerId).transferCandidates().stream().noneMatch(person -> person.membershipId().equals(target))) {
            throw ApiException.forbidden("此客户不能转接给该成员");
        }
        state.pendingOwnerId = target;
        state.ownerCustomerVersion = customer.version();
        state.version++;
        state.previewVersion = state.version;
        state.previewExpiresAt = Instant.now().plusSeconds(600).toString();
        state.previewToken = UUID.randomUUID().toString();
        return "负责人转接预览\n客户：" + customer.fullName() + "\n原负责人：" + customer.ownerDisplayName()
                + "\n新负责人：" + name(target) + "\n影响：负责人、待办提醒和归属团队将同步调整；成单贡献人不变。"
                + "\n请 /confirm " + state.version + " 确认，或 /cancel 取消。"
                + "\n[[WECOM_CONFIRM:"+state.previewToken+"]]";
    }

    private void patch(State state, String key, String value, CurrentActor actor) {
        switch (key) {
            case "primary" -> {
                state.primaryId = member(actor.organizationId(), required(value,"请选择主成单人"));
                defaultAllocations(state);
            }
            case "assist" -> {
                state.assistantIds = new ArrayList<>();
                if (!List.of("none","无","清空").contains(value.trim().toLowerCase(Locale.ROOT))) {
                    for (String person : value.trim().split("[,，、\\s]+")) {
                        if (!person.isBlank()) state.assistantIds.add(member(actor.organizationId(), person));
                    }
                }
                defaultAllocations(state);
            }
            case "order" -> state.orderCents = parseCents(value, true);
            case "bonus" -> { state.bonusCents = parseCents(value, true); defaultAllocations(state); }
            case "split" -> {
                var requested = new LinkedHashMap<UUID,Long>();
                for (String allocation : required(value,"用法：/split @成员=金额 ...").split("[,，\\s]+")) {
                    int equals = allocation.lastIndexOf('=');
                    if (equals <= 0) throw ApiException.badRequest("DEAL_SPLIT_INVALID", "用法：/split @成员=金额 ...");
                    UUID id = member(actor.organizationId(), allocation.substring(0, equals));
                    if (requested.put(id,parseCents(allocation.substring(equals+1),false)) != null) {
                        throw ApiException.badRequest("DEAL_PARTICIPANT_DUPLICATE", "奖金分配人员不能重复");
                    }
                }
                state.allocations = requested;
            }
            default -> throw ApiException.badRequest("DEAL_PATCH_INVALID", "不支持的草稿字段");
        }
    }

    private void applySet(State state, String value, CurrentActor actor) {
        var matcher = SET_FIELD.matcher(value);
        List<String> keys = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        while (matcher.find()) { keys.add(matcher.group(1)); starts.add(matcher.start()); ends.add(matcher.end()); }
        if (keys.isEmpty() || starts.getFirst() != 0 || new LinkedHashSet<>(keys).size() != keys.size()) {
            throw ApiException.badRequest("DEAL_SET_INVALID", "用法：/set primary=@成员 assist=none order=3000 bonus=600 split=@成员=600");
        }
        // Apply splits last, so a field order never silently overwrites explicitly supplied allocations.
        Map<String,String> fields = new LinkedHashMap<>();
        for (int i=0;i<keys.size();i++) fields.put(keys.get(i),value.substring(ends.get(i),i+1<keys.size()?starts.get(i+1):value.length()).trim());
        for (String key : List.of("primary","assist","order","bonus","split")) if (fields.containsKey(key)) patch(state,key,fields.get(key),actor);
    }

    private void defaultAllocations(State state) {
        state.allocations = new LinkedHashMap<>();
        if (state.primaryId != null) state.allocations.put(state.primaryId,state.bonusCents);
        state.assistantIds.forEach(id -> state.allocations.put(id,0L));
    }

    private void validatePeople(State state, UUID org) {
        if (state.assistantIds.size() > 2) throw ApiException.badRequest("DEAL_ASSISTANT_LIMIT", "协助人最多两名");
        var ids = new LinkedHashSet<UUID>(state.assistantIds);
        if (ids.size()!=state.assistantIds.size() || (state.primaryId!=null && ids.contains(state.primaryId))) {
            throw ApiException.badRequest("DEAL_PARTICIPANT_DUPLICATE", "主成单人和协助人不能重复");
        }
        if (state.primaryId != null) ids.add(state.primaryId);
        for (UUID id : ids) member(org,id.toString());
    }

    static long parseCents(String value, boolean positive) {
        try {
            if (value == null || !value.trim().matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
            long cents = new BigDecimal(value.trim()).movePointRight(2).longValueExact();
            if (cents < 0 || (positive && cents == 0)) throw new IllegalArgumentException();
            return cents;
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("DEAL_AMOUNT_INVALID", "金额须为" + (positive?"大于0":"非负") + "的人民币金额，最多两位小数且不能溢出");
        }
    }

    static boolean complete(State state) {
        if (state.customerId == null || state.primaryId == null || state.orderCents <= 0 || state.bonusCents <= 0) return false;
        var people = new LinkedHashSet<>(state.assistantIds);
        if (!people.add(state.primaryId) || people.size()!=state.assistantIds.size()+1 || people.size()>3) return false;
        if (!state.allocations.keySet().equals(people)) return false;
        long sum = 0;
        try {
            for (Long cents : state.allocations.values()) { if (cents == null || cents < 0) return false; sum = Math.addExact(sum,cents); }
        } catch (ArithmeticException exception) { return false; }
        return sum == state.bonusCents;
    }

    private String preview(State state) {
        if (state.customerId == null) return "尚未选择客户。请 /search 关键词 后 /select 序号，或 /new 客户姓名。";
        var customer = customers.get(state.customerId);
        StringBuilder text = new StringBuilder("当前客户：" + customer.fullName() + "\n客户ID：" + customer.id()
                + "\n学校：" + or(customer.school(),"未填") + "\n负责人：" + customer.ownerDisplayName());
        if (!customers.collaboration(state.customerId).canEditProfile()) {
            return text.append("\n当前账号只能查看此档案，请由负责人或有编辑权的成员继续成单；不会自动新增协作权限。").toString();
        }
        if (state.confirmedRequestId != null) return text.append("\n✅ 成单已确认，记录：").append(state.confirmedRequestId).toString();
        if (state.pendingOwnerId != null) return text.append("\n待确认负责人转接：").append(name(state.pendingOwnerId))
                .append("\n请 /confirm ").append(state.version).append(" 确认转接，或 /cancel。")
                .append("\n[[WECOM_CONFIRM:").append(state.previewToken).append("]]").toString();
        text.append("\n主成单人：").append(state.primaryId == null ? "待选择" : name(state.primaryId))
                .append("\n协助：").append(state.assistantIds.isEmpty()?"无":state.assistantIds.stream().map(this::name).reduce((a,b)->a+"、"+b).orElse("无"))
                .append("\n订单：¥").append(money(state.orderCents)).append("\n奖金：¥").append(money(state.bonusCents));
        state.allocations.forEach((id,cents)->text.append("\n  ").append(name(id)).append("：¥").append(money(cents)));
        if (complete(state)) {
            state.previewVersion = state.version;
            state.previewExpiresAt = Instant.now().plusSeconds(600).toString();
            if(state.previewToken==null)state.previewToken=UUID.randomUUID().toString();
            state.previewCustomerVersion = customer.version();
            var currentPolicy = policy.current(actors.require().organizationId());
            text.append("\n确认后立即成单、记入奖金账本，并公开广播至已配置群（")
                    .append(or(currentPolicy.broadcastChatId(),"未配置")).append("）。客户不会归档。")
                    .append("\n请 /confirm ").append(state.version)
                    .append(" 确认；修改次数 ").append(state.revisions).append("/3。")
                    .append("\n[[WECOM_CONFIRM:").append(state.previewToken).append("]]");
        } else text.append("\n尚不能确认：请补齐主成单人、正数订单/奖金，且每人分配合计等于奖金。使用 /set 或单项命令补充。");
        return text.toString();
    }

    private String confirm(State state, String requestedVersion, CurrentActor actor) {
        requireCustomer(state);
        if (state.confirmedRequestId != null) return "✅ 已确认成单，记录：" + state.confirmedRequestId;
        String[] confirmation=requestedVersion.isBlank()?new String[0]:requestedVersion.split("\\s+");
        if (confirmation.length>2 || (confirmation.length>0&&!confirmation[0].equals(Long.toString(state.version)))
                || (confirmation.length==2&&!confirmation[1].equals(state.previewToken))) {
            throw ApiException.conflict("WECOM_CONFIRMATION_STALE", "确认版本已过期，请 /current 重新预览");
        }
        if (state.previewVersion != state.version || state.previewExpiresAt == null
                || !Instant.parse(state.previewExpiresAt).isAfter(Instant.now())) {
            throw ApiException.conflict("WECOM_CONFIRMATION_EXPIRED", "请先 /current 查看最新预览，再明确确认");
        }
        var customer = editable(state.customerId);
        if (state.pendingOwnerId != null) {
            if (customer.version() != state.ownerCustomerVersion) throw ApiException.conflict("CUSTOMER_VERSION_CONFLICT", "客户已修改，请重新发起转接");
            UUID target = member(actor.organizationId(),state.pendingOwnerId.toString());
            customers.transferOwnership(state.customerId,new CustomerDtos.TransferOwnershipRequest(target,customer.version(),"企业微信本人确认转接"));
            state.pendingOwnerId = null;
            state.version++;
            invalidatePreview(state);
            return "✅ 负责人转接已完成；成单草稿保留。使用 /current 重新预览成单。";
        }
        if (customer.version() != state.previewCustomerVersion) throw ApiException.conflict("CUSTOMER_VERSION_CONFLICT", "客户已修改，请 /current 重新预览");
        if (!complete(state)) throw ApiException.badRequest("DEAL_DRAFT_INCOMPLETE", "成单草稿不完整或奖金分配不一致");
        validatePeople(state,actor.organizationId());
        String key = "wecom:" + actor.organizationId() + ":" + actor.membershipId() + ":" + state.customerId + ":" + state.version + ":" + state.previewToken;
        UUID request = deals.confirmFromWecom(new DealConfirmationService.FeishuConfirmation(state.customerId,
                state.primaryId,state.assistantIds,state.orderCents,state.bonusCents,state.allocations,key,state.previewCustomerVersion));
        state.confirmedRequestId = request;
        invalidatePreview(state);
        return "✅ 成单已确认，客户状态已更新；奖金已入账，广播已进入发送队列。\n记录：" + request;
    }

    private String directory(UUID org,String query) {
        var members = members(org);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        StringBuilder text = new StringBuilder("本组织已绑定且可维护客户的成员：\n");
        members.stream().filter(member -> needle.isEmpty() || member.displayName.toLowerCase(Locale.ROOT).contains(needle))
                .limit(50).forEach(member->text.append(member.displayName).append(" · @").append(member.wecomUserId)
                        .append(" · ").append(member.id).append("\n"));
        return text.append("重名时请使用企业微信提及或成员ID。").toString();
    }

    private String detail(State state) {
        requireCustomer(state);
        var customer=customers.get(state.customerId);
        return "客户："+customer.fullName()+"\nID："+customer.id()+"\n昵称："+or(customer.nickname(),"未填")
                +"\n学校："+or(customer.school(),"未填")+"\n专业/年级："+or(customer.major(),"未填")+" / "+or(customer.grade(),"未填")
                +"\n负责人："+or(customer.ownerDisplayName(),"未知")+"\n手机号："+or(customer.phone(),"未填")
                +"\n微信："+or(customer.wechatId(),"未填")+"\nQQ："+or(customer.qqId(),"未填")
                +"\n状态："+or(customer.currentStatus(),"未填")+"\n意向："+or(customer.intentionLevel(),"未填")
                +"\n需求摘要："+or(customer.demandSummary(),"未填")
                +"\n最近跟进："+(customer.lastContactAt()==null?"暂无":customer.lastContactAt())
                +"\n下次跟进："+(customer.nextFollowUpAt()==null?"未设置":customer.nextFollowUpAt())
                +"\n可见范围："+customer.visibility()+"\n归档："+(customer.archivedAt()==null?"否":"是");
    }

    private UUID member(UUID org,String requested) {
        String value = requested.trim().replaceAll("^<@|>$","");
        if (value.startsWith("@")) value=value.substring(1);
        if (List.of("我","本人","me").contains(value)) value=actors.require().membershipId().toString();
        final String token=value;
        List<Member> matches=members(org).stream().filter(member->member.id.toString().equals(token)
                ||member.wecomUserId.equals(token)||member.displayName.equals(token)).toList();
        if (matches.size()!=1) throw ApiException.badRequest("WECOM_MEMBER_AMBIGUOUS", "成员未绑定、已停用、无维护权限或重名；请 /members 查询后使用精确提及/ID");
        return matches.getFirst().id;
    }

    private List<Member> members(UUID org) {
        return jdbc.query("""
                select distinct membership.id,account.display_name,binding.wecom_userid
                from memberships membership join user_accounts account on account.id=membership.user_id
                join wecom_ai_bot_identity_bindings binding on binding.membership_id=membership.id
                    and binding.organization_id=membership.organization_id and binding.active=true
                where membership.organization_id=? and membership.status='ACTIVE' and account.enabled=true
                  and exists(select 1 from membership_roles mr join roles role on role.id=mr.role_id
                    where mr.membership_id=membership.id and role.code in('ORG_ADMIN','TEAM_MANAGER','MEMBER'))
                order by account.display_name,membership.id
                """,(rs,row)->new Member(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("wecom_userid")),org);
    }

    private String name(UUID member) {
        return jdbc.queryForObject("select account.display_name from memberships membership join user_accounts account on account.id=membership.user_id where membership.id=? and membership.organization_id=?",
                String.class,member,actors.require().organizationId());
    }

    private CustomerDtos.Response editable(UUID id) {
        var customer=customers.requireVisibleEntity(id,actors.require());
        customers.requireProfileEditable(customer,actors.require());
        if(customer.getArchivedAt()!=null) throw ApiException.conflict("ARCHIVED_CUSTOMER_DEAL_FORBIDDEN","归档客户不能进行成单或转接");
        return customers.get(id);
    }

    private String mine(CurrentActor actor,String requestedMonth) {
        LocalDate date=month(requestedMonth);
        Instant from=date.atStartOfDay(ZONE).toInstant(), to=date.plusMonths(1).atStartOfDay(ZONE).toInstant();
        return jdbc.query("""
                select count(*) deal_count,coalesce(sum(request.order_amount_cents),0) order_total,
                coalesce(sum(participant.bonus_cents),0) bonus_total
                from customer_deal_participants participant join customer_deal_requests request on request.id=participant.request_id
                where participant.organization_id=? and participant.membership_id=? and participant.participant_role='PRIMARY'
                    and request.status='APPROVED' and request.confirmed_at>=? and request.confirmed_at<?
                """,(rs,row)->"我的"+YearMonth.from(date)+"成单\n主成单："+rs.getLong("deal_count")+"单\n成交额：¥"
                        +money(rs.getLong("order_total"))+"\n主成单奖金：¥"+money(rs.getLong("bonus_total")),
                actor.organizationId(),actor.membershipId(),Timestamp.from(from),Timestamp.from(to)).getFirst();
    }

    private LocalDate month(String value) {
        try { return value==null||value.isBlank()?LocalDate.now(ZONE).withDayOfMonth(1):YearMonth.parse(value).atDay(1); }
        catch(java.time.format.DateTimeParseException exception){throw ApiException.badRequest("DEAL_MONTH_INVALID","月份格式应为 YYYY-MM");}
    }

    private static void requireCustomer(State state) {
        if(state.customerId==null) throw ApiException.badRequest("WECOM_CUSTOMER_REQUIRED","请先搜索并选择客户，或 /new 客户姓名");
    }
    private static void ensureCanRevise(State state) {
        if(state.confirmedRequestId!=null) throw ApiException.conflict("DEAL_ALREADY_CONFIRMED","已确认成单只能由管理员冲正");
        if(state.firstCompleteAt!=null&&state.revisions>=3) throw ApiException.conflict("DEAL_REVISION_LIMIT","已达到三次修订，请确认或取消");
    }
    private static void invalidatePreview(State state){state.previewVersion=-1;state.previewExpiresAt=null;state.previewToken=null;}
    private static String required(String value,String message){if(value==null||value.isBlank())throw ApiException.badRequest("WECOM_ARGUMENT_REQUIRED",message);return value.trim();}
    private static String or(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String money(long cents){return BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString();}
    private State copy(State state){return read(write(state));}
    private State read(String payload){try{return json.readValue(payload,State.class);}catch(Exception exception){throw new IllegalStateException("Unable to read encrypted deal state",exception);}}
    private String write(State state){try{return json.writeValueAsString(state);}catch(Exception exception){throw new IllegalStateException("Unable to serialize deal state",exception);}}
    private record Stored(UUID id,State state){}
    private record Member(UUID id,String displayName,String wecomUserId){}

    public static class State {
        public UUID customerId;
        public String query;
        public int page;
        public List<UUID> searchIds=new ArrayList<>();
        public UUID primaryId;
        public List<UUID> assistantIds=new ArrayList<>();
        public long orderCents;
        public long bonusCents;
        public Map<UUID,Long> allocations=new LinkedHashMap<>();
        public long version;
        public int revisions;
        public String firstCompleteAt;
        public long previewVersion=-1;
        public String previewExpiresAt;
        public String previewToken;
        public long previewCustomerVersion;
        public UUID pendingOwnerId;
        public long ownerCustomerVersion;
        public String pendingNewName;
        public UUID pendingNewOwner;
        public UUID confirmedRequestId;
    }
}
