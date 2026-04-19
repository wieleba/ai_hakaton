package com.hackathon.shared.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class InMemoryStorageService implements StorageService {
  private final ConcurrentMap<String, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public String store(InputStream content, long size, String mimeType) {
    try {
      byte[] bytes = content.readAllBytes();
      String key = UUID.randomUUID().toString();
      objects.put(key, bytes);
      return key;
    } catch (IOException e) {
      throw new RuntimeException("store failed", e);
    }
  }

  @Override
  public InputStream load(String storageKey) {
    byte[] bytes = objects.get(storageKey);
    if (bytes == null) {
      throw new IllegalArgumentException("Unknown storage key: " + storageKey);
    }
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public void delete(String storageKey) {
    objects.remove(storageKey);
  }
}
