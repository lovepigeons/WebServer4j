package org.oldskooler.webserver4j.routing;

import org.oldskooler.webserver4j.http.HttpMethod;

import java.util.regex.Pattern;

/**
 * A compiled route definition with HTTP method and path pattern.
 */
public class RouteDefinition {
    public final HttpMethod method;
    public final String template;
    public final Pattern pattern;
    public final PathPattern compiled;
    public final RouteHandler handler;

    public RouteDefinition(HttpMethod method, String template, PathPattern compiled, Pattern pattern, RouteHandler handler) {
        this.method = method;
        this.template = template;
        this.compiled = compiled;
        this.pattern = pattern;
        this.handler = handler;
    }
}