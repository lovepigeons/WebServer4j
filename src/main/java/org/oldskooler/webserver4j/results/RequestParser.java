package org.oldskooler.webserver4j.results;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.multipart.*;
import org.oldskooler.webserver4j.http.HttpRequestData;
import org.oldskooler.webserver4j.http.QueryParams;
import org.oldskooler.webserver4j.http.UploadedFile;
import org.oldskooler.webserver4j.routing.MatchedRoute;
import org.oldskooler.webserver4j.routing.Router;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses incoming HTTP requests into structured data.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Cookie parsing</li>
 *   <li>Header extraction</li>
 *   <li>Query parameter parsing</li>
 *   <li>Form data parsing (URL-encoded and multipart)</li>
 *   <li>File upload handling</li>
 *   <li>Route parameter extraction</li>
 * </ul>
 */
public class RequestParser {

    /**
     * Parses a Netty FullHttpRequest into our internal HttpRequestData structure.
     *
     * @param req    the Netty HTTP request
     * @param method the parsed HTTP method
     * @param path   the request path
     * @param router the router for extracting route parameters
     * @return parsed request data
     * @throws Exception if parsing fails
     */
    public HttpRequestData parse(FullHttpRequest req,
                                 org.oldskooler.webserver4j.http.HttpMethod method,
                                 String path,
                                 Router router) throws Exception {

        // Parse cookies
        Map<String, Cookie> cookies = parseCookies(req);

        // Parse headers
        Map<String, String> headers = parseHeaders(req);

        // Parse query parameters
        Map<String, List<String>> queryMap = parseQueryParams(req);

        // Parse form data and files
        FormParseResult formResult = parseFormData(req);

        // Extract route parameters
        RouteParseResult routeResult = parseRouteParams(method, path, router);

        return new HttpRequestData(
                method,
                path,
                routeResult.routeParams,
                new QueryParams(queryMap),
                new QueryParams(formResult.formMap),
                formResult.files,
                cookies,
                headers,
                formResult.rawBody,
                formResult.contentType,
                routeResult.wildcardParts
        );
    }

    private Map<String, Cookie> parseCookies(FullHttpRequest req) {
        Map<String, Cookie> cookies = new HashMap<>();
        String cookieHeader = req.headers().get(HttpHeaderNames.COOKIE);
        if (cookieHeader != null) {
            Set<Cookie> parsed = ServerCookieDecoder.STRICT.decode(cookieHeader);
            for (Cookie c : parsed) {
                cookies.put(c.name(), c);
            }
        }
        return cookies;
    }

    private Map<String, String> parseHeaders(FullHttpRequest req) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : req.headers()) {
            headers.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    private Map<String, List<String>> parseQueryParams(FullHttpRequest req) {
        QueryStringDecoder qd = new QueryStringDecoder(req.uri(), StandardCharsets.UTF_8);
        return new HashMap<>(qd.parameters());
    }

    private FormParseResult parseFormData(FullHttpRequest req) throws Exception {
        Map<String, List<String>> formMap = new HashMap<>();
        List<UploadedFile> files = new ArrayList<>();
        byte[] rawBody = new byte[0];
        String contentType = req.headers().get(HttpHeaderNames.CONTENT_TYPE);
        String actualContentType = contentType == null ? "" : contentType;

        if (req.method().equals(HttpMethod.POST) ||
                req.method().equals(HttpMethod.PUT) ||
                req.method().equals(HttpMethod.PATCH)) {

            rawBody = new byte[req.content().readableBytes()];
            req.content().readBytes(rawBody);

            if (contentType != null &&
                    contentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())) {
                parseUrlEncodedForm(rawBody, formMap);
            } else if (contentType != null &&
                    contentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString())) {
                parseMultipartForm(req, formMap, files);
            }
        }

        return new FormParseResult(formMap, files, rawBody, actualContentType);
    }

    private void parseUrlEncodedForm(byte[] rawBody, Map<String, List<String>> formMap) {
        QueryStringDecoder decoder = new QueryStringDecoder(new String(rawBody, StandardCharsets.UTF_8), false);
        formMap.putAll(decoder.parameters());
    }

    private void parseMultipartForm(FullHttpRequest req, Map<String, List<String>> formMap,
                                    List<UploadedFile> files) throws Exception {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(true), req);
        try {
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute attr = (Attribute) data;
                    formMap.computeIfAbsent(attr.getName(), k -> new ArrayList<>()).add(attr.getValue());
                } else if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    FileUpload fu = (FileUpload) data;
                    if (fu.isCompleted()) {
                        File tmp = File.createTempFile("upload_", "_" + fu.getFilename());
                        fu.renameTo(tmp);
                        files.add(new UploadedFile(fu.getName(), fu.getFilename(), fu.getContentType(), tmp));
                    }
                }
            }
        } finally {
            decoder.destroy();
        }
    }

    private RouteParseResult parseRouteParams(org.oldskooler.webserver4j.http.HttpMethod method,
                                              String path, Router router) {
        Optional<MatchedRoute> matched = router.match(method, path);

        Map<String, String> routeParams = new HashMap<>();
        List<String> wildcardParts = new ArrayList<>();

        matched.ifPresent(m -> {
            routeParams.putAll(m.params);
            wildcardParts.addAll(m.wildcards);
        });

        return new RouteParseResult(routeParams, wildcardParts);
    }

    private static class FormParseResult {
        final Map<String, List<String>> formMap;
        final List<UploadedFile> files;
        final byte[] rawBody;
        final String contentType;

        FormParseResult(Map<String, List<String>> formMap, List<UploadedFile> files,
                        byte[] rawBody, String contentType) {
            this.formMap = formMap;
            this.files = files;
            this.rawBody = rawBody;
            this.contentType = contentType;
        }
    }

    private static class RouteParseResult {
        final Map<String, String> routeParams;
        final List<String> wildcardParts;

        RouteParseResult(Map<String, String> routeParams, List<String> wildcardParts) {
            this.routeParams = routeParams;
            this.wildcardParts = wildcardParts;
        }
    }
}