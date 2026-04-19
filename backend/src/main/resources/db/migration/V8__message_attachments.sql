CREATE TABLE message_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_message_attachments_message ON message_attachments(message_id);
CREATE UNIQUE INDEX uq_message_attachments_message ON message_attachments(message_id);

CREATE TABLE direct_message_attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  direct_message_id UUID NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
  filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_direct_message_attachments_message ON direct_message_attachments(direct_message_id);
CREATE UNIQUE INDEX uq_direct_message_attachments_message ON direct_message_attachments(direct_message_id);
