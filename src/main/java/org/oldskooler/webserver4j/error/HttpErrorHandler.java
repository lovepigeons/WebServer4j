package org.oldskooler.webserver4j.error;

import org.oldskooler.webserver4j.http.HttpContext;

/**
 * Global error handler hook.
 *
 * Implementations may handle:
 *  - status-driven errors (e.g., 404), where {@code error == null}
 *  - exception-driven errors (e.g., unhandled exceptions), where {@code status == 500} (or 0) and {@code error != null}
 *
 * Return {@code true} if you've produced a response (status/body/headers),
 * in which case the server will not apply its default error response.
 */
@FunctionalInterface
public interface HttpErrorHandler {
    /**
     * @param ctx    current request context
     * @param status HTTP status (for status-driven errors) or 0 if unknown/exception path
     * @param error  the thrown exception (if any) or {@code null} for status-driven errors
     * @return true if handled (response already set); false to allow next handler / fall back
     */
    boolean handle(HttpContext ctx, int status, Throwable error) throws Exception;
}