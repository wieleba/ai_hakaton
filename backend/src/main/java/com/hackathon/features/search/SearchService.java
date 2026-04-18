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
