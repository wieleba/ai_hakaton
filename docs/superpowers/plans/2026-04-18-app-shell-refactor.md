# App Shell Refactor (Feature #5 execution) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the web client's layout in line with Appendix A of the requirements PDF (top menu + tree sidebar + right members panel + tabbed Manage Room modal) so subsequent features (#5 content, #6 attachments, #7 presence/sessions, #8 account) drop into their final home.

**Architecture:** New `AppShell` layout route wraps all authenticated routes; deletes current `AppSidebar`; new backend `/api/search` endpoint powers a sidebar search dropdown; right panel restructures in place; `ManageRoomModal` (tabbed) owns invitations/bans/settings moving them out of `ChatPage`.

**Tech Stack:** React 19, TypeScript, React Router 6, Vite, Tailwind; Spring Boot 3.5, Java 25, JPA/Hibernate, JUnit 5, MockMvc, Instancio; Playwright.

**Spec:** `docs/superpowers/specs/2026-04-18-app-shell-refactor-design.md`

---

## File Structure

### Backend (new)

```
backend/src/main/java/com/hackathon/features/search/
  SearchController.java
  SearchService.java

backend/src/test/java/com/hackathon/features/search/
  SearchServiceTest.java
  SearchControllerTest.java
```

### Backend (modified)

```
backend/src/main/java/com/hackathon/features/rooms/ChatRoomRepository.java
backend/src/main/java/com/hackathon/features/users/UserRepository.java
```

### Frontend (new)

```
frontend/src/layout/AppShell.tsx
frontend/src/layout/TopMenu.tsx
frontend/src/layout/ProfileMenu.tsx
frontend/src/layout/SideTree.tsx
frontend/src/layout/SideTreeRoomList.tsx
frontend/src/layout/SideTreeContactList.tsx
frontend/src/layout/SearchDropdown.tsx
frontend/src/layout/RightPanel.tsx
frontend/src/components/ManageRoomModal.tsx
frontend/src/components/ComposerActions.tsx
frontend/src/pages/SessionsStub.tsx
frontend/src/services/searchService.ts
frontend/src/types/search.ts
frontend/src/hooks/useSearch.ts
```

### Frontend (modified)

```
frontend/src/App.tsx                           (wire AppShell)
frontend/src/pages/RoomListPage.tsx            (drop My rooms tab)
frontend/src/pages/ChatPage.tsx                (trim chrome, use ManageRoomModal)
frontend/src/pages/DirectMessagesPage.tsx      (landing view)
frontend/src/pages/DirectChatPage.tsx          (trim chrome)
frontend/src/pages/FriendsPage.tsx             (trim chrome)
frontend/src/components/MessageInput.tsx       (ComposerActions slot)
frontend/src/components/RoomMembersPanel.tsx   (presence groups + admin buttons; drop row actions)
frontend/src/components/AppSidebar.tsx         (DELETE)
```

### Tests (new)

```
frontend/e2e/app-shell.spec.ts
```

---

## Implementation Tasks

### Task 1: Backend — Search endpoint (service + controller + tests, TDD)

**Files:**
- Modify: `backend/src/main/java/com/hackathon/features/rooms/ChatRoomRepository.java`
- Modify: `backend/src/main/java/com/hackathon/features/users/UserRepository.java`
- Create: `backend/src/main/java/com/hackathon/features/search/SearchService.java`
- Create: `backend/src/main/java/com/hackathon/features/search/SearchController.java`
- Create: `backend/src/test/java/com/hackathon/features/search/SearchServiceTest.java`
- Create: `backend/src/test/java/com/hackathon/features/search/SearchControllerTest.java`

- [ ] **Step 1: Add repository queries**

Add to `ChatRoomRepository`:

```java
  @org.springframework.data.jpa.repository.Query(
      "SELECT r FROM ChatRoom r "
          + "WHERE r.visibility = 'public' "
          + "AND LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "AND r.id NOT IN (SELECT m.roomId FROM RoomMember m WHERE m.userId = :callerId) "
          + "ORDER BY r.name")
  List<ChatRoom> searchPublicRoomsNotMember(String q, UUID callerId, Pageable pageable);
```

Add to `UserRepository`:

```java
  @org.springframework.data.jpa.repository.Query(
      "SELECT u FROM User u "
          + "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "AND u.id <> :callerId "
          + "ORDER BY u.username")
  java.util.List<User> searchUsersExcludingCaller(String q, UUID callerId, org.springframework.data.domain.Pageable pageable);
```

Add imports in each file (`java.util.List`, `java.util.UUID`, `org.springframework.data.domain.Pageable`).

- [ ] **Step 2: Write `SearchServiceTest`**

Create `backend/src/test/java/com/hackathon/features/search/SearchServiceTest.java`:

```java
package com.hackathon.features.search;

import static org.junit.jupiter.api.Assertions.*;

import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SearchServiceTest {
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired SearchService searchService;

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void emptyQueryReturnsEmptyLists() {
    User caller = register("caller");
    SearchService.SearchResult r = searchService.search("   ", caller.getId(), 5);
    assertTrue(r.rooms().isEmpty());
    assertTrue(r.users().isEmpty());
  }

  @Test
  void findsPublicRoomByPartialName() {
    User owner = register("a");
    User caller = register("b");
    chatRoomService.createRoom("engineering-" + System.nanoTime(), null, owner.getId(), "public");

    SearchService.SearchResult r = searchService.search("engin", caller.getId(), 5);
    assertEquals(1, r.rooms().size());
    assertTrue(r.rooms().get(0).getName().startsWith("engineering-"));
  }

  @Test
  void excludesRoomsCallerIsMemberOf() {
    User caller = register("caller");
    chatRoomService.createRoom("myroom-" + System.nanoTime(), null, caller.getId(), "public");

    SearchService.SearchResult r = searchService.search("myroom", caller.getId(), 5);
    assertTrue(r.rooms().isEmpty());
  }

  @Test
  void doesNotReturnPrivateRooms() {
    User owner = register("a");
    User caller = register("b");
    chatRoomService.createRoom("hidden-" + System.nanoTime(), null, owner.getId(), "private");

    SearchService.SearchResult r = searchService.search("hidden", caller.getId(), 5);
    assertTrue(r.rooms().isEmpty());
  }

  @Test
  void findsUserByPartialUsernameExcludingCaller() {
    User caller = register("caller");
    User other = register("target");

    SearchService.SearchResult r = searchService.search(other.getUsername().substring(0, 6), caller.getId(), 5);
    assertTrue(r.users().stream().anyMatch(u -> u.getId().equals(other.getId())));
    assertTrue(r.users().stream().noneMatch(u -> u.getId().equals(caller.getId())));
  }

  @Test
  void respectsLimit() {
    User caller = register("caller");
    for (int i = 0; i < 7; i++) {
      register("bulk" + i);
    }
    SearchService.SearchResult r = searchService.search("userbulk", caller.getId(), 3);
    assertTrue(r.users().size() <= 3);
  }
}
```

- [ ] **Step 3: Run the test — expect compile failure (SearchService missing)**

Run: `cd /src/ai_hakaton/backend && ./gradlew compileTestJava`
Expected: FAIL — `SearchService` cannot be resolved.

- [ ] **Step 4: Implement `SearchService`**

Create `backend/src/main/java/com/hackathon/features/search/SearchService.java`:

```java
package com.hackathon.features.search;

import com.hackathon.features.rooms.ChatRoom;
import com.hackathon.features.rooms.ChatRoomRepository;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {
  private static final int DEFAULT_LIMIT = 5;
  private static final int MAX_LIMIT = 10;

  private final ChatRoomRepository chatRoomRepository;
  private final UserRepository userRepository;

  public record SearchResult(List<ChatRoom> rooms, List<User> users) {}

  public SearchResult search(String query, UUID callerId, int limit) {
    String q = query == null ? "" : query.trim();
    if (q.isEmpty()) {
      return new SearchResult(List.of(), List.of());
    }
    int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
    var page = PageRequest.of(0, capped);
    List<ChatRoom> rooms = chatRoomRepository.searchPublicRoomsNotMember(q, callerId, page);
    List<User> users = userRepository.searchUsersExcludingCaller(q, callerId, page);
    return new SearchResult(rooms, users);
  }
}
```

- [ ] **Step 5: Run the service tests**

Run: `cd /src/ai_hakaton/backend && ./gradlew test --tests 'SearchServiceTest'`
Expected: PASS (6 tests).

- [ ] **Step 6: Write `SearchControllerTest`**

Create `backend/src/test/java/com/hackathon/features/search/SearchControllerTest.java`:

