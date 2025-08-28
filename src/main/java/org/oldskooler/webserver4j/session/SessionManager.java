package org.oldskooler.webserver4j.session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user sessions with automatic expiration support.
 * <p>
 * This class provides thread-safe session creation, retrieval, and cleanup.
 * Each session is identified by a unique ID string and has a configurable
 * time-to-live (TTL). Expired sessions are removed on {@link #sweep()}.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SessionManager manager = new SessionManager(30_000); // 30s TTL
 * Session s = manager.getOrCreate("someId");
 * String id = manager.ensureId(s);
 * manager.sweep(); // periodically remove expired sessions
 * }</pre>
 */
public class SessionManager {

    /** Active sessions keyed by their session ID. */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** Expiry timestamps (epoch millis) keyed by session ID. */
    private final Map<String, Long> expiries = new ConcurrentHashMap<>();

    /** Time-to-live for sessions, in milliseconds. */
    private final long ttlMillis;

    /**
     * Constructs a new {@code SessionManager}.
     *
     * @param ttlMillis the time-to-live for each session, in milliseconds
     */
    public SessionManager(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Retrieves an existing session by ID or creates a new one if it does not exist.
     * <p>
     * If the given {@code id} is {@code null}, empty, or not present in the session map,
     * a new session will be created with a random UUID-based ID. Otherwise, the
     * session is returned and its expiry is refreshed.
     * </p>
     *
     * @param id the session ID, may be {@code null} or empty
     * @return the existing or newly created {@link Session}
     */
    public Session getOrCreate(String id) {
        if (id == null || id.isEmpty() || !sessions.containsKey(id)) {
            String newId = UUID.randomUUID().toString().replace("-", "");
            Session session = new Session(newId);
            sessions.put(newId, session);
            expiries.put(newId, System.currentTimeMillis() + ttlMillis);
            return session;
        }
        // refresh expiry
        expiries.put(id, System.currentTimeMillis() + ttlMillis);
        return sessions.get(id);
    }

    /**
     * Creates a new session with a fresh ID.
     *
     * @return a new {@link Session} instance
     */
    public Session create() {
        return getOrCreate(null);
    }

    /**
     * Ensures that the provided session has an ID and returns it.
     *
     * @param s the session object, must not be {@code null}
     * @return the ID of the given session
     */
    public String ensureId(Session s) {
        return s.getId();
    }

    /**
     * Cleans up expired sessions.
     * <p>
     * Iterates over all stored sessions and removes those whose expiry time
     * is earlier than the current system time.
     * </p>
     */
    public void sweep() {
        long now = Instant.now().toEpochMilli();
        expiries.entrySet().removeIf(e -> {
            if (e.getValue() < now) {
                String id = e.getKey();
                sessions.remove(id);
                return true;
            }
            return false;
        });
    }
}
