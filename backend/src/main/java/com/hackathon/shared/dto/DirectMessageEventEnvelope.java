package com.hackathon.shared.dto;

/** Tagged-union envelope sent to /user/{uuid}/queue/dms. */
public record DirectMessageEventEnvelope(Type type, DirectMessageDTO message) {
  public enum Type {
    CREATED,
    EDITED,
    DELETED
  }

  public static DirectMessageEventEnvelope created(DirectMessageDTO m) {
    return new DirectMessageEventEnvelope(Type.CREATED, m);
  }

  public static DirectMessageEventEnvelope edited(DirectMessageDTO m) {
    return new DirectMessageEventEnvelope(Type.EDITED, m);
  }

  public static DirectMessageEventEnvelope deleted(DirectMessageDTO m) {
    return new DirectMessageEventEnvelope(Type.DELETED, m);
  }
}