```java
package com.hackathon.features.search;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackathon.features.rooms.ChatRoomService;
import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import com.hackathon.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserService userService;
  @Autowired ChatRoomService chatRoomService;
  @Autowired JwtTokenProvider jwtTokenProvider;

  private String tokenFor(User user) {
    return jwtTokenProvider.generateToken(user.getId(), user.getUsername());
  }

  private User register(String suffix) {
    long t = System.nanoTime();
    return userService.registerUser(
        "u" + t + "-" + suffix + "@example.com",
        "user" + t + suffix,
        "password12345");
  }

  @Test
  void rejectsWithoutJwt() throws Exception {
    mvc.perform(get("/api/search").param("q", "anything")).andExpect(status().isUnauthorized());
  }

  @Test
  void emptyQueryReturnsEmptyArrays() throws Exception {
    User caller = register("caller");
    mvc.perform(get("/api/search").param("q", "   ").header("Authorization", "Bearer " + tokenFor(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms").isArray())
        .andExpect(jsonPath("$.users").isArray())
        .andExpect(jsonPath("$.rooms.length()").value(0))
        .andExpect(jsonPath("$.users.length()").value(0));
  }

  @Test
  void returnsRoomsAndUsersMatchingQuery() throws Exception {
    User caller = register("caller");
    User other = register("pickme");
    chatRoomService.createRoom("findme-" + System.nanoTime(), null, other.getId(), "public");

    String token = tokenFor(caller);
    mvc.perform(get("/api/search").param("q", "findme").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms.length()").value(greaterThanOrEqualTo(1)));

    mvc.perform(get("/api/search").param("q", "pickme").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.users.length()").value(greaterThanOrEqualTo(1)));
  }
}
```

- [ ] **Step 7: Run — expect compile failure (controller missing)**

Run: `./gradlew compileTestJava`
Expected: FAIL — `SearchController` missing.

- [ ] **Step 8: Implement `SearchController`**

Create `backend/src/main/java/com/hackathon/features/search/SearchController.java`:

```java
package com.hackathon.features.search;

import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
  private final SearchService searchService;
  private final UserService userService;

  record RoomHit(UUID id, String name, String description, String visibility) {}

  record UserHit(UUID id, String username) {}

  record SearchResponse(List<RoomHit> rooms, List<UserHit> users) {}

  private UUID currentUserId(Authentication authentication) {
    Object details = authentication.getDetails();
    if (details instanceof UUID uuid) return uuid;
    return userService.getUserByUsername(authentication.getName()).getId();
  }

  @GetMapping
  public ResponseEntity<SearchResponse> search(
      @RequestParam("q") String q,
      @RequestParam(name = "limit", defaultValue = "5") int limit,
      Authentication authentication) {
    var result = searchService.search(q, currentUserId(authentication), limit);
    List<RoomHit> rooms =
        result.rooms().stream()
            .map(r -> new RoomHit(r.getId(), r.getName(), r.getDescription(), r.getVisibility()))
            .toList();
    List<UserHit> users =
        result.users().stream().map(u -> new UserHit(u.getId(), u.getUsername())).toList();
    return ResponseEntity.ok(new SearchResponse(rooms, users));
  }
}
```

- [ ] **Step 9: Update security config if needed**

Verify: `/api/search` must require JWT. Check `backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java`. If the config uses `.anyRequest().authenticated()` pattern, no change needed. Otherwise add the path.

Run: `grep -n 'anyRequest' backend/src/main/java/com/hackathon/shared/security/SecurityConfig.java` — if `anyRequest().authenticated()` is present, no change required.

- [ ] **Step 10: Run the controller tests**

Run: `./gradlew test --tests 'SearchControllerTest'`
Expected: PASS (3 tests).

- [ ] **Step 11: Run the full backend test suite**

Run: `./gradlew test`
Expected: full suite green.

- [ ] **Step 12: Commit**

```bash
cd /src/ai_hakaton
git add backend/src/main/java/com/hackathon/features/search backend/src/test/java/com/hackathon/features/search \
        backend/src/main/java/com/hackathon/features/rooms/ChatRoomRepository.java \
        backend/src/main/java/com/hackathon/features/users/UserRepository.java
git commit -m "feat(search): /api/search endpoint over public rooms + users" -m "- JPA-level ILIKE with caller exclusion (own rooms, self) + limit cap" -m "- Service test via SpringBootTest (Instancio-free, uses real services)" -m "- Controller test via MockMvc with JWT" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Frontend — search service, types, hook

**Files:**
- Create: `frontend/src/types/search.ts`
- Create: `frontend/src/services/searchService.ts`
- Create: `frontend/src/hooks/useSearch.ts`

- [ ] **Step 1: Create `frontend/src/types/search.ts`**

```typescript
export interface RoomHit {
  id: string;
  name: string;
  description: string | null;
  visibility: 'public' | 'private';
}

export interface UserHit {
  id: string;
  username: string;
}

export interface SearchResponse {
  rooms: RoomHit[];
  users: UserHit[];
}
```

- [ ] **Step 2: Create `frontend/src/services/searchService.ts`**

```typescript
import axios from 'axios';
import type { SearchResponse } from '../types/search';

export const searchService = {
  async search(query: string, limit = 5): Promise<SearchResponse> {
    const q = query.trim();
    if (!q) return { rooms: [], users: [] };
    const params = new URLSearchParams({ q, limit: String(limit) });
    return (await axios.get(`/api/search?${params.toString()}`)).data;
  },
};
```

- [ ] **Step 3: Create `frontend/src/hooks/useSearch.ts`**

```typescript
import { useEffect, useState } from 'react';
import { searchService } from '../services/searchService';
import type { SearchResponse } from '../types/search';

const EMPTY: SearchResponse = { rooms: [], users: [] };
const DEBOUNCE_MS = 200;

export function useSearch(query: string) {
  const [results, setResults] = useState<SearchResponse>(EMPTY);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setResults(EMPTY);
      setIsLoading(false);
      setError(null);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    const handle = setTimeout(async () => {
      try {
        const r = await searchService.search(trimmed);
        if (!cancelled) {
          setResults(r);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
  }, [query]);

  return { results, isLoading, error };
}
```

- [ ] **Step 4: Build**

Run: `cd /src/ai_hakaton/frontend && npm run build`
Expected: clean.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/types/search.ts frontend/src/services/searchService.ts frontend/src/hooks/useSearch.ts
git commit -m "feat(frontend): search service + types + useSearch hook" -m "Thin wrapper over /api/search with 200ms debounced useSearch hook." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Frontend — AppShell, TopMenu, ProfileMenu scaffolds (not yet wired)

**Files:**
- Create: `frontend/src/layout/AppShell.tsx`
- Create: `frontend/src/layout/TopMenu.tsx`
- Create: `frontend/src/layout/ProfileMenu.tsx`
- Create: `frontend/src/layout/RightPanel.tsx`
- Create: `frontend/src/pages/SessionsStub.tsx`

- [ ] **Step 1: Create `frontend/src/layout/ProfileMenu.tsx`**

```tsx
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

interface Props {
  username: string;
}

export const ProfileMenu: React.FC<Props> = ({ username }) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const signOut = () => {
    localStorage.removeItem('authToken');
    navigate('/login', { replace: true });
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-1 px-3 py-2 rounded hover:bg-gray-100"
      >
        {username} <span aria-hidden>▼</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 mt-1 w-40 bg-white border rounded shadow z-50">
          <div className="px-3 py-2 text-xs text-gray-500 border-b">Signed in as</div>
          <div className="px-3 py-2 text-sm truncate">{username}</div>
          <button
            onClick={signOut}
            className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50"
            role="menuitem"
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
};
```

- [ ] **Step 2: Create `frontend/src/layout/TopMenu.tsx`**

```tsx
import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ProfileMenu } from './ProfileMenu';

interface Props {
  username: string;
}

export const TopMenu: React.FC<Props> = ({ username }) => {
  const { pathname } = useLocation();
  const linkCls = (to: string) => {
    const active = pathname === to || pathname.startsWith(to + '/');
    return `px-3 py-2 rounded ${active ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100'}`;
  };
  return (
    <nav
      aria-label="Primary"
      className="flex items-center gap-2 px-4 py-2 border-b bg-white shadow-sm"
    >
      <Link to="/rooms" className="font-bold text-lg mr-4">
        Chat
      </Link>
      <Link to="/rooms" className={linkCls('/rooms')}>
        Public Rooms
      </Link>
      <Link to="/friends" className={linkCls('/friends')}>
        Contacts
      </Link>
      <Link to="/sessions" className={linkCls('/sessions')}>
        Sessions
      </Link>
      <div className="flex-1" />
      <ProfileMenu username={username} />
    </nav>
  );
};
```

- [ ] **Step 3: Create `frontend/src/layout/RightPanel.tsx`**

```tsx
import React from 'react';
import { useMatch } from 'react-router-dom';
import { RoomMembersPanel } from '../components/RoomMembersPanel';

const getCurrentUserId = (): string | null => {
  const token = localStorage.getItem('authToken');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.sub === 'string' ? payload.sub : null;
  } catch {
    return null;
  }
};

