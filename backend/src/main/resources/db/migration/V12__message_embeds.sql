CREATE TABLE message_embeds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    canonical_id VARCHAR(64) NOT NULL,
    source_url TEXT NOT NULL,
    title TEXT NULL,
    thumbnail_url TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (message_id, canonical_id)
);
CREATE INDEX idx_message_embeds_canonical ON message_embeds(canonical_id);
CREATE INDEX idx_message_embeds_message ON message_embeds(message_id);

CREATE TABLE direct_message_embeds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    direct_message_id UUID NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    canonical_id VARCHAR(64) NOT NULL,
    source_url TEXT NOT NULL,
    title TEXT NULL,
    thumbnail_url TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (direct_message_id, canonical_id)
);
CREATE INDEX idx_dm_embeds_canonical ON direct_message_embeds(canonical_id);
CREATE INDEX idx_dm_embeds_message ON direct_message_embeds(direct_message_id);
