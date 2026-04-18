-- Add role column to room_members (existing rows default to 'member').
ALTER TABLE room_members
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'member'
    CHECK (role IN ('member', 'admin'));

-- Invitations live per (room, invitee). Accept deletes row + inserts membership;
-- decline deletes row. No historical audit needed.
CREATE TABLE room_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id),
    invitee_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_invitee UNIQUE (room_id, invitee_id),
    CONSTRAINT no_self_invite CHECK (inviter_id <> invitee_id)
);

CREATE INDEX idx_room_invitations_invitee ON room_invitations(invitee_id);

-- Room bans: "kick" atomically deletes room_members row + inserts here.
-- Preserves banned_by + banned_at so admins can see who banned each user.
CREATE TABLE room_bans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    banned_user_id UUID NOT NULL REFERENCES users(id),
    banned_by_id UUID NOT NULL REFERENCES users(id),
    banned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_room_banned_user UNIQUE (room_id, banned_user_id)
);

CREATE INDEX idx_room_bans_banned_user ON room_bans(banned_user_id);
