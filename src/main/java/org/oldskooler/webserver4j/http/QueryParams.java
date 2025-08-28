package org.oldskooler.webserver4j.http;

import java.util.*;

/**
 * Read-only map-like wrapper for query/form parameters.
 */
public class QueryParams {
    private final Map<String, List<String>> data;

    public QueryParams(Map<String, List<String>> data) {
        this.data = new HashMap<>();
        data.forEach((k,v) -> this.data.put(k, Collections.unmodifiableList(new ArrayList<>(v))));
    }

    /** Returns the first value for a key, or null. */
    public String get(String key) {
        List<String> list = data.get(key);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /** Returns all values for a key (possibly empty). */
    public List<String> getAll(String key) {
        return data.getOrDefault(key, Collections.emptyList());
    }

    /** @return an unmodifiable view of the raw map. */
    public Map<String, List<String>> asMap() {
        return Collections.unmodifiableMap(data);
    }
}
