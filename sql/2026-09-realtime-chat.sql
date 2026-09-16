-- Realtime chat schema. PostgreSQL is the source of truth; Redis Pub/Sub only
-- transports committed events between backend instances.
BEGIN;

CREATE TABLE IF NOT EXISTS conversations (
    id              BIGSERIAL PRIMARY KEY,
    shop_id         BIGINT NOT NULL REFERENCES shops(id),
    buyer_id        BIGINT NOT NULL REFERENCES users(id),
    seller_id       BIGINT NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_conversation_distinct_users CHECK (buyer_id <> seller_id),
    CONSTRAINT chk_conversation_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT uq_conversation_shop_buyer_seller UNIQUE (shop_id, buyer_id, seller_id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_buyer_activity
    ON conversations(buyer_id, last_message_at DESC NULLS LAST, id DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_seller_activity
    ON conversations(seller_id, last_message_at DESC NULLS LAST, id DESC);

CREATE TABLE IF NOT EXISTS conversation_participants (
    id                   BIGSERIAL PRIMARY KEY,
    conversation_id      BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    participant_role     VARCHAR(20) NOT NULL,
    last_read_message_id BIGINT,
    last_read_at         TIMESTAMPTZ,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_conversation_participant_role CHECK (participant_role IN ('BUYER', 'SELLER')),
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_conversation_participants_user
    ON conversation_participants(user_id, conversation_id);

CREATE TABLE IF NOT EXISTS messages (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id         BIGINT NOT NULL REFERENCES users(id),
    client_message_id UUID NOT NULL,
    message_type      VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    content           VARCHAR(2000) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_message_type CHECK (message_type IN ('TEXT')),
    CONSTRAINT chk_message_content CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000),
    CONSTRAINT uq_message_sender_client_id UNIQUE (sender_id, client_message_id)
);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_cursor
    ON messages(conversation_id, id DESC);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_conversation_participant_last_read') THEN
        ALTER TABLE conversation_participants
            ADD CONSTRAINT fk_conversation_participant_last_read
            FOREIGN KEY (last_read_message_id) REFERENCES messages(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS message_reads (
    id         BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_message_read UNIQUE (message_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_message_reads_user
    ON message_reads(user_id, message_id DESC);

COMMIT;
