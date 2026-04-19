package com.hackathon.shared.storage;

import java.io.InputStream;

public interface StorageService {
  /** Stores bytes and returns a storage key (UUID-shaped). */
  String store(InputStream content, long size, String mimeType);

  /** Streams bytes for a stored object. Throws if key unknown. */
  InputStream load(String storageKey);

  /** Idempotent — deleting an unknown key is a no-op. */
  void delete(String storageKey);
}
