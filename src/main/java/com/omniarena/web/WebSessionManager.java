package com.omniarena.web;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSessionManager {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private int maxSessions;

    public WebSessionManager(Duration ttl, int maxSessions) {
        this.ttl = ttl;
        this.maxSessions = maxSessions;
    }

    /**
     * @return token, or null if at session limit
     */
    public String createSession() {
        cleanupExpired();
        if (maxSessions > 0 && sessions.size() >= maxSessions) return null;
        String token = nextToken();
        sessions.put(token, new Session(System.currentTimeMillis() + ttl.toMillis()));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        Session session = sessions.get(token);
        if (session == null) return false;
        if (session.expiresAtMs() < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }

    public void killAll() {
        sessions.clear();
    }

    public int activeCount() {
        cleanupExpired();
        return sessions.size();
    }

    public void setMaxSessions(int max) {
        this.maxSessions = max;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAtMs() < now);
    }

    private String nextToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Session(long expiresAtMs) {}
}
