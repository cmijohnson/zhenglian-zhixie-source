package com.yingge.cloud.customer.application;

import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.api.CustomerDtos;
import com.yingge.cloud.customer.domain.CustomerOptions;
import com.yingge.cloud.customer.domain.CustomerQuality;
import com.yingge.cloud.customer.domain.CustomerVisibility;
import com.yingge.cloud.customer.domain.LeadReviewStatus;
import com.yingge.cloud.customer.infrastructure.CustomerAccessGrantRepository;
import com.yingge.cloud.customer.infrastructure.CustomerEntity;
import com.yingge.cloud.customer.infrastructure.CustomerRepository;
import com.yingge.cloud.customer.infrastructure.CustomerSpecifications;
import com.yingge.cloud.followup.application.CustomerFollowUpService;
import com.yingge.cloud.permission.application.PermissionService;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.reminder.application.ReminderPort;
import com.yingge.cloud.shared.api.ApiException;
import com.yingge.cloud.shared.api.PageResponse;
import com.yingge.cloud.shared.domain.TextNormalizer;
import com.yingge.cloud.tag.infrastructure.DictionaryItemRepository;
import com.yingge.cloud.tag.infrastructure.TagEntity;
import com.yingge.cloud.tag.infrastructure.TagRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final CustomerAccessGrantRepository grants;
    private final TagRepository tags;
    private final DictionaryItemRepository dictionaries;
    private final CurrentActorProvider actors;
    private final PermissionService permissions;
    private final ReminderPort reminders;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerFollowUpService followUps;

    public CustomerService(CustomerRepository customers, CustomerAccessGrantRepository grants, TagRepository tags,
                           DictionaryItemRepository dictionaries, CurrentActorProvider actors,
                           PermissionService permissions, ReminderPort reminders, AuditService audit,
                           EntityManager entityManager, NamedParameterJdbcTemplate jdbc,
                           CustomerFollowUpService followUps) {
        this.customers = customers;
        this.grants = grants;
        this.tags = tags;
        this.dictionaries = dictionaries;
        this.actors = actors;
        this.permissions = permissions;
        this.reminders = reminders;
        this.audit = audit;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.followUps = followUps;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDtos.Response> search(String search, String status, String intention,
                                                       String verification, String grade, String school,
                                                       Collection<UUID> tagIds, boolean includeArchived,
                                                       UUID teamId, UUID membershipId, UUID ownerMembershipId,
                                                       int page, int size, String sort) {
        CurrentActor actor = actors.require();
        Sort order = allowedSort(sort);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), order);
        var spec = CustomerSpecifications.visibleTo(actor)
                .and(CustomerSpecifications.matches(search, status, intention, verification, grade, school,
                        tagIds, includeArchived, teamId, membershipId, ownerMembershipId));
        return PageResponse.from(customers.findAll(spec, pageable).map(CustomerDtos.Response::from));
    }

    @Transactional(readOnly = true)
    public List<CustomerEntity> visibleEntities(CurrentActor actor, boolean includeArchived) {
        return customers.findAll(CustomerSpecifications.visibleTo(actor)
                .and(CustomerSpecifications.matches(null, null, null, null, List.of(), includeArchived)),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    @Transactional(readOnly = true)
    public CustomerEntity requireVisibleEntity(UUID id, CurrentActor actor) {
        return customers.findOne(CustomerSpecifications.visibleTo(actor).and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "未找到客户或无权访问"));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.Response get(UUID id) {
        return CustomerDtos.Response.from(requireVisibleEntity(id, actors.require()));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.CollaborationOverview collaboration(UUID id) {
        CurrentActor actor = actors.require();
        CustomerEntity customer = requireVisibleEntity(id, actor);
        return collaborationOverview(customer, actor);
    }

    @Transactional
    public CustomerDtos.CollaborationOverview replaceCollaborators(
            UUID id, CustomerDtos.UpdateCollaboratorsRequest body) {
        CurrentActor actor = actors.require();
        permissions.requireCustomerWrite(actor);
        CustomerEntity customer = customers.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "客户不存在"));
        requireCollaborationManager(customer, actor);
        Set<UUID> requested = body.membershipIds() == null ? Set.of() : new LinkedHashSet<>(body.membershipIds());
        requested.remove(customer.getOwnerMembershipId());
        if (requested.size() > 20) {
            throw ApiException.badRequest("TOO_MANY_COLLABORATORS", "每位客户最多共享给 20 位协作者");
        }
        if (!requested.isEmpty()) {
            List<UUID> eligible = jdbc.queryForList("""
                    select distinct membership.id
                    from memberships membership
                    join user_accounts account on account.id=membership.user_id and account.enabled=true
                    join membership_roles membership_role on membership_role.membership_id=membership.id
                    join roles role on role.id=membership_role.role_id
                    where membership.organization_id=:organizationId
                      and membership.status='ACTIVE'
                      and membership.id in (:membershipIds)
                      and role.code in ('ORG_ADMIN','TEAM_MANAGER','MEMBER')
                    """, new MapSqlParameterSource()
                    .addValue("organizationId", actor.organizationId())
                    .addValue("membershipIds", requested), UUID.class);
            if (eligible.size() != requested.size()) {
                throw ApiException.badRequest("COLLABORATOR_NOT_ELIGIBLE", "协作者必须是当前组织内已启用且可写客户的成员");
            }
        }
        jdbc.update("""
                delete from customer_access_grants
                where organization_id=:organizationId and customer_id=:customerId
                  and grantee_membership_id is not null
                """, new MapSqlParameterSource()
                .addValue("organizationId", actor.organizationId())
                .addValue("customerId", customer.getId()));
        for (UUID membershipId : requested) {
            jdbc.update("""
                    insert into customer_access_grants
                      (id, organization_id, customer_id, grantee_membership_id, can_edit, granted_by_membership_id)
                    values (:id, :organizationId, :customerId, :membershipId, true, :grantedBy)
                    on conflict do nothing
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("organizationId", actor.organizationId())
                    .addValue("customerId", customer.getId())
                    .addValue("membershipId", membershipId)
                    .addValue("grantedBy", actor.membershipId()));
        }
        audit.record(actor, "CUSTOMER_COLLABORATORS_UPDATED", "CUSTOMER", customer.getId().toString(), "SUCCESS",
                java.util.Map.of("collaboratorCount", requested.size()));
        followUps.reconcileCustomer(actor.organizationId(), customer.getId());
        return collaborationOverview(customer, actor);
    }

    @Transactional
    public CustomerDtos.Response transferOwnership(
            UUID id, CustomerDtos.TransferOwnershipRequest body) {
        CurrentActor actor = actors.require();
        permissions.requireCustomerWrite(actor);
        CustomerEntity customer = requireVisibleEntity(id, actor);
        if (!canTransferOwnership(customer, actor)) {
            throw ApiException.forbidden("只有主负责人、负责该团队的团队长或组织管理员可以转接客户");
        }
        if (customer.getArchivedAt() != null) {
            throw ApiException.conflict("ARCHIVED_CUSTOMER_TRANSFER_FORBIDDEN", "归档客户不能转接，请先恢复客户");
        }
        if (body.expectedVersion() != customer.getVersion()) {
            throw ApiException.conflict("STALE_VERSION", "客户已被其他成员更新，请刷新后重试");
        }
        UUID previousOwnerId = customer.getOwnerMembershipId();
        if (previousOwnerId.equals(body.targetMembershipId())) {
            throw ApiException.badRequest("INVALID_TRANSFER_TARGET", "接收人不能是当前主负责人");
        }
        TransferTarget target = requireTransferTarget(customer, actor, body.targetMembershipId());
        if (customer.getVisibility() == CustomerVisibility.TEAM && target.teamId() == null) {
            throw ApiException.badRequest("TRANSFER_TARGET_TEAM_REQUIRED", "团队可见客户只能转接给已加入有效团队的成员");
        }

        UUID previousTeamId = customer.getTeamId();
        customer.transferOwnership(target.membershipId(), target.teamId());
        jdbc.update("""
                delete from customer_access_grants
                where organization_id=:organizationId and customer_id=:customerId
                  and grantee_membership_id=:targetMembershipId
                """, new MapSqlParameterSource()
                .addValue("organizationId", actor.organizationId())
                .addValue("customerId", customer.getId())
                .addValue("targetMembershipId", target.membershipId()));
        int transferredReminders = reminders.transferPending(
                customer.getId(), previousOwnerId, target.membershipId(), actor);
        customers.saveAndFlush(customer);
        entityManager.refresh(customer);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousOwnerMembershipId", previousOwnerId.toString());
        details.put("targetOwnerMembershipId", target.membershipId().toString());
        details.put("previousTeamId", previousTeamId == null ? "" : previousTeamId.toString());
        details.put("targetTeamId", target.teamId() == null ? "" : target.teamId().toString());
        details.put("transferredPendingReminderCount", transferredReminders);
        String reason = TextNormalizer.optional(body.reason());
        if (reason != null) details.put("reason", reason);
        audit.record(actor, "CUSTOMER_OWNERSHIP_TRANSFERRED", "CUSTOMER",
                customer.getId().toString(), "SUCCESS", details);
        return CustomerDtos.Response.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerDtos.DuplicateResponse duplicateCheck(CustomerDtos.DuplicateRequest body) {
        var actor = actors.require();
        return duplicateCheck(body, actor);
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.UpsertRequest body) {
        var actor = actors.require();
        permissions.requireCustomerWrite(actor);
        var duplicate = duplicateCheck(new CustomerDtos.DuplicateRequest(
                body.fullName(), body.major(), body.phone(), body.wechatId(), body.qqId(), null), actor);
        if (duplicate.blocking()) {
            throw ApiException.conflict("EXACT_DUPLICATE", "手机号、微信号或 QQ 已存在于其他客户档案");
        }
        if (duplicate.requiresConfirmation() && TextNormalizer.optional(body.duplicateOverrideReason()) == null) {
            throw ApiException.conflict("POSSIBLE_DUPLICATE", "发现姓名与专业一致的客户，请确认并填写继续创建原因");
        }

        UUID owner = body.ownerMembershipId() == null ? actor.membershipId() : body.ownerMembershipId();
        if (!owner.equals(actor.membershipId())) permissions.requireBulkTransfer(actor);
        var customer = new CustomerEntity(actor.organizationId(), owner, actor.membershipId());
        customer.apply(values(body, actor, owner, body.teamId(), body.visibility(), true));
        var saved = customers.saveAndFlush(customer);
        entityManager.refresh(saved);
        reminders.replacePending(saved.getId(), null, owner, saved.getNextFollowUpAt(), "跟进 " + saved.getFullName(), actor);
        audit.record(actor, "CUSTOMER_CREATED", "CUSTOMER", saved.getId().toString(), "SUCCESS",
                duplicate.requiresConfirmation()
                        ? java.util.Map.of("duplicateOverrideReason", body.duplicateOverrideReason())
                        : null);
        followUps.reconcileCustomer(actor.organizationId(), saved.getId());
        return CustomerDtos.Response.from(saved);
    }

    @Transactional
    public CustomerDtos.Response update(UUID id, CustomerDtos.UpsertRequest body) {
        var actor = actors.require();
        permissions.requireCustomerWrite(actor);
        var customer = requireVisibleEntity(id, actor);
        requireProfileEditable(customer, actor);
        if (body.expectedVersion() != null && body.expectedVersion() != customer.getVersion()) {
            throw ApiException.conflict("STALE_VERSION", "数据已被其他成员更新，请刷新后重试");
        }
        var duplicate = duplicateCheck(new CustomerDtos.DuplicateRequest(
                body.fullName(), body.major(), body.phone(), body.wechatId(), body.qqId(), id), actor);
        if (duplicate.blocking()) {
            throw ApiException.conflict("EXACT_DUPLICATE", "手机号、微信号或 QQ 已存在于其他客户档案");
        }
        if (duplicate.requiresConfirmation() && TextNormalizer.optional(body.duplicateOverrideReason()) == null) {
            throw ApiException.conflict("POSSIBLE_DUPLICATE", "发现姓名与专业一致的客户，请确认并填写继续修改原因");
        }

        UUID owner = body.ownerMembershipId() == null ? customer.getOwnerMembershipId() : body.ownerMembershipId();
        if (!owner.equals(customer.getOwnerMembershipId())) permissions.requireBulkTransfer(actor);
        boolean canManageAssignment = canManageCustomer(customer, actor);
        UUID teamId = canManageAssignment ? body.teamId() : customer.getTeamId();
        CustomerVisibility visibility = canManageAssignment ? body.visibility() : customer.getVisibility();
        customer.apply(values(body, actor, owner, teamId, visibility, canManageAssignment));
        customers.saveAndFlush(customer);
        entityManager.refresh(customer);
        reminders.replacePending(customer.getId(), null, owner, customer.getNextFollowUpAt(), "跟进 " + customer.getFullName(), actor);
        audit.record(actor, "CUSTOMER_UPDATED", "CUSTOMER", customer.getId().toString(), "SUCCESS",
                duplicate.requiresConfirmation()
                        ? java.util.Map.of("duplicateOverrideReason", body.duplicateOverrideReason())
                        : null);
        followUps.reconcileCustomer(actor.organizationId(), customer.getId());
        return CustomerDtos.Response.from(customer);
    }

    @Transactional
    public void archive(UUID id) {
        var actor = actors.require();
        permissions.requireCustomerWrite(actor);
        var customer = requireVisibleEntity(id, actor);
        requireEditable(customer, actor);
        customer.archive();
        reminders.cancelActive(id, actor);
        followUps.reconcileCustomer(actor.organizationId(), id);
        audit.record(actor, "CUSTOMER_ARCHIVED", "CUSTOMER", id.toString(), "SUCCESS", null);
    }

    @Transactional
    public CustomerDtos.Response reviewLead(UUID id, CustomerDtos.LeadReviewRequest body) {
        CurrentActor actor = actors.require();
        if (!actor.canReviewTeamOperations()) {
            throw ApiException.forbidden("只有管理员或团队长可以审核有效线索");
        }
        if (body.status() == LeadReviewStatus.PENDING) {
            throw ApiException.badRequest("INVALID_LEAD_REVIEW_STATUS", "审核结果只能是有效或无效");
        }
        String reason = TextNormalizer.optional(body.reason());
        if (body.status() == LeadReviewStatus.INVALID && reason == null) {
            throw ApiException.badRequest("LEAD_REVIEW_REASON_REQUIRED", "判定无效时必须填写原因");
        }
        CustomerEntity customer = customers.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "客户不存在"));
        requireLeadReviewScope(customer, actor);
        customer.reviewLead(body.status(), reason, actor.membershipId());
        customers.saveAndFlush(customer);
        entityManager.refresh(customer);
        audit.record(actor, "CUSTOMER_LEAD_REVIEWED", "CUSTOMER", id.toString(), "SUCCESS",
                java.util.Map.of("status", body.status().name()));
        return CustomerDtos.Response.from(customer);
    }

    private void requireLeadReviewScope(CustomerEntity customer, CurrentActor actor) {
        if (actor.isAdmin()) return;
        if (!actor.isTeamManager() || !isCustomerInManagedTeam(customer, actor)) {
            throw ApiException.forbidden("不能审核该客户");
        }
    }

    public void requireEditable(CustomerEntity customer, CurrentActor actor) {
        if (canManageCustomer(customer, actor)) return;
        throw ApiException.forbidden("当前客户仅可查看，不能修改画像");
    }

    public void requireProfileEditable(CustomerEntity customer, CurrentActor actor) {
        if (canEditProfile(customer, actor)) return;
        throw ApiException.forbidden("当前客户仅可查看，不能修改画像");
    }

    public void requireTimelineEditable(CustomerEntity customer, CurrentActor actor) {
        if (canEditProfile(customer, actor)) return;
        throw ApiException.forbidden("当前客户仅可查看，不能记录沟通");
    }

    private boolean canEditProfile(CustomerEntity customer, CurrentActor actor) {
        if (canManageCustomer(customer, actor)) return true;
        return hasEditGrant(customer, actor);
    }

    private boolean canManageCustomer(CustomerEntity customer, CurrentActor actor) {
        if (actor.isAdmin() || customer.getOwnerMembershipId().equals(actor.membershipId())) return true;
        return actor.isTeamManager() && customer.getTeamId() != null
                && actor.managedTeamIds().contains(customer.getTeamId());
    }

    private boolean hasEditGrant(CustomerEntity customer, CurrentActor actor) {
        Set<UUID> teamIds = actor.teamIds().isEmpty()
                ? Set.of(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                : actor.teamIds();
        return grants.hasEditGrant(customer.getId(), actor.membershipId(), teamIds);
    }

    private void requireCollaborationManager(CustomerEntity customer, CurrentActor actor) {
        if (actor.isAdmin() || customer.getOwnerMembershipId().equals(actor.membershipId())) return;
        throw ApiException.forbidden("只有主负责人或组织管理员可以管理客户协作者");
    }

    private CustomerDtos.CollaborationOverview collaborationOverview(CustomerEntity customer, CurrentActor actor) {
        boolean canManage = actor.isAdmin() || customer.getOwnerMembershipId().equals(actor.membershipId());
        boolean canTransferOwnership = actor.canWriteCustomers() && canTransferOwnership(customer, actor)
                && customer.getArchivedAt() == null;
        boolean canEditProfile = actor.canWriteCustomers() && canEditProfile(customer, actor);
        boolean canArchive = actor.canWriteCustomers() && canManageCustomer(customer, actor);
        boolean canEditTimeline = canEditProfile;
        List<CustomerDtos.CollaborationMember> collaborators = collaborationMembers(customer.getId(), false,
                customer.getOwnerMembershipId(), actor.organizationId());
        List<CustomerDtos.CollaborationMember> candidates = canManage
                ? collaborationMembers(customer.getId(), true, customer.getOwnerMembershipId(), actor.organizationId())
                : List.of();
        List<CustomerDtos.CollaborationMember> transferCandidates = canTransferOwnership
                ? transferCandidates(customer, actor)
                : List.of();
        return new CustomerDtos.CollaborationOverview(customer.getOwnerMembershipId(), canManage,
                canTransferOwnership, canEditProfile, canArchive, canEditTimeline, collaborators, candidates,
                transferCandidates);
    }

    private List<CustomerDtos.CollaborationMember> collaborationMembers(
            UUID customerId, boolean candidates, UUID ownerMembershipId, UUID organizationId) {
        String membershipScope = candidates
                ? """
                  and membership.id <> :ownerMembershipId
                  and exists (
                    select 1 from membership_roles membership_role
                    join roles role on role.id=membership_role.role_id
                    where membership_role.membership_id=membership.id
                      and role.code in ('ORG_ADMIN','TEAM_MANAGER','MEMBER')
                  )
                  """
                : """
                  and exists (
                    select 1 from customer_access_grants grant_row
                    where grant_row.organization_id=membership.organization_id
                      and grant_row.customer_id=:customerId
                      and grant_row.grantee_membership_id=membership.id
                      and grant_row.can_edit=true
                  )
                  """;
        return jdbc.query("""
                select membership.id, account.display_name,
                       coalesce(string_agg(distinct team.name, '、' order by team.name), '') team_names
                from memberships membership
                join user_accounts account on account.id=membership.user_id and account.enabled=true
                left join team_members team_member on team_member.membership_id=membership.id
                left join teams team on team.id=team_member.team_id and team.active=true
                where membership.organization_id=:organizationId and membership.status='ACTIVE'
                """ + membershipScope + """
                group by membership.id, account.display_name
                order by account.display_name, membership.id
                """, new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("ownerMembershipId", ownerMembershipId)
                .addValue("customerId", customerId),
                (row, index) -> new CustomerDtos.CollaborationMember(
                        row.getObject("id", UUID.class), row.getString("display_name"), row.getString("team_names")));
    }

    private boolean canTransferOwnership(CustomerEntity customer, CurrentActor actor) {
        if (actor.isAdmin() || customer.getOwnerMembershipId().equals(actor.membershipId())) return true;
        return actor.isTeamManager() && isCustomerInManagedTeam(customer, actor);
    }

    private boolean isCustomerInManagedTeam(CustomerEntity customer, CurrentActor actor) {
        if (actor.managedTeamIds().isEmpty()) return false;
        if (customer.getTeamId() != null && actor.managedTeamIds().contains(customer.getTeamId())) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                  select 1 from team_members team_member
                  join teams team on team.id=team_member.team_id and team.active=true
                  where team_member.membership_id=:membershipId
                    and team_member.team_id in (:teamIds)
                )
                """, new MapSqlParameterSource()
                .addValue("membershipId", customer.getOwnerMembershipId())
                .addValue("teamIds", actor.managedTeamIds()), Boolean.class));
    }

    private List<CustomerDtos.CollaborationMember> transferCandidates(
            CustomerEntity customer, CurrentActor actor) {
        Set<UUID> allowedTeamIds = transferTeamScope(actor);
        if (!actor.isAdmin() && allowedTeamIds.isEmpty()) return List.of();
        String teamScope = actor.isAdmin() ? "" : """
                  and exists (
                    select 1 from team_members scoped_team
                    join teams active_team on active_team.id=scoped_team.team_id and active_team.active=true
                    where scoped_team.membership_id=membership.id
                      and scoped_team.team_id in (:allowedTeamIds)
                  )
                """;
        String teamRequired = customer.getVisibility() == CustomerVisibility.TEAM ? """
                  and exists (
                    select 1 from team_members required_team
                    join teams active_required_team on active_required_team.id=required_team.team_id
                      and active_required_team.active=true
                    where required_team.membership_id=membership.id
                  )
                """ : "";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizationId", actor.organizationId())
                .addValue("ownerMembershipId", customer.getOwnerMembershipId());
        if (!actor.isAdmin()) parameters.addValue("allowedTeamIds", allowedTeamIds);
        return jdbc.query("""
                select membership.id, account.display_name,
                       coalesce(string_agg(distinct team.name, '、' order by team.name), '') team_names
                from memberships membership
                join user_accounts account on account.id=membership.user_id and account.enabled=true
                left join team_members team_member on team_member.membership_id=membership.id
                left join teams team on team.id=team_member.team_id and team.active=true
                where membership.organization_id=:organizationId
                  and membership.status='ACTIVE'
                  and membership.id<>:ownerMembershipId
                  and exists (
                    select 1 from membership_roles membership_role
                    join roles role on role.id=membership_role.role_id
                    where membership_role.membership_id=membership.id
                      and role.code in ('ORG_ADMIN','TEAM_MANAGER','MEMBER')
                  )
                """ + teamScope + teamRequired + """
                group by membership.id, account.display_name
                order by account.display_name, membership.id
                """, parameters, (row, index) -> new CustomerDtos.CollaborationMember(
                row.getObject("id", UUID.class), row.getString("display_name"), row.getString("team_names")));
    }

    private TransferTarget requireTransferTarget(
            CustomerEntity customer, CurrentActor actor, UUID targetMembershipId) {
        Set<UUID> allowedTeamIds = transferTeamScope(actor);
        if (!actor.isAdmin() && allowedTeamIds.isEmpty()) {
            throw ApiException.forbidden("当前账号没有可转接客户的有效团队");
        }
        String targetScope = actor.isAdmin() ? "" : """
                  and exists (
                    select 1 from team_members scoped_team
                    join teams scoped_active_team on scoped_active_team.id=scoped_team.team_id
                      and scoped_active_team.active=true
                    where scoped_team.membership_id=membership.id
                      and scoped_team.team_id in (:allowedTeamIds)
                  )
                """;
        String selectedTeamScope = actor.isAdmin() ? "" : " and candidate_team.team_id in (:allowedTeamIds) ";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizationId", actor.organizationId())
                .addValue("targetMembershipId", targetMembershipId)
                .addValue("preferredTeamId", customer.getTeamId());
        if (!actor.isAdmin()) parameters.addValue("allowedTeamIds", allowedTeamIds);
        List<TransferTarget> targets = jdbc.query("""
                select membership.id, account.display_name,
                       (
                         select candidate_team.team_id
                         from team_members candidate_team
                         join teams active_candidate_team on active_candidate_team.id=candidate_team.team_id
                           and active_candidate_team.active=true
                         where candidate_team.membership_id=membership.id
                """ + selectedTeamScope + """
                         order by case when candidate_team.team_id=:preferredTeamId then 0 else 1 end,
                                  candidate_team.created_at, candidate_team.team_id
                         limit 1
                       ) target_team_id
                from memberships membership
                join user_accounts account on account.id=membership.user_id and account.enabled=true
                where membership.organization_id=:organizationId
                  and membership.id=:targetMembershipId
                  and membership.status='ACTIVE'
                  and exists (
                    select 1 from membership_roles membership_role
                    join roles role on role.id=membership_role.role_id
                    where membership_role.membership_id=membership.id
                      and role.code in ('ORG_ADMIN','TEAM_MANAGER','MEMBER')
                  )
                """ + targetScope, parameters, (row, index) -> new TransferTarget(
                row.getObject("id", UUID.class), row.getString("display_name"),
                row.getObject("target_team_id", UUID.class)));
        if (targets.isEmpty()) {
            throw ApiException.badRequest("TRANSFER_TARGET_NOT_ELIGIBLE",
                    "接收人必须是当前允许范围内已启用且可维护客户的成员");
        }
        return targets.getFirst();
    }

    private Set<UUID> transferTeamScope(CurrentActor actor) {
        if (actor.isAdmin()) return Set.of();
        if (actor.isTeamManager() && !actor.managedTeamIds().isEmpty()) return actor.managedTeamIds();
        return actor.teamIds();
    }

    private CustomerEntity.CustomerValues values(
            CustomerDtos.UpsertRequest body, CurrentActor actor, UUID owner, UUID teamId,
            CustomerVisibility requestedVisibility, boolean canManageAssignment) {
        String fullName = TextNormalizer.required(body.fullName());
        String phone = TextNormalizer.optional(body.phone());
        String wechat = TextNormalizer.optional(body.wechatId());
        String qq = TextNormalizer.optional(body.qqId());
        if (phone == null && wechat == null && qq == null) {
            throw ApiException.badRequest("CONTACT_REQUIRED", "手机号、微信号、QQ号至少填写一个");
        }
        String identity = defaulted(body.identityType(), "在校生");
        String intention = defaulted(body.intentionLevel(), "中");
        String urgency = defaulted(body.urgencyLevel(), "中");
        String verification = defaulted(body.verificationStatus(), "未核实");
        String status = defaulted(body.currentStatus(), "初次接触");
        CustomerOptions.validate(identity, intention, urgency, verification, status);
        if (body.sourceDictionaryId() != null) {
            dictionaries.findByIdAndOrganizationId(body.sourceDictionaryId(), actor.organizationId())
                    .orElseThrow(() -> ApiException.badRequest("SOURCE_NOT_FOUND", "来源字典不存在"));
        }
        Set<UUID> requestedTagIds = body.tagIds() == null ? Set.of() : body.tagIds();
        Set<TagEntity> tagSet = new LinkedHashSet<>(tags.findByOrganizationIdAndActiveTrueAndIdIn(actor.organizationId(), requestedTagIds));
        if (tagSet.size() != requestedTagIds.size()) {
            throw ApiException.badRequest("TAG_NOT_FOUND", "包含不存在或不属于当前组织的标签");
        }
        validateCustomerQuality(tagSet);
        Set<String> directions = new LinkedHashSet<>();
        if (body.focusDirections() != null) {
            body.focusDirections().stream().map(TextNormalizer::optional).filter(java.util.Objects::nonNull).forEach(directions::add);
        }
        if (directions.isEmpty()) directions.add("暂不明确");
        CustomerVisibility visibility = requestedVisibility == null ? CustomerVisibility.PRIVATE : requestedVisibility;
        if (visibility == CustomerVisibility.TEAM && teamId == null) {
            throw ApiException.badRequest("TEAM_REQUIRED", "团队可见客户必须指定团队");
        }
        if (canManageAssignment && teamId != null && !actor.isAdmin() && !actor.teamIds().contains(teamId)) {
            throw ApiException.forbidden("不能把客户分配到当前成员不属于的团队");
        }
        validateFriendFunnel(body.friendAddedAt(), body.friendTrustedAt(), body.friendQualifiedAt());
        return new CustomerEntity.CustomerValues(
                owner, teamId, visibility, fullName, TextNormalizer.optional(body.nickname()), phone,
                TextNormalizer.contact(phone), wechat, TextNormalizer.contact(wechat), qq, TextNormalizer.contact(qq),
                TextNormalizer.optional(body.school()), TextNormalizer.optional(body.major()), CustomerOptions.normalizeGrade(body.grade()),
                TextNormalizer.optional(body.city()), TextNormalizer.optional(body.highSchool()),
                TextNormalizer.optional(body.sourceLocation()), identity, directions, TextNormalizer.optional(body.demandSummary()),
                intention, urgency, verification, body.sourceDictionaryId(), TextNormalizer.optional(body.sourceChannel()), status,
                TextNormalizer.optional(body.ownerNotes()), body.nextFollowUpAt(),
                body.friendAddedAt(), body.friendTrustedAt(), body.friendQualifiedAt(), tagSet
        );
    }

    private void validateFriendFunnel(Instant addedAt, Instant trustedAt, Instant qualifiedAt) {
        if (trustedAt != null && addedAt == null) {
            throw ApiException.badRequest("FRIEND_ADDED_REQUIRED", "建立信任前必须先记录添加好友时间");
        }
        if (qualifiedAt != null && trustedAt == null) {
            throw ApiException.badRequest("FRIEND_TRUSTED_REQUIRED", "筛选确认前必须先记录建立信任时间");
        }
        if (trustedAt != null && trustedAt.isBefore(addedAt)) {
            throw ApiException.badRequest("FRIEND_STAGE_TIME_INVALID", "建立信任时间不能早于添加好友时间");
        }
        if (qualifiedAt != null && qualifiedAt.isBefore(trustedAt)) {
            throw ApiException.badRequest("FRIEND_STAGE_TIME_INVALID", "筛选确认时间不能早于建立信任时间");
        }
    }

    private void validateCustomerQuality(Collection<TagEntity> selectedTags) {
        if (CustomerQuality.hasMultipleQualityTags(
                selectedTags.stream().map(TagEntity::getCategory).toList())) {
            throw ApiException.badRequest("CUSTOMER_QUALITY_CONFLICT",
                    "客户质量只能选择 A 类、B 类、C 类中的一项");
        }
    }

    private CustomerDtos.DuplicateResponse duplicateCheck(CustomerDtos.DuplicateRequest body, CurrentActor actor) {
        String phone = TextNormalizer.contact(body.phone());
        String wechat = TextNormalizer.contact(body.wechatId());
        String qq = TextNormalizer.contact(body.qqId());
        String fullName = TextNormalizer.matchKey(body.fullName());
        String major = TextNormalizer.matchKey(body.major());
        List<CustomerEntity> exact = body.excludeCustomerId() == null
                ? customers.findExactDuplicates(actor.organizationId(), phone, wechat, qq)
                : customers.findExactDuplicates(actor.organizationId(), body.excludeCustomerId(), phone, wechat, qq);
        List<UUID> sameNameAndMajorIds = fullName == null || major == null
                ? List.of()
                : body.excludeCustomerId() == null
                    ? customers.findNameMajorDuplicateIds(actor.organizationId(), fullName, major)
                    : customers.findNameMajorDuplicateIds(
                            actor.organizationId(), body.excludeCustomerId(), fullName, major);
        List<CustomerEntity> sameNameAndMajor = sameNameAndMajorIds.isEmpty()
                ? List.of()
                : customers.findAllById(sameNameAndMajorIds);
        Set<UUID> candidateIds = new HashSet<>();
        exact.forEach(candidate -> candidateIds.add(candidate.getId()));
        candidateIds.addAll(sameNameAndMajorIds);
        Set<UUID> visibleIds = candidateIds.isEmpty()
                ? Set.of()
                : new HashSet<>(customers.findAll(CustomerSpecifications.visibleTo(actor)
                        .and((root, query, builder) -> root.get("id").in(candidateIds)))
                        .stream().map(CustomerEntity::getId).toList());
        Map<UUID, CustomerEntity> visibleCandidates = new LinkedHashMap<>();
        Map<UUID, LinkedHashSet<String>> reasonsByCustomer = new LinkedHashMap<>();
        Set<UUID> hiddenIds = new HashSet<>();
        for (CustomerEntity candidate : exact) {
            if (!visibleIds.contains(candidate.getId())) {
                hiddenIds.add(candidate.getId());
                continue;
            }
            visibleCandidates.put(candidate.getId(), candidate);
            var reasons = reasonsByCustomer.computeIfAbsent(candidate.getId(), ignored -> new LinkedHashSet<>());
            if (phone != null && phone.equals(TextNormalizer.contact(candidate.getPhone()))) reasons.add("手机号一致");
            if (wechat != null && wechat.equals(TextNormalizer.contact(candidate.getWechatId()))) reasons.add("微信号一致");
            if (qq != null && qq.equals(TextNormalizer.contact(candidate.getQqId()))) reasons.add("QQ号一致");
        }
        for (CustomerEntity candidate : sameNameAndMajor) {
            if (!visibleIds.contains(candidate.getId())) {
                hiddenIds.add(candidate.getId());
                continue;
            }
            visibleCandidates.put(candidate.getId(), candidate);
            reasonsByCustomer.computeIfAbsent(candidate.getId(), ignored -> new LinkedHashSet<>())
                    .add("姓名与专业一致");
        }
        List<CustomerDtos.DuplicateMatch> matches = visibleCandidates.entrySet().stream()
                .map(entry -> new CustomerDtos.DuplicateMatch(
                        entry.getKey(), entry.getValue().getFullName(),
                        List.copyOf(reasonsByCustomer.getOrDefault(entry.getKey(), new LinkedHashSet<>()))))
                .toList();
        return new CustomerDtos.DuplicateResponse(
                !exact.isEmpty(), !sameNameAndMajor.isEmpty(),
                hiddenIds.size(), matches);
    }

    private Sort allowedSort(String sort) {
        return switch (sort == null ? "updatedDesc" : sort) {
            case "createdDesc" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "lastContactDesc" -> Sort.by(Sort.Direction.DESC, "lastContactAt");
            case "nextFollowUpAsc" -> Sort.by(Sort.Direction.ASC, "nextFollowUpAt");
            default -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
    }

    private String defaulted(String value, String fallback) {
        String normalized = TextNormalizer.optional(value);
        return normalized == null ? fallback : normalized;
    }

    private record TransferTarget(UUID membershipId, String displayName, UUID teamId) {}
}
