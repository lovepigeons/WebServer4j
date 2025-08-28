package org.oldskooler.webserver4j.http;

import io.netty.handler.codec.http.cookie.Cookie;

import java.util.*;

/**
 * Immutable snapshot of the incoming HTTP request used by controllers.
 */
public class HttpRequestData {
    private final HttpMethod method;
    private final String path;
    private final Map<String, String> routeParams;
    private final QueryParams query;
    private final QueryParams form;
    private final List<UploadedFile> files;
    private final Map<String, Cookie> cookies;
    private final byte[] rawBody;
    private final String contentType;
    private final List<String> wildcards;

    public HttpRequestData(HttpMethod method, String path,
                           Map<String, String> routeParams,
                           QueryParams query, QueryParams form,
                           List<UploadedFile> files,
                           Map<String, Cookie> cookies,
                           byte[] rawBody,
                           String contentType,
                           List<String> wildcards) {
        this.method = method;
        this.path = path;
        this.routeParams = Collections.unmodifiableMap(new HashMap<>(routeParams));
        this.query = query;
        this.form = form;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.cookies = Collections.unmodifiableMap(new HashMap<>(cookies));
        this.rawBody = rawBody;
        this.contentType = contentType;
        this.wildcards = Collections.unmodifiableList(new java.util.ArrayList<>(wildcards));
    }

    /**
     * Wildcard captures (positional): w0, w1, ...
     */
    public java.util.List<String> getWildcards() {
        return wildcards;
    }

    /**
     * Convenience: return wildcard at index or null.
     */
    public String getWildcard(int index) {
        return (index >= 0 && index < wildcards.size()) ? wildcards.get(index) : null;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getRouteParams() {
        return routeParams;
    }

    public QueryParams getQuery() {
        return query;
    }

    public QueryParams getForm() {
        return form;
    }

    public List<UploadedFile> getFiles() {
        return files;
    }

    public Map<String, Cookie> getCookies() {
        return cookies;
    }

    public byte[] getRawBody() {
        return rawBody;
    }

    public String getContentType() {
        return contentType;
    }
}