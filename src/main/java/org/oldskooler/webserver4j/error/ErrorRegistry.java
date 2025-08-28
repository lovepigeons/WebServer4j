package org.oldskooler.webserver4j.error;

import org.oldskooler.webserver4j.http.HttpContext;

import java.util.ArrayList;
import java.util.List;

public class ErrorRegistry {
    private final List<HttpErrorHandler> handlers = new ArrayList<>();

    /** Add a global error handler. Order matters (first-match wins). */
    public void add(HttpErrorHandler handler) {
        handlers.add(handler);
    }

    /** Try to handle a status-based error (e.g., 404, 403, 500). */
    public boolean handleStatus(HttpContext ctx, int status) {
        for (HttpErrorHandler h : handlers) {
            try {
                if (h.handle(ctx, status, null)) return true;
            } catch (Exception ignored) { /* fall through to next */ }
        }
        return false;
    }

    /** Try to handle an exception (maps naturally to 500). */
    public boolean handleException(HttpContext ctx, Throwable error) {
        for (HttpErrorHandler h : handlers) {
            try {
                if (h.handle(ctx, 0, error)) return true;
            } catch (Exception ignored) { /* fall through to next */ }
        }
        return false;
    }
}