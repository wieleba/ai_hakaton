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

    // Use the nanotime slice (16 digits) to uniquely match `other`; tests partial matching
    // while avoiding false-positive collisions with other users accumulated across test cases
    String partialName = other.getUsername().substring(4, 20);
    SearchService.SearchResult r = searchService.search(partialName, caller.getId(), 5);
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
