CREATE TABLE wecom_ai_bot_identity_bindings (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    user_account_id uuid NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    wecom_userid varchar(160) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    bound_at timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_wecom_bot_active_user
    ON wecom_ai_bot_identity_bindings(organization_id,wecom_userid) WHERE active;
CREATE UNIQUE INDEX uq_wecom_bot_active_membership
    ON wecom_ai_bot_identity_bindings(organization_id,membership_id) WHERE active;

CREATE TABLE wecom_ai_bot_binding_codes (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    user_account_id uuid NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    code_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX wecom_bot_binding_code_expiry_idx
    ON wecom_ai_bot_binding_codes(expires_at) WHERE consumed_at IS NULL;

CREATE TABLE wecom_ai_bot_event_inbox (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    message_id varchar(256) NOT NULL,
    request_id varchar(256),
    chat_id varchar(256),
    chat_type varchar(32),
    wecom_userid varchar(160),
    command_name varchar(80),
    status varchar(24) NOT NULL DEFAULT 'RECEIVED',
    error_code varchar(120),
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    CONSTRAINT wecom_bot_event_status_check CHECK (
        status IN ('RECEIVED','APPLIED','REJECTED','FAILED','IGNORED')
    ),
    CONSTRAINT uq_wecom_bot_event_message UNIQUE (organization_id,message_id)
);
CREATE INDEX wecom_bot_event_status_idx
    ON wecom_ai_bot_event_inbox(status,received_at);

UPDATE product_releases SET current=false WHERE current;
INSERT INTO product_releases (
    id,version,title,summary,sections,released_at,published,current
) VALUES (
    '20000000-0000-0000-0000-000000000034',
    '2026.09.28.1',
    '企业微信智能机器人接入',
    '新增企业微信长连接、账号绑定及 CRM 查询入口，飞书能力继续保留。',
    '{"新增":["企业微信智能机器人长连接与自动重连","企业微信账号一次性验证码绑定","机器人内打开 CRM、客户搜索和成单榜单"],"改进":["协同入口按平台身份重新执行英格云权限校验","企业微信与飞书可并行使用"],"安全":["Bot Secret 仅从部署环境读取且不写入数据库、日志或前端","机器人消息按 message_id 去重并保留脱敏审计"]}'::jsonb,
    '2026-09-28T12:00:00+08:00',true,true
);
