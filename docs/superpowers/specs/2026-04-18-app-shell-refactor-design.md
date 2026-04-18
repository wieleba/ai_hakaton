# Feature #5 (execution): App Shell Refactor per Appendix A — Design

**Status:** approved 2026-04-18
**Supersedes ordering:** takes the Feature #5 execution slot; original "Message Content Enhancements" becomes execution #6.
**Context:** `docs/superpowers/analysis/2026-04-18-wireframes-gap.md` establishes why the shell refactor lands before the remaining content features.

## Goal

Bring the web client's layout in line with Appendix A of the requirements PDF so that every subsequent feature (#5 message content, #6 attachments, #7 presence/sessions, #8 account management) drops into its final home instead of being built twice.

## Non-goals

| Out of scope | Why | Where it lands |
|---|---|---|
| Live presence dots | Requires websocket presence + AFK tracking | Feature #7 |
| Live unread counts | Requires per-user read markers + websocket delivery | Feature #7 |
| Emoji picker UI | Content feature | Feature #5 (content) |
| Attach button + upload UI | File feature | Feature #6 |
| Reply-to pill | Content feature | Feature #5 (content) |
| Session management functionality | Account feature | Feature #7 |
| Profile editing | Account feature | Feature #8 |
| Search highlighting / fuzzy ranking | YAGNI for hackathon | Future |
| Websocket-driven sidebar refreshes | Complexity, deadline | Feature #7 |

## Target layout

```
┌─────────────────────────────────────────────────────────────┐
│  TopMenu                                                    │
│  [ChatLogo] Public Rooms   Contacts   Sessions   Profile ▼  │
├──────────────┬───────────────────────────┬──────────────────┤
│              │                           │                  │
│  SideTree    │  <Outlet /> (route pane)  │  RightPanel      │
│              │                           │  (in-room only)  │
│  [Search  ]  │                           │                  │
│              │                           │  # room-name     │
│  ROOMS       │                           │  public          │
│   ▸ Public   │                           │                  │
│     • general│                           │  Members         │
│     • eng    │                           │   Online (0)     │
│   ▸ Private  │                           │   AFK (0)        │
│     • core   │                           │   Offline (3)    │
│              │                           │    • alice owner │
│  CONTACTS    │                           │    • bob   admin │
│   ○ Alice    │                           │    • carol       │
│   ○ Bob      │                           │                  │
│              │                           │  [Invite user]*  │
│  [+ Room]    │                           │  [Manage room]*  │
└──────────────┴───────────────────────────┴──────────────────┘
   * admin-only
```

## Architecture

### Routing (option C — two-pane SPA with URL state)

All authenticated routes nest under a new `AppShell` layout route:

```
<Route element={<AppShell />}>
  <Route path="/rooms"            element={<RoomListPage />} />   // public discovery
  <Route path="/rooms/:roomId"    element={<ChatPage />} />       // room pane + right panel
  <Route path="/friends"          element={<FriendsPage />} />    // contacts management
  <Route path="/dms"              element={<DirectMessagesPage />} />   // landing: "pick a contact"
  <Route path="/dms/:friendId"    element={<DirectChatPage />} />        // DM pane
  <Route path="/sessions"         element={<SessionsStub />} />
  <Route path="/"                 element={<Navigate to="/rooms" />} />
</Route>
<Route path="/login"    element={<LoginPage />} />
<Route path="/register" element={<RegisterPage />} />
```

`AppShell` renders `<TopMenu />`, `<SideTree />`, `<Outlet />`, and a `<RightPanel />` that is non-null only when the URL matches `/rooms/:roomId` or `/dms/:friendId`.

The existing `AppSidebar` is deleted.

### Components introduced

| Component | Purpose | Owns state |
|---|---|---|
| `AppShell` | Layout route; composes TopMenu/SideTree/Outlet/RightPanel | none |
| `TopMenu` | Top navigation bar + profile dropdown | profile-dropdown open/close |
| `SideTree` | Left tree sidebar (Search + Rooms + Contacts + Create) | search query, section collapsed flags, dropdown visibility |
| `SideTreeRoomList` | Collapsible room section | `isOpen` |
| `SideTreeContactList` | Collapsible contacts section | `isOpen` |
| `SearchDropdown` | Debounced suggestions (rooms + users) | query, loading, results |
| `RightPanel` | Route-aware container; renders `RoomMembersPanel` on `/rooms/:id`, nothing elsewhere for now | none |
| `RoomMembersPanel` (restructured in place) | room info + members grouped by presence + admin buttons at bottom | none (reloads via hooks) |
| `ManageRoomModal` | Tabbed admin modal | active tab |
| `ManageMembersTab` | Member list with Kick/Promote/Demote | — |
| `ManageInvitationsTab` | Outgoing invitations + Cancel | invitations list |
| `ManageBannedTab` | Wraps existing `BanListPanel` body | — |
| `ManageSettingsTab` | Room metadata (read-only name) + owner-only Delete | — |
| `ComposerActions` | Empty slot inside `MessageInput` for Features #5/#6 | — |
| `SessionsStub` | Placeholder page | — |
| `DirectMessagesLanding` | Shown at `/dms` when no friend selected | — |
| `ProfileMenu` | Top-menu dropdown — username (read-only) + Sign out | open/close |

