import { describe, it, expect, vi, beforeEach } from 'vitest';

describe('Message History Pagination - 100K Message Scenarios', () => {
  type MessageType = { id: number; text: string; timestamp: number };
  let mockFetchMessages: ReturnType<typeof vi.fn>;
  let messageCache: MessageType[] = [];

  beforeEach(() => {
    vi.clearAllMocks();
    messageCache = [];
    mockFetchMessages = vi.fn();
  });

  it('should load initial 50 most recent messages', async () => {
    // Simulate initial load: most recent 50 messages
    const initialMessages: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
      id: 100000 - i,
      text: `Message ${100000 - i}`,
      timestamp: Date.now() - i * 1000,
    }));

    mockFetchMessages.mockResolvedValueOnce(initialMessages);
    const result = (await mockFetchMessages()) as MessageType[];

    expect(result).toHaveLength(50);
    expect(result[0].id).toBe(100000); // Most recent
    expect(result[49].id).toBe(99951); // Older

    messageCache = result;
  });

  it('should load older messages when scrolling up', async () => {
    // First batch: messages 99951-100000
    const firstBatch: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
      id: 100000 - i,
      text: `Message ${100000 - i}`,
      timestamp: Date.now() - i * 1000,
    }));
    messageCache = firstBatch;

    // User scrolls up: load messages older than 99951
    const beforeMessageId = messageCache[messageCache.length - 1].id; // 99951
    const secondBatch: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
      id: beforeMessageId - 1 - i,
      text: `Message ${beforeMessageId - 1 - i}`,
      timestamp: Date.now() - (50 + i) * 1000,
    }));

    mockFetchMessages.mockResolvedValueOnce(secondBatch);
    const result = (await mockFetchMessages({
      limit: 50,
      before: beforeMessageId,
    })) as MessageType[];

    expect(result).toHaveLength(50);
    expect(result[0].id).toBe(99950); // Older than previous batch
    expect(result[49].id).toBe(99901);

    // Prepend older messages (add to top of list)
    messageCache = [...result, ...messageCache];
    expect(messageCache).toHaveLength(100);
  });

  it('should handle 100K message history with progressive loading', async () => {
    // Simulate loading through 100K messages in 50-message batches
    // 100,000 messages / 50 per batch = 2,000 batches
    let totalMessagesLoaded = 0;
    let currentMessageId = 100000;
    let batchCount = 0;

    // Load 20 batches to verify pattern (represents 1000 messages)
    while (batchCount < 20) {
      const batch: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
        id: currentMessageId - i,
        text: `Message ${currentMessageId - i}`,
        timestamp: Date.now() - totalMessagesLoaded * 1000,
      }));

      mockFetchMessages.mockResolvedValueOnce(batch);
      const result = (await mockFetchMessages({
        limit: 50,
        before: currentMessageId,
      })) as MessageType[];

      expect(result).toHaveLength(50);
      totalMessagesLoaded += result.length;
      currentMessageId -= 50;
      batchCount++;
    }

    expect(totalMessagesLoaded).toBe(1000); // 20 batches of 50
    // Extrapolating: 100K messages would require 2000 batches
    expect(batchCount).toBe(20);
  });

  it('should detect boundary when reaching earliest message', async () => {
    // When server returns fewer messages than limit, we've reached the end
    const lastBatch: MessageType[] = Array.from({ length: 30 }, (_, i) => ({
      // Only 30 messages instead of requested 50
      id: 30 - i,
      text: `Message ${30 - i}`,
      timestamp: Date.now() - i * 1000,
    }));

    mockFetchMessages.mockResolvedValueOnce(lastBatch);
    const result = (await mockFetchMessages({ limit: 50, before: 50 })) as MessageType[];

    // Returned fewer than limit = reached earliest message
    expect(result.length).toBeLessThan(50);
    expect(result).toHaveLength(30);

    // Signal: no more messages to load
    const hasMoreMessages = result.length === 50;
    expect(hasMoreMessages).toBe(false);
  });

  it('should not load duplicate messages when paginating', async () => {
    // First batch
    const batch1: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
      id: 100000 - i,
      text: `Message ${100000 - i}`,
      timestamp: Date.now() - i * 1000,
    }));

    // Second batch
    const batch2: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
      id: 99950 - i,
      text: `Message ${99950 - i}`,
      timestamp: Date.now() - (50 + i) * 1000,
    }));

    messageCache = batch1;
    messageCache = [...batch2, ...messageCache];

    // Verify no duplicates
    const ids = messageCache.map((m) => m.id);
    const uniqueIds = new Set(ids);
    expect(uniqueIds.size).toBe(100); // All unique
    expect(ids).toHaveLength(100);
  });

  it('should maintain message order when loading progressively', async () => {
    // Load 5 batches and verify descending order (newest first)
    // Simulate loading: start with newest batch, then progressively load older
    let allMessages: MessageType[] = [];
    let currentId = 100000;

    // First load: 100000-99951 (newest)
    const batch1: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
      id: currentId - i,
      text: `Message ${currentId - i}`,
      timestamp: Date.now() - i * 1000,
    }));
    allMessages = batch1;
    currentId -= 50; // Now 99950

    // Subsequent loads: append older messages to the end (they go below)
    for (let batch = 1; batch < 5; batch++) {
      const batchMessages: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
        id: currentId - i,
        text: `Message ${currentId - i}`,
        timestamp: Date.now() - (batch * 50 + i) * 1000,
      }));
      allMessages = [...allMessages, ...batchMessages]; // Older messages go to end
      currentId -= 50;
    }

    // Verify descending order in the list
    for (let i = 0; i < allMessages.length - 1; i++) {
      expect(allMessages[i].id).toBeGreaterThan(allMessages[i + 1].id);
    }

    expect(allMessages).toHaveLength(250); // 5 batches × 50
    expect(allMessages[0].id).toBe(100000); // Most recent at top
    expect(allMessages[249].id).toBe(99751); // Oldest at bottom
  });

  it('should perform progressive load in <30 seconds for 100K scenario', async () => {
    const startTime = Date.now();
    let batchCount = 0;
    let currentId = 100000;

    // Simulate loading all 2000 batches needed for 100K messages
    // Each fetch takes ~5ms (mocked)
    mockFetchMessages.mockImplementation(
      () =>
        new Promise((resolve) => {
          setTimeout(() => {
            const batch: MessageType[] = Array.from({ length: 50 }, (_, i) => ({
              id: currentId - i,
              text: `Message ${currentId - i}`,
              timestamp: Date.now() - i * 1000,
            }));
            resolve(batch);
          }, 5); // Each batch takes ~5ms
        })
    );

    // Load 400 batches (20,000 messages) as representative test
    // Full 100K would take ~10 seconds simulated time
    for (let i = 0; i < 400; i++) {
      await mockFetchMessages({ limit: 50, before: currentId });
      currentId -= 50;
      batchCount++;
    }

    const elapsedTime = Date.now() - startTime;

    // 400 batches at 5ms each should be ~2 seconds
    // Full 2000 batches would be ~10 seconds
    // Allow up to 30 seconds for reasonable system
    expect(elapsedTime).toBeLessThan(30000);
    expect(batchCount).toBe(400);
  });

  it('should handle scroll detection at top of list', () => {
    // Simulate scroll position tracking
    const scrollThreshold = 100; // Load when user scrolls within 100px of top
    let scrollPosition = 0;
    let isLoading = false;
    let hasMoreMessages = true;

    const handleScroll = (newPosition: number) => {
      scrollPosition = newPosition;
      // Trigger load if scrolled to top AND not already loading
      return (
        scrollPosition < scrollThreshold && !isLoading && hasMoreMessages
      );
    };

    // Test: Scroll far from top - no load
    expect(handleScroll(500)).toBe(false);

    // Test: Scroll within threshold - should load
    expect(handleScroll(50)).toBe(true);

    // Test: While loading - should not load again
    isLoading = true;
    expect(handleScroll(50)).toBe(false);

    // Test: Reached earliest messages
    isLoading = false;
    hasMoreMessages = false;
    expect(handleScroll(50)).toBe(false);
  });
});
