package org.oldskooler.webserver4j.http;

import com.google.gson.Gson;
import org.oldskooler.webserver4j.controller.ActionResult;
import org.oldskooler.webserver4j.session.Session;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The per-request context passed to route handlers and controllers.
 */
public class HttpContext {
    private final HttpRequestData request;
    private final HttpResponseData response;
    private final Session session;
    private final Gson json;

    public HttpContext(HttpRequestData request, HttpResponseData response, Session session, Gson json) {
        this.request = request;
        this.response = response;
        this.session = session;
        this.json = json;
    }

    public HttpRequestData request() {
        return request;
    }

    public HttpResponseData response() {
        return response;
    }

    public Session session() {
        return session;
    }

    public String wildcard(int index) {
        return request.getWildcard(index);
    }

    public List<String> wildcards() {
        return request.getWildcards();
    }

    // Convenience helpers for controllers
    public ActionResult ok(String text) {
        response.setStatus(200);
        response.setContentType("text/plain; charset=UTF-8");
        response.setBody(text.getBytes(StandardCharsets.UTF_8));
        return ActionResult.fromResponse(response);
    }

    public ActionResult html(String text) {
        response.setStatus(200);
        response.setContentType("text/html");
        response.setBody(text.getBytes(StandardCharsets.UTF_8));
        return ActionResult.fromResponse(response);
    }

    public ActionResult json(Object obj) {
        try {
            byte[] bytes = json.toJson(obj).getBytes(StandardCharsets.UTF_8);
            response.setStatus(200);
            response.setContentType("application/json");
            response.setBody(bytes);
            return ActionResult.fromResponse(response);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public ActionResult file(String path) {
        response.setStatus(200);
        response.setFilePath(path);
        return ActionResult.fromResponse(response);
    }

    public ActionResult status(int code, byte[] data, String contentType) {
        response.setStatus(code);
        response.setContentType(contentType);
        response.setBody(data);
        return ActionResult.fromResponse(response);
    }

    public ActionResult status(int code, String text, String contentType) {
        response.setStatus(code);
        response.setContentType(contentType);
        response.setBody(text.getBytes(StandardCharsets.UTF_8));
        return ActionResult.fromResponse(response);
    }

    public ActionResult redirect(String location, int code) {
        response.getHeaders().put("Location", location);
        response.setStatus(code);
        return ActionResult.fromResponse(response);
    }

    public void header(String name, String value) {
        response.getHeaders().put(name, value);
    }

    public void contentType(String contentType) {
        response.setContentType(contentType);
    }

    public void setBody(byte[] bytes) {
        response.setBody(bytes);
    }
}