### Components removed

| Component | Reason |
|---|---|
| `AppSidebar` | Replaced by `SideTree` + `TopMenu` |

### Data sources & reload triggers

| Section | Source | Reloads when |
|---|---|---|
| Sidebar public rooms | `roomService.listMyRooms()` filtered to `visibility === 'public'` | mount; after create-room modal closes; on navigation into/out of `/rooms/:id` |
| Sidebar private rooms | `roomService.listMyRooms()` filtered to `visibility === 'private'` | same |
| Sidebar contacts | `friendshipService.listFriends()` | mount; on navigation into/out of `/dms/:id`; when Friends page actions complete (manual) |
| Invitations (no longer on My rooms tab) | `roomInvitationService.listMyIncoming()` | mount of `AppShell`; polled on navigation back to `/rooms` |
| Right-panel members | existing `useRoomMembersWithRole` | existing behavior; also after actions in Manage modal |

### Search endpoint

**Contract**

```http
GET /api/search?q={query}&limit={limit}
Authorization: Bearer {jwt}
```

- `q`: user input, trimmed server-side; empty → empty arrays
- `limit`: optional, default 5, max 10

Response body:

```json
{
  "rooms": [
    {"id": "uuid", "name": "string", "description": "string|null", "visibility": "public"}
  ],
  "users": [
    {"id": "uuid", "username": "string"}
  ]
}
```

**Semantics**

- Rooms: `visibility='public'` AND `name ILIKE '%q%'`, excluding rooms where the caller is already a member. Limit applied after exclusion.
- Users: `username ILIKE '%q%'`, excluding the caller. Limit applied after exclusion.
- No ranking, no highlighting; database default ordering.

**Implementation**

- `SearchController` in `features/search/` (new flat feature package per backend CLAUDE.md)
- `SearchService` with two queries via repository method signatures on `ChatRoomRepository` and `UserRepository` (avoids native SQL)
- Tests: `SearchServiceTest` (Instancio fixtures, in-memory slice) + `SearchControllerTest` (MockMvc)

### MessageInput changes

- `MessageInput` renders its children around a new `ComposerActions` wrapper (flex row, sits left of the Send button). Today it receives no children; Feature #5/#6 will inject the emoji/attach/reply buttons.
- No visible change today.

### Right panel & Manage Room modal split

**Permanent right panel (`RoomMembersPanel`, restructured):**
- Header: room name, visibility pill
- Members grouped by presence (Online / AFK / Offline). Every member goes into Offline today. Per-row: username, owner/admin badge, Add-friend button for non-friends.
- Admin-only buttons at the bottom:
  - `Invite user` (private rooms only) — opens existing `InviteUserModal`
  - `Manage room` — opens `ManageRoomModal`
- Row-level moderation actions (Kick/Promote/Demote) are **removed** from this panel — they now live in the Manage modal's Members tab.

**`ManageRoomModal`** — shown when admin clicks `Manage room`. Tabs:

| Tab | Contents | Data source |
|---|---|---|
| Members | Full member list with role badges, Kick / Promote / Demote per row | `useRoomMembersWithRole` + `useRoomAdminActions` |
| Invitations | Outgoing invitations list with Cancel per row | `roomInvitationService.listOutgoingForRoom` + `cancelInvitation` |
| Banned | Banned user list with Unban per row | existing `BanListPanel` body inlined |
| Settings | Read-only room name + description; owner-only **Delete room** button | opens existing `DeleteRoomDialog` |

`BanListPanel` and `DeleteRoomDialog` stay as internal components used by the Manage modal (no longer opened directly from `ChatPage`).

### TopMenu items

Left to right:

