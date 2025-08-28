package org.oldskooler.webserver4j.interceptor;

import org.oldskooler.webserver4j.http.HttpContext;

/**
 * Interceptor that can short-circuit handling by returning false.
 */
public interface RequestInterceptor {
    /**
     * Invoked before a route is handled.
     * @return true to continue, false to stop (response already set).
     */
    boolean preHandle(HttpContext ctx) throws Exception;
}
