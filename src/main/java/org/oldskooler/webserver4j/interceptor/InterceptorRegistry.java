package org.oldskooler.webserver4j.interceptor;

import org.oldskooler.webserver4j.http.HttpContext;
import org.oldskooler.webserver4j.routing.PathPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Registry mapping path patterns to interceptors.
 */
public class InterceptorRegistry {
    private static class Entry {
        final PathPattern pattern;
        final RequestInterceptor interceptor;
        Entry(PathPattern p, RequestInterceptor i) { pattern = p; interceptor = i; }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(String template, RequestInterceptor interceptor) {
        entries.add(new Entry(PathPattern.compile(template), interceptor));
    }

    public boolean apply(String path, HttpContext ctx) throws Exception {
        for (Entry e : entries) {
            Matcher m = e.pattern.regex().matcher(path);
            if (m.matches()) {
                if (e.interceptor.preHandle(ctx)) return true;
            }
        }
        return false;
    }
}
