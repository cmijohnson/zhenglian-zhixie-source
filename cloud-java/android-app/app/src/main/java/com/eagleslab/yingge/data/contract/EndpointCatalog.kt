package com.eagleslab.yingge.data.contract

/** Machine-scannable, allow-listed Android network surface. */
object EndpointCatalog {
    data class Endpoint(val method: String, val path: String)
    const val MOBILE_REGISTRATION_OPTIONS = "/api/v1/mobile/auth/registration-options"
    const val MOBILE_REGISTER = "/api/v1/mobile/auth/register"
    const val MOBILE_LOGIN = "/api/v1/mobile/auth/login"
    const val MOBILE_REFRESH = "/api/v1/mobile/auth/refresh"
    const val MOBILE_LOGOUT = "/api/v1/mobile/auth/logout"
    const val PASSWORD_CHANGE = "/api/v1/auth/password"
    const val MOBILE_DEVICES = "/api/v1/mobile/auth/devices"
    const val MOBILE_DEVICE = "/api/v1/mobile/auth/devices/{deviceId}"
    const val MOBILE_BOOTSTRAP = "/api/v1/mobile/bootstrap"
    const val PUSH_REGISTRATION = "/api/v1/mobile/devices/push-registration"
    const val NOTIFICATION_PREFERENCES = "/api/v1/mobile/devices/notification-preferences"
    const val WEB_SESSIONS = "/api/v1/mobile/web-sessions"
    const val WEB_SESSION_EXCHANGE = "/api/v1/mobile/web-sessions/exchange"
    const val CURRENT_RELEASE = "/api/v1/mobile/app-releases/current"
    const val RELEASE_APK = "/api/v1/mobile/app-releases/{releaseId}/apk"
    const val CUSTOMER = "/api/v1/customers/{customerId}"
    const val CUSTOMER_DEAL_REQUEST = "/api/v1/customers/{customerId}/deal-requests"

    val contractPaths = setOf(
        MOBILE_REGISTRATION_OPTIONS, MOBILE_REGISTER, MOBILE_LOGIN, MOBILE_REFRESH,
        MOBILE_LOGOUT, PASSWORD_CHANGE, MOBILE_DEVICES, MOBILE_DEVICE,
        MOBILE_BOOTSTRAP, PUSH_REGISTRATION, NOTIFICATION_PREFERENCES, WEB_SESSIONS,
        WEB_SESSION_EXCHANGE, CURRENT_RELEASE, RELEASE_APK, CUSTOMER,
        CUSTOMER_DEAL_REQUEST,
    )

