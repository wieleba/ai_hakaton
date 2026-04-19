-- V7: rewrite every user-referencing FK to support account deletion.
-- Dev-only migration — user has confirmed "don't worry about migrating data".

-- Allow messages.user_id to be NULL so author-deleted messages survive.
ALTER TABLE messages ALTER COLUMN user_id DROP NOT NULL;

-- chat_rooms.owner_id → CASCADE (deleting an owner deletes their rooms).
ALTER TABLE chat_rooms DROP CONSTRAINT fk_chat_rooms_owner;
ALTER TABLE chat_rooms
  ADD CONSTRAINT fk_chat_rooms_owner
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_members.user_id → CASCADE (memberships removed with the user).
ALTER TABLE room_members DROP CONSTRAINT fk_room_members_user;
ALTER TABLE room_members
  ADD CONSTRAINT fk_room_members_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.user_id → SET NULL (messages in non-owned rooms survive as "Deleted user").
ALTER TABLE messages DROP CONSTRAINT fk_messages_user;
ALTER TABLE messages
  ADD CONSTRAINT fk_messages_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- friendships — CASCADE both sides.
ALTER TABLE friendships DROP CONSTRAINT friendships_requester_id_fkey;
ALTER TABLE friendships
  ADD CONSTRAINT friendships_requester_id_fkey
  FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE friendships DROP CONSTRAINT friendships_addressee_id_fkey;
ALTER TABLE friendships
  ADD CONSTRAINT friendships_addressee_id_fkey
  FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE;

-- user_bans — CASCADE both sides.
ALTER TABLE user_bans DROP CONSTRAINT user_bans_banner_id_fkey;
ALTER TABLE user_bans
  ADD CONSTRAINT user_bans_banner_id_fkey
  FOREIGN KEY (banner_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_bans DROP CONSTRAINT user_bans_banned_id_fkey;
ALTER TABLE user_bans
  ADD CONSTRAINT user_bans_banned_id_fkey
  FOREIGN KEY (banned_id) REFERENCES users(id) ON DELETE CASCADE;

-- direct_conversations — CASCADE both sides (conversation + its DMs vanish on either side's delete).
ALTER TABLE direct_conversations DROP CONSTRAINT direct_conversations_user1_id_fkey;
ALTER TABLE direct_conversations
  ADD CONSTRAINT direct_conversations_user1_id_fkey
  FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE direct_conversations DROP CONSTRAINT direct_conversations_user2_id_fkey;
ALTER TABLE direct_conversations
  ADD CONSTRAINT direct_conversations_user2_id_fkey
  FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE;

-- direct_messages.sender_id — CASCADE (redundant via conversation but symmetric).
ALTER TABLE direct_messages DROP CONSTRAINT direct_messages_sender_id_fkey;
ALTER TABLE direct_messages
  ADD CONSTRAINT direct_messages_sender_id_fkey
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_invitations — CASCADE both sides.
ALTER TABLE room_invitations DROP CONSTRAINT room_invitations_inviter_id_fkey;
ALTER TABLE room_invitations
  ADD CONSTRAINT room_invitations_inviter_id_fkey
  FOREIGN KEY (inviter_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE room_invitations DROP CONSTRAINT room_invitations_invitee_id_fkey;
ALTER TABLE room_invitations
  ADD CONSTRAINT room_invitations_invitee_id_fkey
  FOREIGN KEY (invitee_id) REFERENCES users(id) ON DELETE CASCADE;

-- room_bans — CASCADE both sides.
ALTER TABLE room_bans DROP CONSTRAINT room_bans_banned_user_id_fkey;
ALTER TABLE room_bans
  ADD CONSTRAINT room_bans_banned_user_id_fkey
  FOREIGN KEY (banned_user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE room_bans DROP CONSTRAINT room_bans_banned_by_id_fkey;
ALTER TABLE room_bans
  ADD CONSTRAINT room_bans_banned_by_id_fkey
  FOREIGN KEY (banned_by_id) REFERENCES users(id) ON DELETE CASCADE;

-- messages.deleted_by → SET NULL.
ALTER TABLE messages DROP CONSTRAINT messages_deleted_by_fkey;
ALTER TABLE messages
  ADD CONSTRAINT messages_deleted_by_fkey
  FOREIGN KEY (deleted_by) REFERENCES users(id) ON DELETE SET NULL;

-- direct_messages.deleted_by → SET NULL.
ALTER TABLE direct_messages DROP CONSTRAINT direct_messages_deleted_by_fkey;
ALTER TABLE direct_messages
  ADD CONSTRAINT direct_messages_deleted_by_fkey
  FOREIGN KEY (deleted_by) REFERENCES users(id) ON DELETE SET NULL;
