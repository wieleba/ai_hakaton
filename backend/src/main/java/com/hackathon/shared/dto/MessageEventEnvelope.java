package com.hackathon.shared.dto;

/** Tagged-union envelope sent over /topic/room/{roomId}. */
public record MessageEventEnvelope(Type type, ChatMessageDTO message) {
  public enum Type {
    CREATED,
    EDITED,
    DELETED
  }

  public static MessageEventEnvelope created(ChatMessageDTO m) {
    return new MessageEventEnvelope(Type.CREATED, m);
  }

  public static MessageEventEnvelope edited(ChatMessageDTO m) {
    return new MessageEventEnvelope(Type.EDITED, m);
  }

  public static MessageEventEnvelope deleted(ChatMessageDTO m) {
    return new MessageEventEnvelope(Type.DELETED, m);
  }
}
