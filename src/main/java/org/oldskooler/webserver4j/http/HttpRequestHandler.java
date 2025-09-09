package org.oldskooler.webserver4j.http;

import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.oldskooler.webserver4j.error.ErrorRegistry;
import org.oldskooler.webserver4j.interceptor.InterceptorRegistry;
import org.oldskooler.webserver4j.results.RequestParser;
import org.oldskooler.webserver4j.results.ResponseWriter;
import org.oldskooler.webserver4j.routing.MatchedRoute;
import org.oldskooler.webserver4j.routing.Router;
import org.oldskooler.webserver4j.session.Session;
import org.oldskooler.webserver4j.session.SessionManager;
import org.oldskooler.webserver4j.staticfiles.StaticFileService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles the core HTTP request processing logic.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Parsing HTTP requests into internal data structures</li>
 *   <li>Managing session cookies</li>
 *   <li>Coordinating between interceptors, routing, and static files</li>
 *   <li>Error handling and fallbacks</li>
 * </ul>
 */
public class HttpRequestHandler {
    private final Router router;
    private final InterceptorRegistry interceptors;
    private final ErrorRegistry errors;
    private final StaticFileService staticFiles;
    private final SessionManager sessions;
    private final Gson json = new Gson();

    private final RequestParser requestParser;
    private final ResponseWriter responseWriter;

    public HttpRequestHandler(Router router, InterceptorRegistry interceptors, ErrorRegistry errors,
                              StaticFileService staticFiles, SessionManager sessions) {
        this.router = router;
        this.interceptors = interceptors;
        this.errors = errors;
        this.staticFiles = staticFiles;
        this.sessions = sessions;
        this.requestParser = new RequestParser();
        this.responseWriter = new ResponseWriter(sessions);
    }

    /**
     * Core request handler for HTTP requests.
     * <p>
     * This method coordinates the entire request processing pipeline:
     * interceptors -> routing -> static files -> error handling
     *
     * @param chx Netty channel handler context
     * @param req the incoming HTTP request
     * @throws Exception if processing fails
     */
    public void handle(ChannelHandlerContext chx, FullHttpRequest req) throws Exception {
        String uri = req.uri();
        String path = uri.split("\\?")[0];
        org.oldskooler.webserver4j.http.HttpMethod method = mapMethod(req.method());

        // Parse session from cookies
        Session session = parseSession(req);

        // Parse the full request
        HttpRequestData request = requestParser.parse(req, method, path, router);

        HttpResponseData resp = new HttpResponseData();
        HttpContext ctx = new HttpContext(request, resp, session, json);

        try {
            // Apply interceptors
            if (interceptors.apply(path, ctx)) {
                responseWriter.writeResponse(chx, ctx, req, session, resp);
                return;
            }

            // Try route handler
            Optional<MatchedRoute> matched = router.match(method, path);
            if (matched.isPresent()) {
                matched.get().route.handler.handle(ctx);
                responseWriter.writeResponse(chx, ctx, req, session, resp);
                return;
            }

            // Try static file
            File file = staticFiles.resolve(path);
            if (file != null) {
                responseWriter.sendFile(session, chx, req, file);
                return;
            }

            // Fallback 404
            handle404(ctx, path, resp);
        } catch (Throwable ex) {
            handleException(ctx, resp, ex);
        } finally {
            responseWriter.writeResponse(chx, ctx, req, session, resp);
        }
    }

    private Session parseSession(FullHttpRequest req) {
        Map<String, Cookie> cookies = new HashMap<>();
        String cookieHeader = req.headers().get("Cookie");
        if (cookieHeader != null) {
            Set<Cookie> parsed = ServerCookieDecoder.STRICT.decode(cookieHeader);
            for (Cookie c : parsed) {
                cookies.put(c.name(), c);
            }
        }
        String sessionId = cookies.containsKey("SESSIONID") ? cookies.get("SESSIONID").value() : null;
        return sessions.getOrCreate(sessionId);
    }

    private void handle404(HttpContext ctx, String path, HttpResponseData resp) {
        boolean handled = errors.handleStatus(ctx, 404);
        if (!handled) {
            resp.setStatus(404);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.setBody(("404 Not Found: " + path).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handleException(HttpContext ctx, HttpResponseData resp, Throwable ex) {
        boolean handled = errors.handleException(ctx, ex);
        if (!handled) {
            resp.setStatus(500);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.setBody("Internal Server Error".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Maps a Netty {@link HttpMethod} to the internal {@link org.oldskooler.webserver4j.http.HttpMethod}.
     *
     * @param m Netty HTTP method
     * @return mapped internal HTTP method
     */
    private org.oldskooler.webserver4j.http.HttpMethod mapMethod(HttpMethod m) {
        if (m.equals(HttpMethod.GET)) return org.oldskooler.webserver4j.http.HttpMethod.GET;
        if (m.equals(HttpMethod.POST)) return org.oldskooler.webserver4j.http.HttpMethod.POST;
        if (m.equals(HttpMethod.PUT)) return org.oldskooler.webserver4j.http.HttpMethod.PUT;
        if (m.equals(HttpMethod.DELETE)) return org.oldskooler.webserver4j.http.HttpMethod.DELETE;
        if (m.equals(HttpMethod.PATCH)) return org.oldskooler.webserver4j.http.HttpMethod.PATCH;
        if (m.equals(HttpMethod.HEAD)) return org.oldskooler.webserver4j.http.HttpMethod.HEAD;
        return org.oldskooler.webserver4j.http.HttpMethod.OPTIONS;
    }
}