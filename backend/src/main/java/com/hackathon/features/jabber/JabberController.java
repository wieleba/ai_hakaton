package com.hackathon.features.jabber;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jabber")
@RequiredArgsConstructor
public class JabberController {

    private final JabberStatusService jabberStatusService;
    private final JabberProvisioningService provisioning;
    private final UserService userService;

    @GetMapping("/status")
    public List<JabberServerStatus> status() {
        return jabberStatusService.statuses();
    }

    /**
     * Returns the calling user's XMPP bridge credentials so they can paste into
     * a Jabber client. Returns {@code available: false} when the username is
     * not JID-safe or provisioning never succeeded; the UI shows an explainer
     * in that case.
     */
    @GetMapping("/me")
    public ResponseEntity<MyJabberCredentials> myCredentials(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        User user = userService.getUserById(userId);
        Optional<JabberProperties.Server> primary = provisioning.primary();
        if (primary.isEmpty() || user.getXmppPassword() == null) {
            return ResponseEntity.ok(MyJabberCredentials.unavailable(user.getUsername()));
        }
        JabberProperties.Server s = primary.get();
        return ResponseEntity.ok(new MyJabberCredentials(
                true,
                user.getUsername() + "@" + s.domain(),
                s.clientHost(),
                s.clientPort(),
                user.getXmppPassword()));
    }

    public record MyJabberCredentials(
            boolean available, String jid, String host, Integer port, String password) {

        static MyJabberCredentials unavailable(String username) {
            return new MyJabberCredentials(false, username, null, null, null);
        }
    }

    private UUID currentUserId(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof UUID uuid) return uuid;
        return userService.getUserByUsername(authentication.getName()).getId();
    }
}
