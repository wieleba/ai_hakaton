package com.hackathon.shared.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InMemoryStorageServiceTest {
  @Autowired StorageService storage;

  @Test
  void storeThenLoad_returnsSameBytes() throws Exception {
    byte[] input = "hello world".getBytes();
    String key = storage.store(new ByteArrayInputStream(input), input.length, "text/plain");
    assertNotNull(key);
    byte[] out = storage.load(key).readAllBytes();
    assertArrayEquals(input, out);
  }

  @Test
  void deleteUnknownKey_isNoOp() {
    assertDoesNotThrow(() -> storage.delete("not-a-real-key"));
  }

  @Test
  void loadAfterDelete_throws() {
    byte[] input = "gone soon".getBytes();
    String key = storage.store(new ByteArrayInputStream(input), input.length, "text/plain");
    storage.delete(key);
    assertThrows(IllegalArgumentException.class, () -> storage.load(key));
  }
}
