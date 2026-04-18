ALTER TABLE messages
  ADD COLUMN edited_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_by UUID NULL REFERENCES users(id),
  ADD COLUMN reply_to_id UUID NULL REFERENCES messages(id) ON DELETE SET NULL;

ALTER TABLE direct_messages
  ADD COLUMN edited_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_at TIMESTAMPTZ NULL,
  ADD COLUMN deleted_by UUID NULL REFERENCES users(id),
  ADD COLUMN reply_to_id UUID NULL REFERENCES direct_messages(id) ON DELETE SET NULL;

CREATE INDEX idx_messages_reply_to ON messages(reply_to_id);
CREATE INDEX idx_direct_messages_reply_to ON direct_messages(reply_to_id);
