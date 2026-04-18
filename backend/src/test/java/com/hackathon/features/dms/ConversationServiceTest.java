package com.hackathon.features.dms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ConversationServiceTest {
  @Mock DirectConversationRepository directConversationRepository;
  ConversationService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ConversationService(directConversationRepository);
  }

  @Test
  void getOrCreate_returnsExistingWhenPresent_regardlessOfArgOrder() {
    UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    DirectConversation existing =
        DirectConversation.builder().id(UUID.randomUUID()).user1Id(a).user2Id(b).build();
    when(directConversationRepository.findByUser1IdAndUser2Id(a, b))
        .thenReturn(Optional.of(existing));

    DirectConversation r1 = service.getOrCreate(a, b);
    DirectConversation r2 = service.getOrCreate(b, a); // inverse arg order

    assertSame(existing, r1);
    assertSame(existing, r2);
    verify(directConversationRepository, never()).save(any());
  }

  @Test
  void getOrCreate_createsWithCanonicalOrdering_whenCalledWithHigherFirst() {
    UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    when(directConversationRepository.findByUser1IdAndUser2Id(a, b)).thenReturn(Optional.empty());
    when(directConversationRepository.save(any(DirectConversation.class)))
        .thenAnswer(inv -> inv.getArgument(0, DirectConversation.class));

    DirectConversation created = service.getOrCreate(b, a); // higher UUID first

    assertEquals(a, created.getUser1Id()); // canonical: lower UUID first
    assertEquals(b, created.getUser2Id());
  }

  @Test
  void getOrCreate_rejectsSameUser() {
    UUID a = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> service.getOrCreate(a, a));
  }
}
