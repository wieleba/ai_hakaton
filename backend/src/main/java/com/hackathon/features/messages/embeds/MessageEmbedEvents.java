package com.hackathon.features.messages.embeds;

import com.hackathon.features.dms.DirectMessage;
import com.hackathon.features.messages.Message;

/** Event records fired from MessageService / DirectMessageService after the DB commit,
 *  consumed by EmbedService to run YouTube oEmbed + persistence off the transaction. */
public final class MessageEmbedEvents {
    private MessageEmbedEvents() {}

    public record MessageCreated(Message message) {}
    public record MessageEdited(Message message) {}
    public record DirectMessageCreated(DirectMessage message) {}
    public record DirectMessageEdited(DirectMessage message) {}
}
