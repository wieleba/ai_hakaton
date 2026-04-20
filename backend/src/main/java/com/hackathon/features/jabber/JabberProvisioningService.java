package com.hackathon.features.jabber;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Provisions / deprovisions XMPP accounts on the primary ejabberd server
 * (label "A" in JabberProperties) for Chat users. Called from {@code
 * UserService.registerUser} after the Chat row is saved.
 *
 * <p>Failures never block user registration — if ejabberd is unreachable or
 * the JID local-part is invalid, the Chat user's {@code xmpp_password} stays
 * NULL and they simply have no XMPP bridge. The {@code /account} page will
 * show "XMPP unavailable for this username".
 */
@Service
@Slf4j
public class JabberProvisioningService {

    /** Safe JID local-part subset — lowercase ASCII, digits, {@code .-_}. */
    private static final Pattern SAFE_LOCALPART = Pattern.compile("^[a-z0-9._-]+$");

    private static final int PASSWORD_BYTES = 18; // ~24-char base64 password

    private final JabberProperties props;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient http;
    private final SecureRandom random = new SecureRandom();

    public JabberProvisioningService(
            JabberProperties props,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.props = props;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(java.time.Duration.ofMillis(2000))
                .withReadTimeout(java.time.Duration.ofMillis(2000));
        this.http = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    /**
     * Best-effort registration of {@code user.username}@primary-domain with a
     * fresh random password. Persists the password on the user row on success.
     */
    @Transactional
    public void provisionFor(User user) {
        Optional<JabberProperties.Server> primary = primary();
        if (primary.isEmpty()) {
            log.debug("JabberProvisioning: no primary server configured, skipping {}", user.getUsername());
            return;
        }
        String localpart = user.getUsername();
        if (!SAFE_LOCALPART.matcher(localpart).matches()) {
            log.info(
                    "JabberProvisioning: username '{}' has characters invalid for a JID local-part; skipping",
                    localpart);
            return;
        }
        String password = randomPassword();
        JabberProperties.Server s = primary.get();
        try {
            callAdminApi(s, "/api/register", Map.of(
                    "user", localpart,
                    "host", s.domain(),
                    "password", password));
            user.setXmppPassword(password);
            userRepository.save(user);
            eventPublisher.publishEvent(new JabberEvents.UserProvisioned(user.getId()));
            log.info("JabberProvisioning: provisioned {}@{}", localpart, s.domain());
        } catch (RuntimeException e) {
            log.warn(
                    "JabberProvisioning: failed to provision {}@{}: {}",
                    localpart,
                    s.domain(),
                    e.toString());
        }
    }

    /**
     * Best-effort unregistration. Called from the delete-account path to keep
     * ejabberd's user list in sync. Does not throw even when the JID isn't
     * present (user already deleted, or never provisioned).
     */
    public void deprovisionFor(User user) {
        Optional<JabberProperties.Server> primary = primary();
        if (primary.isEmpty() || user.getXmppPassword() == null) {
            return;
        }
        JabberProperties.Server s = primary.get();
        try {
            callAdminApi(s, "/api/unregister", Map.of(
                    "user", user.getUsername(),
                    "host", s.domain()));
            eventPublisher.publishEvent(new JabberEvents.UserDeprovisioned(user.getId()));
            log.info("JabberProvisioning: deprovisioned {}@{}", user.getUsername(), s.domain());
        } catch (RuntimeException e) {
            log.warn(
                    "JabberProvisioning: failed to deprovision {}@{}: {}",
                    user.getUsername(),
                    s.domain(),
                    e.toString());
        }
    }

    /** Finds the primary server — first one labelled "A", falling back to index 0. */
    public Optional<JabberProperties.Server> primary() {
        if (props.servers() == null || props.servers().isEmpty()) return Optional.empty();
        return props.servers().stream()
                .filter(s -> "A".equalsIgnoreCase(s.label()))
                .findFirst()
                .or(() -> Optional.of(props.servers().get(0)));
    }

    /** @return true if this username can be bridged to XMPP. Used by UI. */
    public boolean isJidSafe(String username) {
        return username != null && SAFE_LOCALPART.matcher(username).matches();
    }

    private String randomPassword() {
        byte[] bytes = new byte[PASSWORD_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void callAdminApi(JabberProperties.Server s, String path, Map<String, String> body) {
        String basic = Base64.getEncoder()
                .encodeToString(
                        (s.adminJid() + ":" + s.adminPassword()).getBytes(StandardCharsets.UTF_8));
        http.post()
                .uri(s.httpUrl() + path)
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(toJson(body))
                .retrieve()
                .toBodilessEntity();
    }

    private static String toJson(Map<String, String> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : body.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(escape(e.getValue())).append('"');
        }
        return sb.append("}").toString();
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
