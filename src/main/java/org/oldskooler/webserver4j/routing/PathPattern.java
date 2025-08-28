package org.oldskooler.webserver4j.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts ASP.NET-style route templates (e.g., "/users/{id}", "/files/**") to regex patterns.
 * Supports named params {name}, single segment wildcard * and multi-segment wildcard **.
 */
public final class PathPattern {
    private final Pattern regex;
    /**
     * Named groups for {...} route params.
     */
    private final List<String> routeParamGroupNames;
    /**
     * Named groups for wildcards in positional order (w0, w1, ...).
     */
    private final List<String> wildcardGroupNames;

    private PathPattern(Pattern regex, List<String> routeParamGroupNames, List<String> wildcardGroupNames) {
        this.regex = regex;
        this.routeParamGroupNames = routeParamGroupNames;
        this.wildcardGroupNames = wildcardGroupNames;
    }

    public static PathPattern compile(String template) {
        String[] parts = template.split("/");
        StringBuilder sb = new StringBuilder();
        List<String> routeNames = new ArrayList<>();
        List<String> wildcardNames = new ArrayList<>();
        int wIndex = 0;
        sb.append("^");
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append("/");
            if (p.equals("**")) {
                String wn = "w" + (wIndex++);
                sb.append("(?<").append(wn).append(">.*)");
                wildcardNames.add(wn);
            } else if (p.equals("*")) {
                String wn = "w" + (wIndex++);
                sb.append("(?<").append(wn).append(">[^/]+)");
                wildcardNames.add(wn);
            } else if (p.startsWith("{") && p.endsWith("}")) {
                String name = p.substring(1, p.length() - 1);
                routeNames.add(name);
                sb.append("(?<").append(name).append(">[^/]+)");
            } else {
                sb.append(Pattern.quote(p));
            }
        }
        sb.append("/?$");
        return new PathPattern(Pattern.compile(sb.toString()), routeNames, wildcardNames);
    }

    public Pattern regex() {
        return regex;
    }

    public List<String> routeParamGroupNames() {
        return routeParamGroupNames;
    }

    public List<String> wildcardGroupNames() {
        return wildcardGroupNames;
    }

    /**
     * Extract named groups from a regex matcher into a map.
     */
    public Map<String, String> extractRouteParams(Matcher m) {
        Map<String, String> map = new HashMap<>();
        for (String g : routeParamGroupNames) {
            try {
                String v = m.group(g);
                if (v != null) map.put(g, v);
            } catch (IllegalArgumentException ex) {
                // ignore
            }
        }
        return map;
    }

    /**
     * Extract wildcard captures in positional order.
     */
    public List<String> extractWildcards(Matcher m) {
        List<String> out = new ArrayList<>();
        for (String g : wildcardGroupNames) {
            try {
                String v = m.group(g);
                if (v != null) out.add(v);
            } catch (IllegalArgumentException ex) {
                // ignore
            }
        }
        return out;
    }
}
