package org.oldskooler.webserver4j.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client session storage backed by an in-memory map.
 * The session persists across requests via the SESSIONID cookie.
 */
public class Session {
    private final String id;
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public Session(String id) {
        this.id = id;
    }

    /** @return session id */
    public String getId() { return id; }

    /** @return mutable attribute map */
    public Map<String, Object> data() { return data; }

    public Object get(String key) { return data.get(key); }
    public void set(String key, Object value) { data.put(key, value); }
    public void remove(String key) { data.remove(key); }
}
