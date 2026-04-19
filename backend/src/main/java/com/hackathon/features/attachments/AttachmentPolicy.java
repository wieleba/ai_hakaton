package com.hackathon.features.attachments;

import java.util.Set;

public final class AttachmentPolicy {
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  public static final Set<String> IMAGE_MIMES =
      Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

  public static final Set<String> DOCUMENT_MIMES =
      Set.of("application/pdf", "text/plain", "application/zip");

  public static boolean isImage(String mime) {
    return mime != null && IMAGE_MIMES.contains(mime);
  }

  public static boolean isAllowed(String mime) {
    return mime != null && (IMAGE_MIMES.contains(mime) || DOCUMENT_MIMES.contains(mime));
  }

  private AttachmentPolicy() {}
}