    object EmployeeBusiness {
        const val CUSTOMERS = "/api/v1/customers"
        const val CUSTOMER_COLLABORATION = "/api/v1/customers/{customerId}/collaboration"
        const val CUSTOMER_TRANSFER = "/api/v1/customers/{customerId}/transfer"
        const val INTERACTIONS = "/api/v1/customers/{customerId}/interactions"
        const val REMINDERS = "/api/v1/reminders"
        const val REMINDER = "/api/v1/reminders/{reminderId}"
        const val DAILY_REPORT_TODAY = "/api/v1/daily-reports/today"
        const val DAILY_REPORT_DATE = "/api/v1/daily-reports/{date}"
        const val DASHBOARD = "/api/v1/dashboard"
        const val NOTIFICATIONS = "/api/v1/notifications"
        const val TASKS = "/api/v1/tasks"
        const val TASK_CLAIM = "/api/v1/tasks/{taskId}/claim"
        const val TASK_ASSIGNMENT = "/api/v1/tasks/{taskId}/my-assignment"
        const val PERFORMANCE_TODAY = "/api/v1/performance/today"
        const val PERFORMANCE_CLAIMS = "/api/v1/performance/claims"
        const val PERFORMANCE_CLAIM_EVIDENCE = "/api/v1/performance/claims/{claimId}/evidence"
        const val PERFORMANCE_CLAIM_SUBMIT = "/api/v1/performance/claims/{claimId}/submit"
        const val CONSULTATIONS = "/api/v1/consultations"
        const val CONSULTATION = "/api/v1/consultations/{consultationId}"
        const val CONSULTATION_CANDIDATES = "/api/v1/consultations/candidates"
        const val CONSULTATION_COMPLETE = "/api/v1/consultations/{consultationId}/complete"
        const val CONSULTATION_FAILURE_REASONS = "/api/v1/consultation-failure-reasons"
        const val CONTENT_OVERVIEW = "/api/v1/content-tasks/overview"
        const val CONTENT_POSTS = "/api/v1/content-posts"
        const val CONTENT_METRICS = "/api/v1/content-posts/{postId}/metrics"
        const val CIRCLE_POSTS = "/api/v1/circle/posts"
        const val ANNOUNCEMENTS = "/api/v1/announcements"
        const val RELEASES = "/api/v1/releases"
        const val UNACKNOWLEDGED_RELEASE = "/api/v1/releases/current/unacknowledged"
        const val ACKNOWLEDGE_RELEASE = "/api/v1/releases/{releaseId}/acknowledge"
        const val ASSISTANT_AVAILABILITY = "/api/v1/assistant/availability"
        const val ASSISTANT_CUSTOMER_SUMMARY = "/api/v1/assistant/customers/{customerId}/summary"
        const val AGENT_AUTHORIZATIONS = "/api/v1/agent/authorizations"
        const val AGENT_AUTHORIZATION = "/api/v1/agent/authorizations/{authorizationId}"
        const val CUSTOMER_POOLS = "/api/v1/customer-pools"
        const val CUSTOMER_POOL_CLAIM = "/api/v1/customer-pools/{entryId}/claims"
        const val SECOND_LINE_RECRUITMENTS = "/api/v1/second-line-recruitments"
        const val SECOND_LINE_CLAIM = "/api/v1/second-line-recruitments/{recruitmentId}/claim"
    }

