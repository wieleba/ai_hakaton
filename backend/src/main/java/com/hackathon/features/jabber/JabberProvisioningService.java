package com.hackathon.features.jabber;

import com.hackathon.features.users.User;
import com.hackathon.features.users.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
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
import org.springframework.web.client.HttpClientErrorException;
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
            registerOrRotate(s, localpart, password);
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
     * Backfill XMPP provisioning for every Chat user whose username is
     * JID-safe and whose {@code xmpp_password} is currently NULL. Used on
     * startup to catch users created before V14 (the xmpp_password column)
     * or while the Jabber feature was disabled.
     *
     * <p>For accounts already present in ejabberd (created out-of-band)
     * this rotates the XMPP password to a freshly-generated one so the
     * backend can log Smack in. Any XMPP client session still using the
     * old password will be kicked off on its next reconnect — users see
     * the new credentials on the Account-settings page.
     *
     * @return number of users successfully provisioned in this pass
     */
    public int backfillAllUnprovisioned() {
        Optional<JabberProperties.Server> primary = primary();
        if (primary.isEmpty()) return 0;
        JabberProperties.Server s = primary.get();
        List<User> candidates = userRepository.findAll().stream()
                .filter(u -> u.getXmppPassword() == null)
                .filter(u -> u.getUsername() != null && SAFE_LOCALPART.matcher(u.getUsername()).matches())
                .toList();
        if (candidates.isEmpty()) return 0;
        log.info("JabberProvisioning: backfilling {} unprovisioned user(s)", candidates.size());
        int ok = 0;
        for (User user : candidates) {
            String password = randomPassword();
            try {
                registerOrRotate(s, user.getUsername(), password);
                user.setXmppPassword(password);
                userRepository.save(user);
                eventPublisher.publishEvent(new JabberEvents.UserProvisioned(user.getId()));
                ok++;
                // Minimal throttle to avoid hammering ejabberd on a fresh
                // backend start; keeps startup time predictable.
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("JabberProvisioning: backfill interrupted after {} users", ok);
                return ok;
            } catch (RuntimeException e) {
                log.warn(
                        "JabberProvisioning: backfill skipped {}: {}",
                        user.getUsername(),
                        e.toString());
            }
        }
        log.info("JabberProvisioning: backfill complete — {}/{}", ok, candidates.size());
        return ok;
    }

    /**
     * Create the XMPP account or, if it already exists in ejabberd, rotate
     * its password. Either way, after this call the given localpart is live
     * on the server with the given password.
     */
    private void registerOrRotate(JabberProperties.Server s, String localpart, String password) {
        try {
            callAdminApi(s, "/api/register", Map.of(
                    "user", localpart,
                    "host", s.domain(),
                    "password", password));
        } catch (HttpClientErrorException.Conflict alreadyExists) {
            log.debug(
                    "JabberProvisioning: {}@{} exists in ejabberd; rotating password",
                    localpart,
                    s.domain());
            callAdminApi(s, "/api/change_password", Map.of(
                    "user", localpart,
                    "host", s.domain(),
                    "newpass", password));
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