export const RightPanel: React.FC = () => {
  const roomMatch = useMatch('/rooms/:roomId');
  const roomId = roomMatch?.params.roomId;
  const currentUserId = getCurrentUserId();

  if (roomId && currentUserId) {
    return <RoomMembersPanel roomId={roomId} currentUserId={currentUserId} />;
  }
  return null;
};
```

- [ ] **Step 4: Create `frontend/src/pages/SessionsStub.tsx`**

```tsx
import React from 'react';

export const SessionsStub: React.FC = () => {
  return (
    <div className="p-8 max-w-2xl">
      <h1 className="text-2xl font-bold mb-2">Sessions</h1>
      <p className="text-gray-600">
        Active session management ships in Feature #7.
      </p>
    </div>
  );
};
```

- [ ] **Step 5: Create `frontend/src/layout/AppShell.tsx` (SideTree wired in a later task; stub for now)**

```tsx
import React from 'react';
import { Outlet } from 'react-router-dom';
import { TopMenu } from './TopMenu';
import { RightPanel } from './RightPanel';

const getUsername = (): string => {
  const token = localStorage.getItem('authToken');
  if (!token) return 'User';
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.username === 'string' ? payload.username : 'User';
  } catch {
    return 'User';
  }
};

export const AppShell: React.FC = () => {
  const username = getUsername();
  return (
    <div className="flex flex-col h-screen">
      <TopMenu username={username} />
      <div className="flex flex-1 min-h-0">
        <aside className="w-64 border-r bg-white overflow-y-auto" aria-label="Workspace">
          {/* SideTree goes here in Task 4 */}
          <div className="p-4 text-sm text-gray-400">Sidebar coming…</div>
        </aside>
        <main className="flex-1 min-w-0 overflow-hidden">
          <Outlet />
        </main>
        <RightPanel />
      </div>
    </div>
  );
};
```

- [ ] **Step 6: Build**

Run: `cd /src/ai_hakaton/frontend && npm run build`
Expected: clean (these files are not yet referenced by routes, so they build unused).

- [ ] **Step 7: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/layout/AppShell.tsx frontend/src/layout/TopMenu.tsx \
        frontend/src/layout/ProfileMenu.tsx frontend/src/layout/RightPanel.tsx \
        frontend/src/pages/SessionsStub.tsx
git commit -m "feat(frontend): AppShell + TopMenu + ProfileMenu + RightPanel + SessionsStub scaffolds" -m "New layout primitives created but not yet wired into routes (the route flip happens in a later task). Compiles cleanly as dead code." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Frontend — SideTree and sub-components (not yet wired)

**Files:**
- Create: `frontend/src/layout/SideTreeRoomList.tsx`
- Create: `frontend/src/layout/SideTreeContactList.tsx`
- Create: `frontend/src/layout/SideTree.tsx`

- [ ] **Step 1: Create `frontend/src/layout/SideTreeRoomList.tsx`**

```tsx
import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { ChatRoom } from '../types/room';

interface Props {
  title: string;
  rooms: ChatRoom[];
  emptyHint: string;
}