    /** Canonical Android contract surface. CI compares these METHOD|template rows bidirectionally. */
    val contractOperations = setOf(
        "POST|/api/v1/mobile/auth/registration-options",
        "POST|/api/v1/mobile/auth/register",
        "POST|/api/v1/mobile/auth/login",
        "POST|/api/v1/mobile/auth/refresh",
        "POST|/api/v1/mobile/auth/logout",
        "POST|/api/v1/auth/password",
        "GET|/api/v1/mobile/auth/devices",
        "DELETE|/api/v1/mobile/auth/devices/{deviceId}",
        "GET|/api/v1/mobile/bootstrap",
        "POST|/api/v1/mobile/dashboard/greeting",
        "POST|/api/v1/mobile/customers/{customerId}/insight",
        "POST|/api/v1/mobile/devices/push-registration",
        "DELETE|/api/v1/mobile/devices/push-registration",
        "PUT|/api/v1/mobile/devices/notification-preferences",
        "POST|/api/v1/mobile/web-sessions",
        "GET|/api/v1/mobile/app-releases/current",
        "GET|/api/v1/mobile/app-releases/{releaseId}/apk",
        "GET|/api/v1/dashboard",
        "GET|/api/v1/team-data/overview",
        "GET|/api/v1/customers",
        "POST|/api/v1/customers",
        "GET|/api/v1/customers/{customerId}",
        "PATCH|/api/v1/customers/{customerId}",
        "GET|/api/v1/customers/{customerId}/collaboration",
        "PUT|/api/v1/customers/{customerId}/collaborators",
        "POST|/api/v1/customers/{customerId}/transfer",
        "POST|/api/v1/customers/{customerId}/archive",
        "GET|/api/v1/customers/{customerId}/classifications",
        "POST|/api/v1/customers/{customerId}/classifications",
        "GET|/api/v1/customers/{customerId}/deadline-shadow",
        "GET|/api/v1/exports/customers/{customerId}/timeline",
        "POST|/api/v1/imports/customers/preview",
        "POST|/api/v1/imports/{jobId}/commit",
        "GET|/api/v1/exports/customers",
        "GET|/api/v1/customers/{customerId}/interactions",
        "POST|/api/v1/customers/{customerId}/interactions",
        "POST|/api/v1/customers/{customerId}/deal-requests",
        "GET|/api/v1/reminders",
        "PATCH|/api/v1/reminders/{reminderId}",
        "GET|/api/v1/daily-reports/today",
        "PUT|/api/v1/daily-reports/{date}",
        "GET|/api/v1/notifications",
        "POST|/api/v1/notifications/{notificationId}/read",
        "POST|/api/v1/notifications/read-all",
        "GET|/api/v1/tasks",
        "POST|/api/v1/tasks/{taskId}/claim",
        "PATCH|/api/v1/tasks/{taskId}/my-assignment",
        "GET|/api/v1/performance/today",
        "POST|/api/v1/performance/claims",
        "POST|/api/v1/performance/claims/{claimId}/evidence",
        "POST|/api/v1/performance/claims/{claimId}/submit",
        "GET|/api/v1/consultations",
        "POST|/api/v1/consultations",
        "GET|/api/v1/consultations/{consultationId}",
        "GET|/api/v1/consultations/candidates",
        "POST|/api/v1/consultations/{consultationId}/accept",
        "POST|/api/v1/consultations/{consultationId}/reject",
        "POST|/api/v1/consultations/{consultationId}/end",
        "POST|/api/v1/consultations/{consultationId}/complete",
        "GET|/api/v1/consultation-failure-reasons",
        "GET|/api/v1/content-tasks/overview",
        "POST|/api/v1/content-posts",
        "PATCH|/api/v1/content-posts/{postId}/metrics",
        "GET|/api/v1/circle/posts",
        "GET|/api/v1/circle/senders",
        "GET|/api/v1/announcements",
        "GET|/api/v1/assistant/availability",
        "POST|/api/v1/assistant/customer-draft",
        "POST|/api/v1/assistant/interaction-draft",
        "GET|/api/v1/assistant/customers/{customerId}/summary",
        "GET|/api/v1/assistant/customers/{customerId}/next-actions",
        "GET|/api/v1/assistant/customers/{customerId}/data-quality",
        "GET|/api/v1/assistant/daily-briefing",
        "GET|/api/v1/releases",
        "GET|/api/v1/releases/current/unacknowledged",
        "POST|/api/v1/releases/{releaseId}/acknowledge",
        "GET|/api/v1/agent/authorizations",
        "DELETE|/api/v1/agent/authorizations/{authorizationId}",
        "GET|/api/v1/customer-pools",
        "POST|/api/v1/customer-pools/{entryId}/claims",
        "GET|/api/v1/second-line-recruitments",
        "POST|/api/v1/second-line-recruitments/{recruitmentId}/claim",
        "GET|/api/v1/mobile/admin/overview",
        "GET|/api/v1/admin/members",
        "POST|/api/v1/admin/members",
        "PATCH|/api/v1/admin/members/{membershipId}",
        "POST|/api/v1/admin/members/{membershipId}/delete",
        "GET|/api/v1/admin/teams",
        "POST|/api/v1/admin/teams",
        "PUT|/api/v1/admin/teams/{teamId}/leader",
        "DELETE|/api/v1/admin/teams/{teamId}",
        "GET|/api/v1/admin/registration-codes",
        "POST|/api/v1/admin/registration-codes",
        "POST|/api/v1/admin/registration-codes/{codeId}/disable",
        "GET|/api/v1/admin/deal-requests",
        "POST|/api/v1/admin/deal-requests/{requestId}/approve",
        "POST|/api/v1/admin/deal-requests/{requestId}/reject",
        "GET|/api/v1/admin/market/classification-reviews",
        "GET|/api/v1/admin/market/classification-anomalies",
        "POST|/api/v1/admin/market/classification-reviews/{classificationId}/select",
        "POST|/api/v1/admin/market/classification-reviews/{classificationId}/review",
        "GET|/api/v1/customers/{customerId}/classifications/current",
        "GET|/api/v1/admin/cross-sales",
        "GET|/api/v1/admin/cross-sales/export.xlsx",
        "POST|/api/v1/tasks/{taskId}/close",
        "DELETE|/api/v1/tasks/{taskId}",
        "GET|/api/v1/tasks/matrix",
        "PATCH|/api/v1/tasks/{taskId}/assignments/{assignmentId}",
        "POST|/api/v1/tasks",
        "PATCH|/api/v1/content-posts/{postId}/review",
        "POST|/api/v1/announcements/{announcementId}/archive",
        "POST|/api/v1/announcements",
        "PATCH|/api/v1/announcements/{announcementId}",
        "GET|/api/v1/daily-reports/calendar",
        "GET|/api/v1/performance/claims",
        "POST|/api/v1/performance/claims/{claimId}/review",
        "GET|/api/v1/performance/history",
        "GET|/api/v1/performance/leaderboard",
        "GET|/api/v1/performance/activities",
        "POST|/api/v1/performance/activities",
        "POST|/api/v1/performance/activities/{activityId}/evidence",
        "POST|/api/v1/performance/activities/{activityId}/complete",
        "POST|/api/v1/performance/activities/{activityId}/allocate",
        "POST|/api/v1/performance/activities/{activityId}/review",
        "GET|/api/v1/performance/admin/rules",
        "POST|/api/v1/performance/admin/rules",
        "GET|/api/v1/performance/admin/participants",
        "POST|/api/v1/performance/admin/participants",
        "PATCH|/api/v1/performance/admin/participants/{participantId}",
        "GET|/api/v1/performance/admin/days/{date}",
        "POST|/api/v1/performance/admin/days/{date}/settle",
        "POST|/api/v1/performance/admin/days/{date}/reopen",
        "GET|/api/v1/performance/admin/export",
        "POST|/api/v1/performance/admin/manual-entries",
        "GET|/api/v1/tags",
        "POST|/api/v1/tags",
        "GET|/api/v1/dictionaries",
        "POST|/api/v1/dictionaries",
        "GET|/api/v1/admin/assistant-settings",
        "PUT|/api/v1/admin/assistant-settings",
        "GET|/api/v1/admin/customer-intelligence/settings",
        "GET|/api/v1/admin/customer-intelligence/runtime",
        "POST|/api/v1/admin/customer-intelligence/backfill/resume",
        "POST|/api/v1/admin/customer-intelligence/backfill/pause",
        "GET|/api/v1/admin/customer-enrichment-jobs",
        "POST|/api/v1/admin/customer-enrichment-jobs/preview",
        "POST|/api/v1/admin/customer-enrichment-jobs/{jobId}/commit",
        "GET|/api/v1/admin/customer-enrichment-jobs/{jobId}/report.csv",
        "GET|/api/v1/admin/agent/authorizations",
        "DELETE|/api/v1/admin/agent/authorizations/{authorizationId}",
        "GET|/api/v1/admin/audit",
        "GET|/api/v1/admin/audit/export",
        "GET|/api/v1/admin/wecom-webhooks",
        "POST|/api/v1/admin/wecom-webhooks",
        "DELETE|/api/v1/admin/wecom-webhooks/{webhookId}",
        "GET|/api/v1/admin/wecom-webhooks/deliveries",
        "POST|/api/v1/admin/wecom-webhooks/deliveries/{deliveryId}/retry",
        "GET|/api/v1/admin/leadership-screen-settings",
        "PUT|/api/v1/admin/leadership-screen-settings",
        "GET|/api/v1/admin/follow-up-policy",
        "PUT|/api/v1/admin/follow-up-policy",
        "GET|/api/v1/admin/consultation-settings",
        "PUT|/api/v1/admin/consultation-settings",
        "POST|/api/v1/admin/consultation-reasons",
        "PUT|/api/v1/admin/consultation-reasons/{reasonId}",
        "GET|/api/v1/leadership-dashboard",
    )

    val reachableEndpoints = contractOperations.mapTo(linkedSetOf()) { operation ->
        val separator = operation.indexOf('|')
        Endpoint(operation.substring(0, separator), operation.substring(separator + 1))
    }
}
