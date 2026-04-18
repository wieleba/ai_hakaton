# Chat Application Design Caveats

## 1. Message Data Strategy: Hybrid REST + WebSocket

### Problem
- **REST only:** Cannot efficiently handle 100+ message updates per user (too many HTTP requests)
- **WebSocket only:** Overcomplicated architecture, unnecessary for all data types

### Solution: Hybrid Approach
- **REST Endpoints:**
  - Initial message history load (paginated)
  - Historical message fetches
  - Message search
  - Static data (user profiles, room info)

- **WebSocket Events:**
  - Real-time message delivery (new messages only)
  - User typing indicators
  - User activity status
  - Presence updates
  - Temporary state changes

### Implementation Notes
- Load messages with REST: `GET /api/rooms/{roomId}/messages?page=1&limit=50`
- Stream new messages: WebSocket event `message:new`
- Never load all messages at once - use pagination
- Cache recent messages in client to reduce redundant fetches

---

## 2. User Activity Tracking Strategy

### Optimal Implementation
- **Hook:** Monitor `mousemove` (or keyboard events)
- **Throttle:** Send "user active" signal only if cursor moved within past 1-2 seconds
- **Signal:** Emit WebSocket event when activity detected

### Frontend Logic
```javascript
let lastActivityTime = Date.now();
let activityTimeout = null;

document.addEventListener('mousemove', () => {
  lastActivityTime = Date.now();
  
  if (!activityTimeout) {
    activityTimeout = setTimeout(() => {
      websocket.emit('user:active');
      activityTimeout = null;
    }, 1000); // Send signal every 1 second if active
  }
});
```

### Benefits
- Lightweight (minimal server load)
- Reflects actual user engagement
- Better than polling-based activity tracking

---

## 3. Browser Tab Hibernation Handling

### Challenge
Browser hibernates inactive tabs:
- All JavaScript execution stops
- Cannot send "user inactive" signal
- WebSocket connection may be suspended
- Need graceful reconnection on tab wake

### Server-Side Solution
- **Activity Timeout:** Mark user as inactive after 5+ minutes of no activity signals
- **Heartbeat:** Send periodic ping to clients to detect disconnections
- **Fallback:** Monitor last activity timestamp, auto-clear after timeout

### Frontend Solution
- **Visibility API:** Detect when tab becomes active again
- **Reconnect Logic:** Re-establish WebSocket on tab visibility change
- **Resume State:** Fetch missed messages/updates after reconnection
- **Don't panic:** Gracefully handle connection drops (expected behavior)

### Implementation Notes
```javascript
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    console.log('Tab hidden - WebSocket may be suspended');
    // Let server handle the inactivity
  } else {
    console.log('Tab visible - Reconnecting...');
    reconnectWebSocket();
    fetchMissedMessages();
  }
});
```

---

## 4. Architecture Summary

### Data Flow
```
Initial Load (REST) → Load messages, user profiles, room info
                   ↓
Real-time Updates (WebSocket) → New messages, typing, activity, presence
                   ↓
Periodic REST Calls → Sync state, resolve conflicts, pull missed data (fallback)
                   ↓
Graceful Degradation → Work with poor connections, tab hibernation
```

### Backend Responsibilities
1. **REST API:** Stateless, cacheable, paginated responses
2. **WebSocket Server:** Maintain active connections, broadcast events
3. **Activity Manager:** Track user activity, auto-timeout after inactivity
4. **Message Queue:** Buffer messages for temporarily disconnected users

### Frontend Responsibilities
1. **Activity Monitor:** Track cursor/keyboard, send heartbeat signals
2. **Connection Manager:** Handle WebSocket lifecycle, reconnect on failure
3. **Data Sync:** Merge REST + WebSocket data intelligently
4. **Visibility Handler:** Resume activity when tab becomes active

---

## 5. Performance Considerations

| Metric | Target | Notes |
|--------|--------|-------|
| Message Delivery Latency | <500ms | Via WebSocket |
| Activity Signal Interval | 1-2 sec | Throttled mousemove |
| Activity Timeout | 5+ min | Server-side inactivity marker |
| Message Page Size | 50 messages | Balance between load time and UX |
| WebSocket Reconnect | Exponential backoff | Max 30 sec |
| Tab Hibernation Recovery | <2 sec | Quick reconnection + state sync |