export const SideTreeRoomList: React.FC<Props> = ({ title, rooms, emptyHint }) => {
  const { pathname } = useLocation();
  return (
    <details open className="px-2 py-1">
      <summary className="cursor-pointer text-xs font-semibold uppercase text-gray-500 py-1">
        {title}
      </summary>
      {rooms.length === 0 ? (
        <p className="pl-4 pr-2 text-xs text-gray-400 italic py-1">{emptyHint}</p>
      ) : (
        <ul>
          {rooms.map((r) => {
            const to = `/rooms/${r.id}`;
            const active = pathname === to;
            return (
              <li key={r.id}>
                <Link
                  to={to}
                  className={`block pl-4 pr-2 py-1 text-sm truncate rounded ${
                    active ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100'
                  }`}
                >
                  # {r.name}{' '}
                  <span className="text-xs text-gray-400" aria-label="unread count">
                    (0)
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </details>
  );
};
```

- [ ] **Step 2: Create `frontend/src/layout/SideTreeContactList.tsx`**

```tsx
import React from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import type { FriendView } from '../types/friendship';
import { directMessageService } from '../services/directMessageService';

interface Props {
  friends: FriendView[];
}

export const SideTreeContactList: React.FC<Props> = ({ friends }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const openDm = async (userId: string) => {
    try {
      const conv = await directMessageService.getOrCreateWith(userId);
      navigate(`/dms/${conv.id}`);
    } catch (e) {
      console.error('Failed to open DM', e);
    }
  };

  return (
    <details open className="px-2 py-1">
      <summary className="cursor-pointer text-xs font-semibold uppercase text-gray-500 py-1">
        Contacts
      </summary>
      {friends.length === 0 ? (
        <p className="pl-4 pr-2 text-xs text-gray-400 italic py-1">No friends yet</p>
      ) : (
        <ul>
          {friends.map((f) => {
            const dmPath = pathname.startsWith('/dms/') ? pathname : '';
            return (
              <li key={f.userId}>
                <Link
                  to={dmPath}
                  onClick={(e) => {
                    e.preventDefault();
                    openDm(f.userId);
                  }}
                  className="flex items-center justify-between pl-4 pr-2 py-1 text-sm hover:bg-gray-100 rounded"
                >
                  <span className="truncate">
                    <span className="text-gray-400 mr-1" aria-label="offline">
                      ○
                    </span>
                    {f.username}
                  </span>
                  <span className="text-xs text-gray-400" aria-label="unread count">
                    (0)
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </details>
  );
};
```

- [ ] **Step 3: Create `frontend/src/layout/SideTree.tsx`**

```tsx
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { roomService } from '../services/roomService';
import { friendshipService } from '../services/friendshipService';
import type { ChatRoom } from '../types/room';
import type { FriendView } from '../types/friendship';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { SideTreeRoomList } from './SideTreeRoomList';
import { SideTreeContactList } from './SideTreeContactList';

export const SideTree: React.FC = () => {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [friends, setFriends] = useState<FriendView[]>([]);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { pathname } = useLocation();

  const reloadRooms = useCallback(async () => {
    try {
      setRooms(await roomService.listMyRooms());
    } catch (e) {
      console.error('Failed to load sidebar rooms', e);
    }
  }, []);

  const reloadFriends = useCallback(async () => {
    try {
      setFriends(await friendshipService.listFriends());
    } catch (e) {
      console.error('Failed to load contacts', e);
    }
  }, []);

  useEffect(() => {
    reloadRooms();
    reloadFriends();
  }, [reloadRooms, reloadFriends, pathname]);

  const publicRooms = useMemo(() => rooms.filter((r) => r.visibility === 'public'), [rooms]);
  const privateRooms = useMemo(() => rooms.filter((r) => r.visibility === 'private'), [rooms]);

  const handleCreateRoom = async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ) => {
    await roomService.createRoom(name, description, visibility);
    await reloadRooms();
  };

  return (
    <div className="h-full flex flex-col">
      <div className="p-2 border-b">
        <div className="text-xs text-gray-400">Search coming…</div>
      </div>
      <div className="flex-1 overflow-y-auto">
        <div className="py-1">
          <div className="px-2 pt-2 pb-1 text-xs font-bold uppercase text-gray-600">Rooms</div>
          <SideTreeRoomList
            title="Public"
            rooms={publicRooms}
            emptyHint="No public rooms joined"
          />
          <SideTreeRoomList
            title="Private"
            rooms={privateRooms}
            emptyHint="No private rooms"
          />
        </div>
        <div className="py-1 border-t">
          <SideTreeContactList friends={friends} />
        </div>
      </div>
      <div className="p-2 border-t">
        <button
          onClick={() => setIsCreateOpen(true)}
          className="w-full text-sm px-3 py-2 border rounded hover:bg-gray-100"
        >
          + Create room
        </button>
      </div>
      <RoomCreateModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreateRoom}
      />
    </div>
  );
};
```

- [ ] **Step 4: Wire SideTree into AppShell**

Modify `frontend/src/layout/AppShell.tsx` — replace the placeholder `<div>Sidebar coming…</div>` with `<SideTree />`:

```tsx
import React from 'react';
import { Outlet } from 'react-router-dom';
import { TopMenu } from './TopMenu';
import { RightPanel } from './RightPanel';
import { SideTree } from './SideTree';

const getUsername = (): string => {
  const token = localStorage.getItem('authToken');
  if (!token) return 'User';
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.username === 'string' ? payload.username : 'User';
  } catch {
    return 'User';
  }
};

export const AppShell: React.FC = () => {
  const username = getUsername();
  return (
    <div className="flex flex-col h-screen">
      <TopMenu username={username} />
      <div className="flex flex-1 min-h-0">
        <aside className="w-64 border-r bg-white overflow-y-auto" aria-label="Workspace">
          <SideTree />
        </aside>
        <main className="flex-1 min-w-0 overflow-hidden">
          <Outlet />
        </main>
        <RightPanel />
      </div>
    </div>
  );
};
```

- [ ] **Step 5: Build**

Run: `cd /src/ai_hakaton/frontend && npm run build`
Expected: clean.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/layout/SideTree.tsx frontend/src/layout/SideTreeRoomList.tsx \
        frontend/src/layout/SideTreeContactList.tsx frontend/src/layout/AppShell.tsx
git commit -m "feat(frontend): SideTree with Rooms + Contacts + Create Room" -m "Rooms grouped by visibility using roomService.listMyRooms(); contacts from friendshipService. Clicking a contact resolves to the DirectConversation via directMessageService.getOrCreateWith and navigates. Unread + presence are placeholders until Feature #7. Create-Room modal wired in." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Frontend — SearchDropdown inside SideTree

**Files:**
- Create: `frontend/src/layout/SearchDropdown.tsx`
- Modify: `frontend/src/layout/SideTree.tsx`

- [ ] **Step 1: Create `frontend/src/layout/SearchDropdown.tsx`**

```tsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSearch } from '../hooks/useSearch';
import { roomService } from '../services/roomService';
import { directMessageService } from '../services/directMessageService';

export const SearchDropdown: React.FC = () => {
  const [query, setQuery] = useState('');
  const [focused, setFocused] = useState(false);
  const { results, isLoading } = useSearch(query);
  const navigate = useNavigate();

  const reset = () => {
    setQuery('');
    setFocused(false);
  };

  const openRoom = async (id: string) => {
    try {
      await roomService.joinRoom(id);
    } catch {
      /* already member / banned — navigate anyway; ChatPage handles errors */
    }
    navigate(`/rooms/${id}`);
    reset();
  };

  const openUser = async (userId: string) => {
    try {
      const conv = await directMessageService.getOrCreateWith(userId);
      navigate(`/dms/${conv.id}`);
    } catch (e) {
      console.error('Failed to open DM', e);
    }
    reset();
  };

  const show = focused && query.trim().length > 0;

  return (
    <div className="relative">
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 150)}
        placeholder="Search rooms or users…"
        className="w-full text-sm border rounded px-2 py-1"
      />
      {show && (
        <div className="absolute left-0 right-0 mt-1 bg-white border rounded shadow z-50 max-h-80 overflow-y-auto">
          {isLoading && (
            <div className="px-3 py-2 text-xs text-gray-400">Searching…</div>
          )}
          {!isLoading && results.rooms.length === 0 && results.users.length === 0 && (
            <div className="px-3 py-2 text-xs text-gray-400">No matches</div>
          )}
          {results.rooms.length > 0 && (
            <div>
              <div className="px-3 py-1 text-xs font-semibold uppercase text-gray-500 bg-gray-50">
                Rooms
              </div>
              <ul>
                {results.rooms.map((r) => (
                  <li key={r.id}>
                    <button
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => openRoom(r.id)}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100"
                    >
                      # {r.name}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {results.users.length > 0 && (
            <div>
              <div className="px-3 py-1 text-xs font-semibold uppercase text-gray-500 bg-gray-50">
                Users
              </div>
              <ul>
                {results.users.map((u) => (
                  <li key={u.id}>
                    <button
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => openUser(u.id)}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100"
                    >
                      {u.username}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
```

- [ ] **Step 2: Replace the placeholder in `SideTree.tsx`**

In `frontend/src/layout/SideTree.tsx`, replace the placeholder block:

```tsx
      <div className="p-2 border-b">
        <div className="text-xs text-gray-400">Search coming…</div>
      </div>
```

with:

```tsx
      <div className="p-2 border-b">
        <SearchDropdown />
      </div>
```

And add at the top:

```tsx
import { SearchDropdown } from './SearchDropdown';
```

- [ ] **Step 3: Build**

Run: `cd /src/ai_hakaton/frontend && npm run build`
Expected: clean.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/layout/SearchDropdown.tsx frontend/src/layout/SideTree.tsx
git commit -m "feat(frontend): SearchDropdown in SideTree wired to /api/search" -m "Clicking a room joins + navigates; clicking a user opens DM via getOrCreateWith. Uses debounced useSearch hook." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Frontend — route flip (App.tsx) + delete AppSidebar + trim pages

This is the big structural commit. Every change is in this single commit so each commit compiles.

**Files:**
- Modify: `frontend/src/App.tsx`
- Delete: `frontend/src/components/AppSidebar.tsx`
- Modify: `frontend/src/pages/DirectMessagesPage.tsx`
- Modify: `frontend/src/pages/DirectChatPage.tsx` (minor chrome trim if any)
- Modify: `frontend/src/pages/FriendsPage.tsx` (minor chrome trim if any)
- Modify: `frontend/src/pages/RoomListPage.tsx` (drop My rooms tab)
- Modify: `frontend/src/pages/ChatPage.tsx` (remove duplicate right panel rendering — RightPanel now provides it)

- [ ] **Step 1: Update `frontend/src/App.tsx` to use AppShell layout route**

Replace entire file contents with:

```tsx
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import AuthGuard from './components/AuthGuard';
import { AppShell } from './layout/AppShell';
import { RoomListPage } from './pages/RoomListPage';
import { ChatPage } from './pages/ChatPage';
import { FriendsPage } from './pages/FriendsPage';
import { DirectMessagesPage } from './pages/DirectMessagesPage';
import { DirectChatPage } from './pages/DirectChatPage';
import { SessionsStub } from './pages/SessionsStub';

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-100">
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<Navigate to="/rooms" replace />} />
          <Route
            element={
              <AuthGuard>
                <AppShell />
              </AuthGuard>
            }
          >
            <Route path="/rooms" element={<RoomListPage />} />
            <Route path="/rooms/:roomId" element={<ChatPage />} />
            <Route path="/friends" element={<FriendsPage />} />
            <Route path="/dms" element={<DirectMessagesPage />} />
            <Route path="/dms/:conversationId" element={<DirectChatPage />} />
            <Route path="/sessions" element={<SessionsStub />} />
            <Route path="/" element={<Navigate to="/rooms" replace />} />
          </Route>
        </Routes>
      </div>
    </Router>
  );
}
```

- [ ] **Step 2: Delete `frontend/src/components/AppSidebar.tsx`**

Run: `rm /src/ai_hakaton/frontend/src/components/AppSidebar.tsx`

- [ ] **Step 3: Downgrade `frontend/src/pages/DirectMessagesPage.tsx` to a landing view**

Replace entire file contents with:

```tsx
import React from 'react';

export const DirectMessagesPage: React.FC = () => {
  return (
    <div className="p-8 max-w-2xl">
      <h1 className="text-2xl font-bold mb-2">Direct Messages</h1>
      <p className="text-gray-600">
        Pick a contact from the sidebar to start a direct message.
      </p>
    </div>
  );
};
```

- [ ] **Step 4: Trim `ChatPage.tsx` — remove the inline RoomMembersPanel (RightPanel now renders it)**

Modify `frontend/src/pages/ChatPage.tsx`. Remove these lines:

```tsx
        {roomId && currentUserId && (
          <RoomMembersPanel
            roomId={roomId}
            currentUserId={currentUserId}
            roomVisibility={currentRoom?.visibility as 'public' | 'private' | undefined}
            onOpenBans={() => setBansOpen(true)}
          />
        )}
```

and remove the `import { RoomMembersPanel }` line.

Also: since the members panel is now rendered by the AppShell's `RightPanel`, the flex containing `{ flex flex-1 min-h-0 max-w-6xl mx-auto w-full }` should shrink to main-pane-only. Replace the outer `<div className="flex flex-1 min-h-0 max-w-6xl mx-auto w-full">` block and its inner content with:

```tsx
      <div className="flex flex-1 min-h-0 w-full">
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          <MessageList
            messages={messages}
            isLoading={false}
            hasMore={true}
            onLoadMore={loadMoreMessages}
          />
          <MessageInput onSend={handleSendMessage} disabled={!isConnected} />
        </div>
      </div>
```

Leave the BanListPanel / DeleteRoomDialog wiring untouched for now — Task 9 replaces them with ManageRoomModal.

- [ ] **Step 5: Inspect `FriendsPage.tsx` and `DirectChatPage.tsx` for duplicate chrome**

Run: `grep -n 'max-w-\|max-h-screen\|h-screen' frontend/src/pages/FriendsPage.tsx frontend/src/pages/DirectChatPage.tsx`

FriendsPage typically has its own `<div className="max-w-...">` wrapper — leave it (it's content-max-width styling, not chrome). No change required unless they reference the deleted `AppSidebar`.

- [ ] **Step 6: Build**

Run: `cd /src/ai_hakaton/frontend && npm run build`
Expected: clean. If TypeScript complains about unused `RoomMembersPanel` import in `ChatPage.tsx`, confirm it's removed.

- [ ] **Step 7: Run vitest**

Run: `cd /src/ai_hakaton/frontend && npm test -- --run`
Expected: all green. (If any test imports `AppSidebar`, replace with an `AppShell` wrapper or remove the assertion — inspect output and fix narrowly.)

- [ ] **Step 8: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/App.tsx frontend/src/pages/DirectMessagesPage.tsx \
        frontend/src/pages/ChatPage.tsx
git rm frontend/src/components/AppSidebar.tsx
git commit -m "refactor(frontend): switch to AppShell layout route; delete AppSidebar" -m "- All authenticated routes nest under AppShell (TopMenu + SideTree + Outlet + RightPanel)" -m "- DirectMessagesPage becomes a landing view (sidebar Contacts owns the inbox)" -m "- ChatPage drops its inline RoomMembersPanel rendering; AppShell's RightPanel provides it" -m "- AppSidebar removed" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Frontend — restructure `RoomMembersPanel` (presence groups + admin buttons; drop row-level moderation actions)

**Files:**
- Modify: `frontend/src/components/RoomMembersPanel.tsx`

The Manage Room modal built in Task 8 will own the Kick/Promote/Demote controls. This task removes them from the panel and adds the presence groupings + bottom admin buttons. The `onOpenBans` prop is replaced with `onOpenManage` in the new contract; `ChatPage` is rewired in Task 9.

- [ ] **Step 1: Look up the current ChatRoom type to confirm visibility pill logic**

Run: `grep -n visibility frontend/src/types/room.ts`

Confirm `visibility: 'public' | 'private'`.

- [ ] **Step 2: Fetch room metadata inside the panel**

The panel now needs the room's visibility and name independently (since `ChatPage` no longer passes them). Add a call to `roomService.getRoomById(roomId)`.

- [ ] **Step 3: Replace `RoomMembersPanel.tsx` fully**

Write `frontend/src/components/RoomMembersPanel.tsx`:

```tsx
import React, { useEffect, useState, useCallback } from 'react';
import { friendshipService } from '../services/friendshipService';
import { roomService } from '../services/roomService';
import type { ChatRoom } from '../types/room';
import type { RoomMemberView } from '../types/roomModeration';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { InviteUserModal } from './InviteUserModal';
import { ManageRoomModal } from './ManageRoomModal';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';

interface Props {
  roomId: string;
  currentUserId: string;
}

export const RoomMembersPanel: React.FC<Props> = ({ roomId, currentUserId }) => {
  const { members, isAdmin, reload } = useRoomMembersWithRole(roomId, currentUserId);
  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [friendIds, setFriendIds] = useState<Set<string>>(new Set());
  const [isInviteOpen, setInviteOpen] = useState(false);
  const [isManageOpen, setManageOpen] = useState(false);
  const { invite } = useRoomAdminActions(roomId);

  useEffect(() => {
    roomService
      .getRoomById(roomId)
      .then(setRoom)
      .catch((e) => console.error('Failed to load room', e));
  }, [roomId]);

  useEffect(() => {
    friendshipService
      .listFriends()
      .then((fs) => setFriendIds(new Set(fs.map((f) => f.userId))));
  }, []);

  const sendRequest = useCallback(async (username: string, userId: string) => {
    try {
      await friendshipService.sendRequest(username);
      setFriendIds((prev) => new Set([...prev, userId]));
    } catch (e) {
      console.error('Friend request failed', e);
    }
  }, []);

  const doInvite = async (username: string) => {
    await invite(username);
  };

  const roleBadge = (m: RoomMemberView) => {
    if (m.isOwner)
      return (
        <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">owner</span>
      );
    if (m.role === 'admin')
      return <span className="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded">admin</span>;
    return null;
  };

  // Presence groupings — all members are Offline until Feature #7 lights them up.
  const online: RoomMemberView[] = [];
  const afk: RoomMemberView[] = [];
  const offline: RoomMemberView[] = members;

  const renderGroup = (title: string, dot: string, list: RoomMemberView[]) => (
    <div>
      <div className="text-xs font-semibold text-gray-500 uppercase mt-3 mb-1">
        {title} ({list.length})
      </div>
      <ul className="space-y-1">
        {list.map((m) => {
          const isMe = m.userId === currentUserId;
          const isFriend = friendIds.has(m.userId);
          return (
            <li key={m.userId} className="flex justify-between items-center text-sm">
              <span className="flex items-center gap-1 truncate">
                <span className="text-gray-400">{dot}</span>
                <span className="truncate">
                  {m.username}
                  {isMe && ' (you)'}
                </span>
                {roleBadge(m)}
              </span>
              {!isMe && !isFriend && (
                <button
                  onClick={() => sendRequest(m.username, m.userId)}
                  className="text-xs px-2 py-1 border rounded hover:bg-blue-50"
                >
                  Add friend
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );

  return (
    <aside className="w-72 border-l bg-white p-4 overflow-y-auto">
      <div>
        <h3 className="font-semibold text-lg truncate">{room?.name ?? 'Loading…'}</h3>
        {room && (
          <span
            className={`text-xs px-2 py-0.5 rounded ${
              room.visibility === 'private'
                ? 'bg-purple-100 text-purple-700'
                : 'bg-green-100 text-green-700'
            }`}
          >
            {room.visibility}
          </span>
        )}
      </div>

      {renderGroup('Online', '●', online)}
      {renderGroup('AFK', '◐', afk)}
      {renderGroup('Offline', '○', offline)}

      {isAdmin && (
        <div className="mt-4 pt-4 border-t space-y-2">
          {room?.visibility === 'private' && (
            <button
              onClick={() => setInviteOpen(true)}
              className="w-full px-3 py-2 border rounded hover:bg-blue-50 text-sm"
            >
              Invite user
            </button>
          )}
          <button
            onClick={() => setManageOpen(true)}
            className="w-full px-3 py-2 border rounded hover:bg-blue-50 text-sm"
          >
            Manage room
          </button>
        </div>
      )}

      <InviteUserModal
        isOpen={isInviteOpen}
        onClose={() => setInviteOpen(false)}
        onInvite={doInvite}
      />
      <ManageRoomModal
        isOpen={isManageOpen}
        onClose={() => {
          setManageOpen(false);
          reload();
        }}
        roomId={roomId}
        currentUserId={currentUserId}
        room={room}
      />
    </aside>
  );
};
```

- [ ] **Step 4: Temporary `ManageRoomModal` stub so build stays green**

The real modal lands in Task 8. Create a minimal stub now so this task's commit compiles:

Create `frontend/src/components/ManageRoomModal.tsx`:

```tsx
import React from 'react';
import type { ChatRoom } from '../types/room';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
  currentUserId: string;
  room: ChatRoom | null;
}

// Real tabs ship in Task 8.
export const ManageRoomModal: React.FC<Props> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-96">
        <h2 className="text-xl font-bold mb-2">Manage room</h2>
        <p className="text-sm text-gray-600 mb-4">Tabs ship in the next commit.</p>
        <div className="flex justify-end">
          <button onClick={onClose} className="px-4 py-2 border rounded hover:bg-gray-100">
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 5: Build + tests**

Run: `cd /src/ai_hakaton/frontend && npm run build && npm test -- --run`
Expected: clean + vitest green.

- [ ] **Step 6: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/components/RoomMembersPanel.tsx frontend/src/components/ManageRoomModal.tsx
git commit -m "feat(frontend): RoomMembersPanel — presence groupings + admin buttons" -m "Members split into Online/AFK/Offline groups (all Offline until Feature #7). Per-row Kick/Promote/Demote removed — moves to Manage Room modal. Invite + Manage room buttons at bottom when admin. Fetches room info locally now that ChatPage no longer passes it." -m "ManageRoomModal is a stub for this commit; tabs ship next." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Frontend — full `ManageRoomModal` with all four tabs

**Files:**
- Modify: `frontend/src/components/ManageRoomModal.tsx`

Admin tabs use existing `BanListPanel` and `DeleteRoomDialog` bodies inline. No extract into separate tab files — keeps this diff reviewable.

- [ ] **Step 1: Replace `frontend/src/components/ManageRoomModal.tsx`**

```tsx
import React, { useCallback, useEffect, useState } from 'react';
import type { ChatRoom } from '../types/room';
import type { RoomBan, RoomInvitation, RoomMemberView } from '../types/roomModeration';
import { roomService } from '../services/roomService';
import { roomInvitationService } from '../services/roomInvitationService';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';

type Tab = 'members' | 'invitations' | 'banned' | 'settings';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
  currentUserId: string;
  room: ChatRoom | null;
}

export const ManageRoomModal: React.FC<Props> = ({
  isOpen,
  onClose,
  roomId,
  currentUserId,
  room,
}) => {
  const [tab, setTab] = useState<Tab>('members');
  const { members, isOwner, reload: reloadMembers } = useRoomMembersWithRole(
    roomId,
    currentUserId,
  );
  const { kick, promote, demote, unban, deleteRoom } = useRoomAdminActions(roomId);

  const [invitations, setInvitations] = useState<RoomInvitation[]>([]);
  const [bans, setBans] = useState<RoomBan[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState(false);

  const reloadInvitations = useCallback(async () => {
    try {
      setInvitations(await roomInvitationService.listOutgoingForRoom(roomId));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  const reloadBans = useCallback(async () => {
    try {
      setBans(await roomService.listBans(roomId));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  useEffect(() => {
    if (!isOpen) return;
    setErr(null);
    if (tab === 'invitations') reloadInvitations();
    if (tab === 'banned') reloadBans();
  }, [isOpen, tab, reloadInvitations, reloadBans]);

  if (!isOpen) return null;

  const renderMembers = () => (
    <ul className="divide-y">
      {members.map((m: RoomMemberView) => {
        const isMe = m.userId === currentUserId;
        return (
          <li key={m.userId} className="flex items-center justify-between py-2">
            <div className="flex items-center gap-2">
              <span className="font-medium">{m.username}</span>
              {m.isOwner && (
                <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">
                  owner
                </span>
              )}
              {!m.isOwner && m.role === 'admin' && (
                <span className="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded">
                  admin
                </span>
              )}
              {isMe && <span className="text-xs text-gray-400">(you)</span>}
            </div>
            {!isMe && !m.isOwner && (
              <div className="flex gap-1 text-xs">
                <button
                  onClick={async () => {
                    await kick(m.userId);
                    await reloadMembers();
                  }}
                  className="px-2 py-1 border border-red-400 text-red-600 rounded hover:bg-red-50"
                >
                  Kick
                </button>
                {m.role === 'admin' ? (
                  <button
                    onClick={async () => {
                      await demote(m.userId);
                      await reloadMembers();
                    }}
                    className="px-2 py-1 border rounded hover:bg-gray-100"
                  >
                    Demote
                  </button>
                ) : (
                  <button
                    onClick={async () => {
                      await promote(m.userId);
                      await reloadMembers();
                    }}
                    className="px-2 py-1 border rounded hover:bg-gray-100"
                  >
                    Promote
                  </button>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );

  const renderInvitations = () =>
    invitations.length === 0 ? (
      <p className="text-gray-500 italic py-4">No pending invitations.</p>
    ) : (
      <ul className="divide-y">
        {invitations.map((inv) => (
          <li key={inv.id} className="flex items-center justify-between py-2">
            <div>
              <div className="font-medium">
                Invitation {inv.id.slice(0, 8)}
              </div>
              <div className="text-xs text-gray-500">
                sent by {inv.inviterUsername} ·{' '}
                {new Date(inv.createdAt).toLocaleString()}
              </div>
            </div>
            <button
              onClick={async () => {
                await roomInvitationService.cancelInvitation(roomId, inv.id);
                await reloadInvitations();
              }}
              className="px-3 py-1 border rounded hover:bg-gray-100"
            >
              Cancel
            </button>
          </li>
        ))}
      </ul>
    );

  const renderBanned = () =>
    bans.length === 0 ? (
      <p className="text-gray-500 italic py-4">No banned users.</p>
    ) : (
      <ul className="divide-y">
        {bans.map((b) => (
          <li key={b.bannedUserId} className="flex items-center justify-between py-2">
            <div>
              <div className="font-medium">{b.bannedUsername}</div>
              <div className="text-xs text-gray-500">
                banned by {b.bannedByUsername} ·{' '}
                {new Date(b.bannedAt).toLocaleString()}
              </div>
            </div>
            <button
              onClick={async () => {
                await unban(b.bannedUserId);
                await reloadBans();
              }}
              className="px-3 py-1 border rounded hover:bg-gray-100"
            >
              Unban
            </button>
          </li>
        ))}
      </ul>
    );

  const renderSettings = () => (
    <div className="space-y-4 py-2">
      <div>
        <div className="text-xs font-semibold uppercase text-gray-500">Name</div>
        <div className="py-1 font-medium">{room?.name}</div>
      </div>
      {room?.description && (
        <div>
          <div className="text-xs font-semibold uppercase text-gray-500">Description</div>
          <div className="py-1 text-sm text-gray-700">{room.description}</div>
        </div>
      )}
      {isOwner && (
        <div className="pt-4 border-t">
          {!deleteConfirm ? (
            <button
              onClick={() => setDeleteConfirm(true)}
              className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
            >
              Delete room
            </button>
          ) : (
            <div className="space-y-2">
              <p className="text-sm text-gray-700">
                This will permanently delete <strong>{room?.name}</strong> and all its messages.
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setDeleteConfirm(false)}
                  className="px-4 py-2 border rounded hover:bg-gray-100"
                >
                  Cancel
                </button>
                <button
                  onClick={async () => {
                    await deleteRoom();
                    onClose();
                  }}
                  className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
                >
                  Confirm delete
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );

  const tabClass = (t: Tab) =>
    `px-3 py-2 text-sm ${
      tab === t ? 'border-b-2 border-blue-500 font-semibold' : 'text-gray-500 hover:text-gray-700'
    }`;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg w-[40rem] max-h-[80vh] flex flex-col">
        <div className="flex justify-between items-center px-5 py-3 border-b">
          <h2 className="text-xl font-bold">Manage room</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">
            ×
          </button>
        </div>
        <div className="flex gap-2 px-5 border-b">
          <button onClick={() => setTab('members')} className={tabClass('members')}>
            Members
          </button>
          <button onClick={() => setTab('invitations')} className={tabClass('invitations')}>
            Invitations
          </button>
          <button onClick={() => setTab('banned')} className={tabClass('banned')}>
            Banned
          </button>
          <button onClick={() => setTab('settings')} className={tabClass('settings')}>
            Settings
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-3">
          {err && <div className="text-red-500 text-sm mb-2">{err}</div>}
          {tab === 'members' && renderMembers()}
          {tab === 'invitations' && renderInvitations()}
          {tab === 'banned' && renderBanned()}
          {tab === 'settings' && renderSettings()}
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Build + tests**

Run: `cd /src/ai_hakaton/frontend && npm run build && npm test -- --run`
Expected: clean + green.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/components/ManageRoomModal.tsx
git commit -m "feat(frontend): ManageRoomModal tabs — Members | Invitations | Banned | Settings" -m "Members tab owns kick/promote/demote (removed from right panel). Invitations tab lists outgoing invites with Cancel. Banned tab lists bans with Unban. Settings tab shows read-only name+description and owner-only Delete room with inline confirm." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Frontend — remove old Bans/DeleteRoomDialog wiring from `ChatPage`

The ChatPage still imports `BanListPanel` + `DeleteRoomDialog` + their state. Those modals are now opened from `ManageRoomModal` instead. Clean up.

**Files:**
- Modify: `frontend/src/pages/ChatPage.tsx`

- [ ] **Step 1: Rewrite `frontend/src/pages/ChatPage.tsx`**

```tsx
import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useRoom } from '../hooks/useRoom';
import { useRoomMessages } from '../hooks/useRoomMessages';
import { useWebSocket } from '../hooks/useWebSocket';
import { MessageList } from '../components/MessageList';
import { MessageInput } from '../components/MessageInput';
import { roomService } from '../services/roomService';

export const ChatPage: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const { currentRoom, fetchRoom, leaveRoom } = useRoom();
  const { messages, loadInitialMessages, loadMoreMessages, addMessage } = useRoomMessages(roomId);
  const { isConnected, subscribe, unsubscribe, sendMessage: sendWebSocketMessage } = useWebSocket();

  useEffect(() => {
    if (!roomId) return;
    fetchRoom(roomId);
    loadInitialMessages(roomId);
    // joinRoom is idempotent server-side and swallows for private rooms
    // where the caller is already a member (owner or invitee).
    roomService.joinRoom(roomId).catch(() => {});

    if (isConnected) {
      subscribe(roomId, addMessage);
    }

    return () => {
      if (roomId) unsubscribe(roomId);
    };
  }, [roomId, isConnected]);

  const handleSendMessage = (text: string) => {
    if (roomId && isConnected) {
      try {
        sendWebSocketMessage(roomId, text);
      } catch (err) {
        console.error('Failed to send message:', err);
      }
    }
  };

  const handleLeaveRoom = async () => {
    if (roomId) {
      await leaveRoom(roomId);
      navigate('/rooms');
    }
  };

  return (
    <div className="h-full bg-gray-100 flex flex-col min-h-0">
      <div className="bg-white shadow p-4 border-b">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold">{currentRoom?.name || 'Loading...'}</h1>
            {currentRoom?.description && (
              <p className="text-gray-600 text-sm">{currentRoom.description}</p>
            )}
          </div>
          <button
            onClick={handleLeaveRoom}
            className="px-4 py-2 border rounded hover:bg-gray-100"
          >
            Leave Room
          </button>
        </div>
      </div>

      <div className="flex flex-1 min-h-0 w-full">
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          <MessageList
            messages={messages}
            isLoading={false}
            hasMore={true}
            onLoadMore={loadMoreMessages}
          />
          <MessageInput onSend={handleSendMessage} disabled={!isConnected} />
        </div>
      </div>
    </div>
  );
};
```

- [ ] **Step 2: Build + tests**

Run: `cd /src/ai_hakaton/frontend && npm run build && npm test -- --run`
Expected: clean + green.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/pages/ChatPage.tsx
git commit -m "refactor(frontend): ChatPage delegates moderation UI to ManageRoomModal" -m "- Drops inline BanListPanel and DeleteRoomDialog usage (ManageRoomModal owns them via RoomMembersPanel)" -m "- Drops useRoomMembersWithRole and useRoomAdminActions calls from the page (panel-scoped now)" -m "- Keeps Leave Room button; Delete Room is now in Settings tab" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Frontend — ComposerActions slot in MessageInput + RoomListPage trim My-rooms tab

**Files:**
- Create: `frontend/src/components/ComposerActions.tsx`
- Modify: `frontend/src/components/MessageInput.tsx`
- Modify: `frontend/src/pages/RoomListPage.tsx`

- [ ] **Step 1: Create `frontend/src/components/ComposerActions.tsx`**

```tsx
import React from 'react';

interface Props {
  children?: React.ReactNode;
}

// Renders a flex row for composer controls (emoji picker, attach, reply pill).
// Feature #5 (content) and Feature #6 (attachments) inject controls here.
// Returns null when empty to avoid an empty container in the DOM.
export const ComposerActions: React.FC<Props> = ({ children }) => {
  if (!children || (Array.isArray(children) && children.length === 0)) return null;
  return <div className="flex items-center gap-2 mt-2">{children}</div>;
};
```

- [ ] **Step 2: Wire `ComposerActions` into `MessageInput.tsx`**

Rewrite `frontend/src/components/MessageInput.tsx`:

```tsx
import React, { useState } from 'react';
import { ComposerActions } from './ComposerActions';

interface MessageInputProps {
  onSend: (text: string) => void;
  disabled: boolean;
  actions?: React.ReactNode;
}

const MAX_LENGTH = 3072;

export const MessageInput: React.FC<MessageInputProps> = ({ onSend, disabled, actions }) => {
  const [text, setText] = useState('');

  const send = () => {
    if (disabled) return;
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(text);
    setText('');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    send();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      send();
    }
  };

  const remaining = MAX_LENGTH - text.length;

  return (
    <form onSubmit={handleSubmit} className="border-t p-4 bg-white rounded">
      <ComposerActions>{actions}</ComposerActions>
      <div className="flex gap-2">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX_LENGTH))}
          onKeyDown={handleKeyDown}
          placeholder="Type a message... (Ctrl+Enter to send)"
          disabled={disabled}
          rows={3}
          className="flex-1 border rounded px-3 py-2 resize-none"
        />
        <button
          type="submit"
          disabled={disabled || !text.trim()}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 self-end"
        >
          Send
        </button>
      </div>
      <div className="text-xs text-gray-500 mt-2">
        {remaining} characters remaining · Ctrl+Enter (⌘+Enter on Mac) to send
      </div>
    </form>
  );
};
```

- [ ] **Step 3: Trim `RoomListPage.tsx` — drop `My rooms` tab (sidebar owns it now)**

Replace `frontend/src/pages/RoomListPage.tsx`:

```tsx
import React, { useEffect, useState } from 'react';
import { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { useNavigate } from 'react-router-dom';

export const RoomListPage: React.FC = () => {
  const navigate = useNavigate();
  const [publicRooms, setPublicRooms] = useState<ChatRoom[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const reload = async () => {
    try {
      const result = await roomService.listPublicRooms(0, 20);
      setPublicRooms(result.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  useEffect(() => {
    reload();
  }, []);

  const handleCreateRoom = async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ) => {
    const newRoom = await roomService.createRoom(name, description, visibility);
    navigate(`/rooms/${newRoom.id}`);
  };

  const renderRoomCard = (room: ChatRoom) => (
    <div
      key={room.id}
      className="bg-white rounded-lg shadow p-4 cursor-pointer hover:shadow-lg"
      onClick={() => navigate(`/rooms/${room.id}`)}
    >
      <div className="flex justify-between items-start mb-2">
        <h2 className="text-lg font-bold">{room.name}</h2>
        <span
          className={`text-xs px-2 py-1 rounded ${
            room.visibility === 'private'
              ? 'bg-purple-100 text-purple-700'
              : 'bg-green-100 text-green-700'
          }`}
        >
          {room.visibility}
        </span>
      </div>
      {room.description && <p className="text-gray-600 text-sm mb-4">{room.description}</p>}
      <div className="text-xs text-gray-400">
        Created {new Date(room.createdAt).toLocaleDateString()}
      </div>
    </div>
  );

  return (
    <div className="h-full bg-gray-100 p-6 overflow-y-auto">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-3xl font-bold">Public Rooms</h1>
          <button
            onClick={() => setIsModalOpen(true)}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            New Room
          </button>
        </div>

        {error && <div className="text-red-500 mb-4">{error}</div>}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {publicRooms.map(renderRoomCard)}
        </div>
        {publicRooms.length === 0 && (
          <p className="text-gray-500 italic">No public rooms yet.</p>
        )}

        <RoomCreateModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          onCreate={handleCreateRoom}
        />
      </div>
    </div>
  );
};
```

- [ ] **Step 4: Build + tests**

Run: `cd /src/ai_hakaton/frontend && npm run build && npm test -- --run`
Expected: clean + green. If any test references `RoomInvitationList` imported from `RoomListPage`, fix narrowly. If unit tests rely on the old tabbed UI, either skip those assertions or update to match the simplified page.

- [ ] **Step 5: Commit**

```bash
cd /src/ai_hakaton
git add frontend/src/components/ComposerActions.tsx frontend/src/components/MessageInput.tsx \
        frontend/src/pages/RoomListPage.tsx
git commit -m "feat(frontend): MessageInput ComposerActions slot + RoomListPage simplified to public discovery" -m "- MessageInput renders optional actions above the textarea via new ComposerActions slot component (empty today; Features #5/#6 will inject buttons here)" -m "- RoomListPage drops the Public | My rooms tab switcher; sidebar already shows My rooms" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Playwright — app-shell.spec.ts

**Files:**
- Create: `frontend/e2e/app-shell.spec.ts`

- [ ] **Step 1: Create the test**

```typescript
import { test, expect, Browser, Page } from '@playwright/test';

const password = 'password123';

function uniqueUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`;
  return { username, email: `${username}@example.com`, password };
}

async function registerAndLogin(
  browser: Browser,
  email: string,
  username: string,
  pw: string,
): Promise<{ ctx: Awaited<ReturnType<Browser['newContext']>>; page: Page }> {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto('/register');
  await page.fill('#email', email);
  await page.fill('#username', username);
  await page.fill('#password', pw);
  await page.fill('#confirm-password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/login$/);
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/rooms$/);
  return { ctx, page };
}

test.describe('App shell', () => {
  test('top menu navigates to each primary route', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const { ctx, page } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );

    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
    await expect(page.getByRole('complementary', { name: 'Workspace' })).toBeVisible();

    await page.getByRole('link', { name: 'Contacts' }).click();
    await page.waitForURL(/.*\/friends$/);
    await expect(page.locator('body')).toContainText(/friends/i);

    await page.getByRole('link', { name: 'Sessions' }).click();
    await page.waitForURL(/.*\/sessions$/);
    await expect(page.locator('body')).toContainText(/feature #7/i);

    await page.getByRole('link', { name: 'Public Rooms' }).click();
    await page.waitForURL(/.*\/rooms$/);
    await expect(page.locator('body')).toContainText(/public rooms/i);

    await ctx.close();
  });

  test('search finds a public room and clicking joins + navigates', async ({ browser }) => {
    const bob = uniqueUser('owner');
    const { ctx: ownerCtx, page: ownerPage } = await registerAndLogin(
      browser,
      bob.email,
      bob.username,
      bob.password,
    );
    const roomName = `searchable-${Date.now().toString().slice(-7)}`;
    // Use the sidebar Create Room button
    await ownerPage.getByRole('button', { name: '+ Create room' }).click();
    await ownerPage.fill('input[placeholder="Enter room name"]', roomName);
    await ownerPage.click('button:has-text("Create"):not(:has-text("Cancel"))');
    await ownerPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await ownerCtx.close();

    const carol = uniqueUser('searcher');
    const { ctx: searcherCtx, page: searcherPage } = await registerAndLogin(
      browser,
      carol.email,
      carol.username,
      carol.password,
    );

    const searchBox = searcherPage.getByPlaceholder('Search rooms or users…');
    await searchBox.click();
    await searchBox.fill(roomName.slice(0, 8));
    await expect(searcherPage.locator('body')).toContainText(roomName, { timeout: 5_000 });
    await searcherPage.getByRole('button', { name: new RegExp(`# ${roomName}`) }).click();
    await searcherPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    await searcherCtx.close();
  });

  test('ManageRoomModal tabs open from the right panel for the room owner', async ({
    browser,
  }) => {
    const dave = uniqueUser('owner');
    const { ctx, page } = await registerAndLogin(
      browser,
      dave.email,
      dave.username,
      dave.password,
    );
    const roomName = `mgrtest-${Date.now().toString().slice(-7)}`;
    await page.getByRole('button', { name: '+ Create room' }).click();
    await page.fill('input[placeholder="Enter room name"]', roomName);
    await page.click('button:has-text("Create"):not(:has-text("Cancel"))');
    await page.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    await page.getByRole('button', { name: 'Manage room' }).click();
    await expect(page.getByRole('heading', { name: 'Manage room' })).toBeVisible();

    await page.getByRole('button', { name: 'Invitations' }).click();
    await expect(page.locator('body')).toContainText(/no pending invitations|invitation/i);

    await page.getByRole('button', { name: 'Banned' }).click();
    await expect(page.locator('body')).toContainText(/no banned users|banned by/i);

    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.locator('body')).toContainText(/delete room/i);

    await ctx.close();
  });
});
```

- [ ] **Step 2: Ensure the stack is up-to-date**

Run:

```bash
cd /src/ai_hakaton && docker compose up -d --build backend
```

Wait a few seconds for it to come up. Verify `curl -s http://localhost:8080/api/actuator/health` (or tail `docker logs chat-backend` for the "Started" line).

- [ ] **Step 3: Run Playwright**

Run:

```bash
cd /src/ai_hakaton/frontend && npm run test:e2e -- --reporter=line
```

Expected: all suites pass, including the new `app-shell.spec.ts` scenarios (and the existing `room-moderation.spec.ts` still passing with the refactored UI — its selectors are button-text-based so should keep working).

If `room-moderation.spec.ts` fails because `button:has-text("Invite")` now resolves differently (the Invite button is inside the admin buttons block, still rendered, same text — it should still match), read the failure message and adjust only narrowly.

- [ ] **Step 4: Commit**

```bash
cd /src/ai_hakaton
git add frontend/e2e/app-shell.spec.ts
git commit -m "test(e2e): app shell navigation + search + ManageRoomModal tabs" -m "Three scenarios: top-menu routes each visible; sidebar search dropdown matches a public room and click navigates; owner opens Manage room modal and sees the four tabs." -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Roadmap update + housekeeping

**Files:**
- Modify: `/src/ai_hakaton/FEATURES_ROADMAP.md`

- [ ] **Step 1: Update roadmap**

In the Completed section, insert a new entry for "Execution #5: App Shell Refactor per Appendix A" ABOVE the current Feature #5 (which is renumbered to execution #6: "Message Content Enhancements").

Edit `FEATURES_ROADMAP.md`:

Add after the Feature #4 block inside `## Completed Features`:

```markdown
### Execution #5: App Shell Refactor (Appendix A layout) ✅
- Top menu (Public Rooms / Contacts / Sessions stub / Profile ▼)
- Left tree sidebar (Rooms ▸ Public / Private, Contacts, Create room, Search dropdown)
- Right `RoomMembersPanel` restructured: presence groupings (Online / AFK / Offline, all Offline until Feature #7), admin buttons at bottom (`Invite user`, `Manage room`)
- `ManageRoomModal` (tabbed: Members / Invitations / Banned / Settings) — owns kick/promote/demote, invitation cancel, unban, delete
- `/api/search` backend endpoint over public rooms + users (excludes caller / member rooms)
- `MessageInput` gains an empty `ComposerActions` slot for Features #5/#6
- `AppSidebar` removed; `App.tsx` nests authenticated routes under a single `AppShell` layout route
- Playwright lifecycle E2E (`app-shell.spec.ts`) + existing suite kept green
- Spec: `docs/superpowers/specs/2026-04-18-app-shell-refactor-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-app-shell-refactor.md`
- **Status: COMPLETE**
```

In the `Planned Features` section, renumber Feature #5 — keep the existing "Message Content Enhancements" content but add a note about execution ordering:

```markdown
### Feature #5 (planned content): Message Content Enhancements
> Execution note: this now lands AFTER the app shell refactor which claimed the execution #5 slot.
- Multi-line + emoji (plain text already supported)
- Replies / quoted messages
- Message editing with "edited" indicator
- Message deletion (by author or room admin)
- Applies to both rooms and DMs
- **Status: TODO**
```

Update the Progress block:

```markdown
## Progress
- **Completed:** 5 execution slots (Features #1, #2, #3, #4, App Shell Refactor)
- **In progress:** 0
- **Remaining:** 4 (Message Content, Attachments, Presence/Sessions, Account Management)
```

- [ ] **Step 2: Verify (visual skim)**

Read the updated `FEATURES_ROADMAP.md` top-to-bottom to confirm numbering is consistent and no stray "TODO" remains for Feature #4.

- [ ] **Step 3: Commit**

```bash
cd /src/ai_hakaton
git add FEATURES_ROADMAP.md
git commit -m "docs(roadmap): App Shell Refactor (execution #5) complete; message-content bumped to after the refactor" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Verification Checklist

Before considering the refactor shipped:

- [ ] `./gradlew test` — full backend suite passes (including the new `SearchServiceTest` / `SearchControllerTest`)
- [ ] `npm run build` — clean frontend build
- [ ] `npm test -- --run` — all vitest suites green
- [ ] `npm run test:e2e` — full Playwright suite green (new `app-shell.spec.ts` + existing `auth` / `chat-layout` / `friends-and-dms` / `room-moderation` / `room-reentry`)
- [ ] Browser smoke: after `docker compose up --build -d`, log in and verify:
  - Top menu renders and each link navigates
  - Sidebar shows Rooms (Public/Private) and Contacts with placeholders
  - `+ Create room` opens the modal
  - Search finds a public room and clicking joins + navigates
  - On `/rooms/:id`, right panel shows presence-grouped members; admin buttons visible for owner
  - `Manage room` opens the tabbed modal; each tab renders; Settings → Delete room navigates back to `/rooms`
- [ ] `FEATURES_ROADMAP.md` reflects the completed refactor and the renumbering note for Feature #5 (content)

---

## Notes

- Presence dots, unread counts, Sessions page functionality, Profile editing, emoji/attach/reply controls are **all out of scope** — stubs per spec.
- If Playwright exposes timing issues against live Vite HMR, re-run; do NOT add arbitrary `sleep`s. Use Playwright's auto-wait or `expect(locator).toBeVisible()`.
- Every commit must compile cleanly (both backend `./gradlew compileJava compileTestJava` and frontend `npm run build`). The plan is ordered so this holds.
