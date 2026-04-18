-- 1. Drop users table (no prod data) and recreate with UUID PK.
--    Cascade drops any FK-pointing columns, but Feature #2 tables only had
--    UUID user_id columns WITHOUT FK constraints, so they survive untouched.
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Add FK constraints on Feature #2 tables now that users.id is UUID.
--    Remove any orphan rows first (hackathon dev DB — safe to truncate).
TRUNCATE TABLE messages, room_members, chat_rooms CASCADE;

ALTER TABLE chat_rooms
    ADD CONSTRAINT fk_chat_rooms_owner FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE chat_rooms
    ADD CONSTRAINT chat_rooms_visibility_check CHECK (visibility IN ('public', 'private'));

ALTER TABLE room_members
    ADD CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_user FOREIGN KEY (user_id) REFERENCES users(id);

-- 3. Convert existing TIMESTAMP columns to TIMESTAMPTZ (UTC interpretation).
ALTER TABLE chat_rooms
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE chat_rooms
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE room_members
    ALTER COLUMN joined_at TYPE TIMESTAMPTZ USING joined_at AT TIME ZONE 'UTC';

ALTER TABLE room_members
    ALTER COLUMN joined_at SET DEFAULT now();

ALTER TABLE messages
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE messages
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

-- 4. New Feature #3 tables.

CREATE TABLE friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id),
    addressee_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'accepted')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friendship_pair UNIQUE (requester_id, addressee_id),
    CONSTRAINT no_self_friendship CHECK (requester_id <> addressee_id)
);

CREATE INDEX idx_friendships_addressee_status ON friendships(addressee_id, status);
CREATE INDEX idx_friendships_requester_status ON friendships(requester_id, status);

CREATE TABLE user_bans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    banner_id UUID NOT NULL REFERENCES users(id),
    banned_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_ban_pair UNIQUE (banner_id, banned_id),
    CONSTRAINT no_self_ban CHECK (banner_id <> banned_id)
);

CREATE INDEX idx_user_bans_banned ON user_bans(banned_id);

CREATE TABLE direct_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user1_id UUID NOT NULL REFERENCES users(id),
    user2_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_direct_conv_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT canonical_user_order CHECK (user1_id < user2_id)
);

CREATE TABLE direct_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES direct_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    text VARCHAR(3072) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_direct_messages_conv_created ON direct_messages(conversation_id, created_at DESC);
