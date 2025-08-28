package org.oldskooler.webserver4j.staticfiles;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal MIME type mapping used by the static file handler.
 */
public final class MimeTypes {
    private static final Map<String,String> map = new HashMap<>();
    static {
        map.put("html", "text/html; charset=UTF-8");
        map.put("htm", "text/html; charset=UTF-8");
        map.put("css", "text/css; charset=UTF-8");
        map.put("js", "application/javascript");
        map.put("json", "application/json");
        map.put("png", "image/png");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("gif", "image/gif");
        map.put("svg", "image/svg+xml");
        map.put("txt", "text/plain; charset=UTF-8");
        map.put("ico", "image/x-icon");
        map.put("pdf", "application/pdf");
    }
    public static String get(String ext) {
        return map.getOrDefault(ext.toLowerCase(), "application/octet-stream");
    }
}
