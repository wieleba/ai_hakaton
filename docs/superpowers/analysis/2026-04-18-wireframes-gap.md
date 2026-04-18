# Wireframes Gap Analysis (Appendix A of requirements_v3.pdf)

_Notes from reading Appendix A + section 4 "UI Requirements" mid-Feature-4._

## What the spec mandates (§4 + Appendix A)

Authoritative layout:

```
+-------------------------------------------------------------+
| ChatLogo | Public Rooms | Private Rooms | Contacts          |   ← top menu
|          | Sessions | Profile ▼ | Sign out                  |
+-------------------------------------------------------------+
| Left sidebar (tree) | Main chat area    | Right: members    |
|                     |                   | panel + admin     |
| Search [       ]    | # room-name       | actions           |
| ROOMS               | description       |                   |
| > Public Rooms      | [messages...]     | Room info         |
|   • general (3)     |                   | Owner / Admins    |
|   • engineering     | [replying-to pill]| Members (38)      |
| > Private Rooms     | [emoji][Attach]   | ● online          |
|   • core-team (1)   | [multiline input] | ◐ AFK             |
| CONTACTS            | [Send]            | ○ offline (2)     |
|  ● Alice (online)   |                   | [Invite user]     |
|  ◐ Bob (AFK)        |                   | [Manage room]     |
|  ○ Carol (2 unread) |                   |                   |
| [Create room]       |                   |                   |
+---------------------+-------------------+-------------------+
```

- **Top menu** (not left sidebar) for primary navigation
- Rooms and contacts live in one tree-style sidebar (Public Rooms expand, Private Rooms expand, Contacts list with presence dots)
- Chat area has a full composition bar: emoji picker, Attach button, "Replying to: X ×" pill, multiline textarea, Send
- Members panel on the right with presence dots and **"Manage room"** button that opens a tabbed modal (Members / Admins / Banned users / Invitations / Settings)
- Classic web chat feel (explicit: "The application should resemble a classic web chat rather than a modern social network or collaboration suite.")

## What we have today (Features #1–#4 WIP)

- **AppSidebar** = vertical left-side nav with three links (Rooms / Friends / Direct Messages) → separate pages (RoomListPage, FriendsPage, DirectMessagesPage, ChatPage)
- RoomListPage: tabbed (Public | My rooms) with cards; no tree-style sidebar entry with unread counts
- ChatPage: header (room name + Leave) + MessageList + MessageInput + RoomMembersPanel on the right
- Moderation controls spread across separate modals (being built in Feature #4): `InviteUserModal`, `BanListPanel`, `DeleteRoomDialog`
- No emoji picker, no attach button, no reply-to pill, no presence dots, no unread badges, no "Keep me signed in" checkbox, no Forgot password flow, no Sessions page, no Profile menu

## Gap size

**Layout architecture mismatch** (the biggest chunk):
1. Top menu instead of left sidebar for primary nav
2. Rooms + Contacts tree (with visibility split and unread counts) as a single panel on one side
3. Unified "Chat app shell" — main area swaps content but the shell stays — vs. current full-page-per-route model
4. Tabbed **Manage Room** modal (Members/Admins/Banned/Invitations/Settings) vs. our separate single-purpose modals

**UI pieces still missing** (already planned in later features):
- Attachments UI (Feature #6)
- Reply-to pill / message threading (Feature #5)
- Presence dots (● ◐ ○) and unread badges (Feature #7 + notifications)
- Sessions page (Feature #7)
- Forgot-password flow + "Keep me signed in" (Feature #8)

## Implications for ordering

If we do Features #5–#8 FIRST against the current AppSidebar-based layout, we will:
- Build reply pills / emoji picker / attachment buttons in `MessageInput` as it exists today → then move them again when the shell changes
- Build presence indicators into `RoomMembersPanel` and `FriendsList` → then move again
- Add a Sessions page and Profile dropdown that need to live in the top menu → no home for them today

If we do the layout refactor FIRST (as Feature #5 in execution order):
- Feature #5 (message content: reply/edit/delete) and #6 (attachments) compose INTO the new MessageInput that already has the attach/reply/emoji slots
- Feature #7 (presence) drops into the tree sidebar's existing presence-dot spots and the right-panel member list
- The tabbed Manage Room modal receives the admin pieces we built in Feature #4 (kick/ban/promote) without needing to be rewritten

## Recommendation (post-Feature-4 decision)

**Make the look-and-feel refactor Feature #5 in execution order**, renumbering later features accordingly:

- Proposed execution #5: "UI shell refactor per Appendix A" — top menu, tree sidebar with unread slots (placeholders OK until Feature #7), tabbed Manage Room modal, MessageInput expanded with emoji/attach/reply slots (disabled/hidden for now).
- Proposed execution #6: "Message content features" (original Feature #5 scope: edit, reply, delete) — now fits naturally into the new shell.
- Etc.

### Why not defer the refactor

Every feature from #5 onward touches at least one of: MessageInput, RoomMembersPanel, AppSidebar, ChatPage. Touching them twice (once to add the feature to the current shell, once again when we refactor the shell) is strictly more work than refactoring first and then adding features into their final home.

### Why not do it instead of Feature #4

Feature #4 is ~2/3 done (6/19 tasks complete). The moderation actions we're still building (Task 8's RoomModerationController, Task 9's RoomInvitationController, Task 16's RoomMembersPanel admin controls, Task 17's BanListPanel, Task 18's DeleteRoomDialog) will drop cleanly into the tabbed Manage Room modal later — the individual components stay useful.

### Caveat

The refactor itself is a big feature, probably ~15–20 tasks spanning ~8–12h. If the timeline after Feature #4 looks tight, alternatives:
1. Minimal refactor: only move nav from left sidebar to top menu; keep separate pages. (~3–4h)
2. Full refactor per above. (~10h)

Revisit size estimate once Feature #4 lands.
