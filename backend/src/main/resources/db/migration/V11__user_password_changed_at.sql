-- Track when the user's password was last set so JWTs issued before that moment
-- can be rejected at the filter layer. Covers the gap where presence-scoped
-- session revocation misses REST-only tokens.
--
-- Default NOW() for existing rows. This does invalidate any JWTs issued before
-- the migration ran for existing users (they'll need to re-login once), which
-- is the intended semantics: we know the column wasn't tracked before, so we
-- conservatively assume passwords were just set.

ALTER TABLE users
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
