-- Additive Agent state; original CRM/Feishu history is never rewritten.
ALTER TABLE oauth_grants ALTER COLUMN expires_at DROP NOT NULL;
ALTER TABLE oauth_grants ADD CONSTRAINT oauth_long_lived_internal_client_check
    CHECK (expires_at IS NOT NULL OR client_id='yingge_wecom_agent_internal');

CREATE TABLE wecom_agent_settings (
    organization_id uuid PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    enabled boolean NOT NULL DEFAULT false,
    submission_chat_id varchar(256) NOT NULL DEFAULT '',
    broadcast_chat_id varchar(256) NOT NULL DEFAULT '',
    full_data_risk_accepted_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE wecom_agent_credentials (
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    membership_id uuid PRIMARY KEY REFERENCES memberships(id) ON DELETE CASCADE,
    grant_id uuid NOT NULL REFERENCES oauth_grants(id) ON DELETE CASCADE,
    encrypted_access_token text NOT NULL,
    encrypted_refresh_token text NOT NULL,
    access_expires_at timestamptz NOT NULL,
    refresh_expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE wecom_agent_conversations (
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    chat_id varchar(256) NOT NULL,
    encrypted_messages text NOT NULL,
    last_used_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (organization_id,membership_id,chat_id)
);
CREATE INDEX wecom_agent_history_expiry_idx ON wecom_agent_conversations(expires_at);
CREATE TABLE wecom_agent_pending_actions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    chat_id varchar(256) NOT NULL,
    grant_id uuid NOT NULL REFERENCES oauth_grants(id) ON DELETE CASCADE,
    execution_key varchar(160) NOT NULL,
    tool_name varchar(180) NOT NULL,
    encrypted_arguments text NOT NULL,
    approval_id uuid NOT NULL REFERENCES mcp_approval_requests(id),
    status varchar(24) NOT NULL DEFAULT 'PENDING'
      CHECK (status IN ('PENDING','SENDING','APPLIED','REJECTED','UNCERTAIN')),
    expires_at timestamptz NOT NULL,
    encrypted_result text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id,membership_id,execution_key)
);
CREATE INDEX wecom_pending_action_expiry_idx ON wecom_agent_pending_actions(expires_at);
CREATE TABLE wecom_deal_conversations (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    membership_id uuid NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    chat_id varchar(256) NOT NULL,
    state_ciphertext text NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organization_id,membership_id,chat_id)
);
CREATE INDEX wecom_deal_conversation_expiry_idx ON wecom_deal_conversations(expires_at);

ALTER TABLE wecom_ai_bot_event_inbox ADD COLUMN reply_ciphertext bytea,
    ADD COLUMN processing_started_at timestamptz;
ALTER TABLE wecom_ai_bot_event_inbox DROP CONSTRAINT wecom_bot_event_status_check;
ALTER TABLE wecom_ai_bot_event_inbox ADD CONSTRAINT wecom_bot_event_status_check
    CHECK (status IN ('RECEIVED','PROCESSING','APPLIED','REJECTED','FAILED','IGNORED','UNCERTAIN'));

CREATE TABLE wecom_ai_bot_connection_leases (
    bot_key varchar(256) PRIMARY KEY,
    owner_id uuid NOT NULL,
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE wecom_ai_bot_message_outbox (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    chat_id varchar(256) NOT NULL,
    dedup_key varchar(256) NOT NULL,
    request_id varchar(256) NOT NULL UNIQUE,
    callback_request_id varchar(256),
    callback_message_id varchar(256),
    encrypted_payload bytea NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SENDING','SENT','FAILED','UNCERTAIN')),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    error_code varchar(120),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    UNIQUE (organization_id,dedup_key)
);
CREATE INDEX wecom_outbox_due_idx ON wecom_ai_bot_message_outbox(status,next_attempt_at);

ALTER TABLE customer_deal_requests ADD COLUMN confirmation_payload_hash char(64);
ALTER TABLE customer_deal_requests DROP CONSTRAINT customer_deal_request_source_check;
ALTER TABLE customer_deal_requests ADD CONSTRAINT customer_deal_request_source_check
    CHECK (source IN ('DIRECT','LEGACY_BUSINESS_CYCLE','FEISHU_BOT','FEISHU_WEB','WECOM_BOT'));
ALTER TABLE customer_deal_requests DROP CONSTRAINT customer_deal_confirmation_channel_check;
ALTER TABLE customer_deal_requests ADD CONSTRAINT customer_deal_confirmation_channel_check
    CHECK (confirmation_channel IS NULL OR confirmation_channel IN
      ('WEB_ADMIN','ANDROID_ADMIN','FEISHU_BOT','FEISHU_WEB','WECOM_BOT','LEGACY'));

ALTER TABLE customers DROP CONSTRAINT ck_customer_contact;
ALTER TABLE customers ADD CONSTRAINT ck_customer_contact CHECK (
    nullif(trim(coalesce(phone,'')),'') IS NOT NULL OR
    nullif(trim(coalesce(wechat_id,'')),'') IS NOT NULL OR
    nullif(trim(coalesce(qq_id,'')),'') IS NOT NULL OR
    (contact_completion_status='PENDING' AND verification_status='未核实'
      AND source_channel_legacy IN ('飞书临时建档','企业微信快速建档'))
);

UPDATE product_releases SET current=false WHERE current;
INSERT INTO product_releases(id,version,title,summary,sections,released_at,published,current)
VALUES ('20000000-0000-0000-0000-000000000035','2026.09.28.2',
  '企业微信自然语言与 Slash 双模式助手',
  '新增长期可撤销 Agent 授权、共享成单草稿及企业微信独立广播。',
  '{"新增":["企业微信自然语言调用已授权 CRM 工具","Slash 与自然语言共享客户选择和成单草稿","企业微信成单与奖金广播"],"改进":["账号长期授权与短期令牌轮换","独立企业微信管理与失败重试"],"安全":["敏感数据加密保存","转接和成单必须本人确认","机器人业务群需要管理员公开风险确认"]}'::jsonb,
  '2026-09-28T18:00:00+08:00',true,true);
