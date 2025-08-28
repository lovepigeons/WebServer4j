package org.oldskooler.webserver4j.routing;

import org.oldskooler.webserver4j.http.HttpMethod;

import java.util.*;
import java.util.regex.Matcher;

/**
 * Router that supports explicit route registration and wildcard templates.
 */
public class Router {
    private final List<RouteDefinition> routes = new ArrayList<>();

    public void map(HttpMethod method, String template, RouteHandler handler) {
        PathPattern pp = PathPattern.compile(template);
        routes.add(new RouteDefinition(method, template, pp, pp.regex(), handler));
    }

    public Optional<MatchedRoute> match(HttpMethod method, String path) {
        for (RouteDefinition r : routes) {
            if (r.method != method) continue;
            Matcher m = r.pattern.matcher(path);
            if (m.matches()) {
                Map<String, String> params = r.compiled.extractRouteParams(m);
                List<String> wilds = r.compiled.extractWildcards(m);
                return Optional.of(new MatchedRoute(r, params, wilds));
            }
        }
        return Optional.empty();
    }

    public List<RouteDefinition> getRoutes() {
        return Collections.unmodifiableList(this.routes);
    }
}
