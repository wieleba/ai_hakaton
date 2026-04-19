-- Per-user read cursor for rooms and direct-message conversations.
-- chat_type distinguishes room vs DM so the same chat_id space (UUID) can coexist.
-- Orphan rows (after room/DM deletion) are harmless: unread-count queries join on
-- live membership, so they're filtered naturally. users FK cascades on account delete.

CREATE TABLE chat_read_markers (
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_type    VARCHAR(16) NOT NULL,
    chat_id      UUID        NOT NULL,
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, chat_type, chat_id)
);

CREATE INDEX idx_chat_read_markers_user ON chat_read_markers (user_id);
