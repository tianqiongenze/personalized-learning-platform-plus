-- Outbox 事件表(EER 铁律): 业务事务内同库写入, 独立 relay 投递 Pulsar
-- messageId 全链路幂等键: outbox.message_id == envelope.eventId
CREATE TABLE IF NOT EXISTS event_outbox (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(200)  NOT NULL,
    event_type   VARCHAR(100)  NOT NULL,
    tenant_id    VARCHAR(64),
    aggregate_id VARCHAR(64)   NOT NULL,
    payload      JSONB         NOT NULL,
    message_id   VARCHAR(64)   NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    attempts     INT           NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_undelivered
    ON event_outbox (created_at) WHERE delivered_at IS NULL;