| Item | Route | Notes |
|---|---|---|
| Chat logo | `/` (→ `/rooms` if auth) | text-only for now |
| Public Rooms | `/rooms` | opens existing `RoomListPage`, which is trimmed to the **Public** discovery pane (the "My rooms" tab is dropped; sidebar owns that) |
| Contacts | `/friends` | existing `FriendsPage` unchanged except wrapped in AppShell |
| Sessions | `/sessions` | new `SessionsStub` page with placeholder text |
| Profile ▼ | dropdown | read-only username + Sign out |

Private Rooms is intentionally absent from the top menu — there is no global private-rooms discovery.

### Page-level changes

| Route | Change |
|---|---|
| `/rooms` | `RoomListPage` loses the tab switcher and always shows public discovery. Drop `useMyRooms` and `useRoomInvitations` imports here (they now live in the sidebar and an AppShell-level invitations surface). |
| `/rooms/:roomId` | `ChatPage`'s header loses the AppSidebar-provided chrome; right panel swapped to the restructured `RoomRightPanel`; single `Manage room` button replaces the separate Bans/Delete wiring. |
| `/dms` | Existing `DirectMessagesPage` becomes a landing view — "Pick a contact from the sidebar to start a direct message." |
| `/dms/:friendId` | Existing `DirectChatPage` stays; duplicate chrome trimmed (no own sidebar). |
| `/friends` | Existing `FriendsPage` stays; duplicate chrome trimmed. |
| `/sessions` | New `SessionsStub` page — "Active session management ships in Feature #7." |

### Database

No schema changes. Search uses existing tables.

### Tests

| Level | Coverage |
|---|---|
| Backend unit | `SearchServiceTest` — empty query, no matches, partial matches, excludes caller/member correctly, limit respected |
| Backend controller | `SearchControllerTest` — 401 without JWT, 200 with JWT, empty-query empty-arrays case |
| Frontend unit (vitest) | `SideTree.test.tsx` (renders sections, handles empty states), `SearchDropdown.test.tsx` (debounced query, result click navigates), `ManageRoomModal.test.tsx` (tab switching, owner-only Settings) |
| Frontend E2E (Playwright) | New `app-shell.spec.ts` — logged-in user sees top menu + sidebar + can reach each route; search suggests a public room and clicking navigates; Manage Room modal opens and switches tabs |

### Accessibility & responsive notes

- TopMenu is a `<nav>` with `aria-label="Primary"`; Profile dropdown uses `aria-expanded`/`aria-haspopup`.
- `SideTree` uses `<nav aria-label="Workspace">` and `<details>`/`<summary>` for collapsible sections (no custom keyboard handling needed).
- Narrow-viewport behavior is best-effort: below 768px the sidebar collapses to a hamburger (rendered button that toggles `SideTree` visibility). Right panel hides below 1024px. Full responsive polish is not a goal.

### File-path summary (anticipated)

```
frontend/src/layout/AppShell.tsx
frontend/src/layout/TopMenu.tsx
frontend/src/layout/ProfileMenu.tsx
frontend/src/layout/SideTree.tsx
frontend/src/layout/SideTreeRoomList.tsx
frontend/src/layout/SideTreeContactList.tsx
frontend/src/layout/SearchDropdown.tsx
frontend/src/layout/RightPanel.tsx
frontend/src/components/RoomMembersPanel.tsx        (restructured in place)
frontend/src/components/ManageRoomModal.tsx
frontend/src/components/ManageMembersTab.tsx
frontend/src/components/ManageInvitationsTab.tsx
frontend/src/components/ManageBannedTab.tsx
frontend/src/components/ManageSettingsTab.tsx
frontend/src/components/ComposerActions.tsx
frontend/src/pages/SessionsStub.tsx
frontend/src/services/searchService.ts
frontend/src/hooks/useSearch.ts
frontend/src/hooks/useSidebarData.ts

backend/src/main/java/com/hackathon/features/search/SearchController.java
backend/src/main/java/com/hackathon/features/search/SearchService.java
backend/src/test/java/com/hackathon/features/search/SearchServiceTest.java
backend/src/test/java/com/hackathon/features/search/SearchControllerTest.java
```

## Risk / deadline notes

- Deadline: 2026-04-20 12:00 UTC — ~38 hours of calendar time.
- The refactor is structural; the risk is cosmetic regressions in existing pages (friends, DMs) after their chrome is trimmed. Mitigation: E2E suite must pass before commit of each task; keep plan tasks narrow so each commit compiles/tests cleanly.
- If the timeline tightens mid-execution, fallback = land the shell (Tasks 1–6 above) and defer Manage Room modal tabbing (Tasks 7–8) to run later in the slot originally held for Feature #5's content work. The separate-modals state (`BanListPanel`, `DeleteRoomDialog`) still works and is a valid ship state.
