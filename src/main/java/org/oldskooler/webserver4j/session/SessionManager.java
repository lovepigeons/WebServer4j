package org.oldskooler.webserver4j.session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SESSIONID -> Session mappings with expiration.
 */
public class SessionManager {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> expiries = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public SessionManager(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /** Get or create session by id. */
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

    /** Create a brand new session. */
    public Session create() {
        return getOrCreate(null);
    }

    /** @return SESSIONID for provided session. */
    public String ensureId(Session s) {
        return s.getId();
    }

    /** Cleanup expired sessions. */
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
