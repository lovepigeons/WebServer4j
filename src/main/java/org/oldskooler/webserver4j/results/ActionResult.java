package org.oldskooler.webserver4j.results;

import org.oldskooler.webserver4j.http.HttpResponseData;

/**
 * Marker type returned by controllers. Wraps an HttpResponseData instance.
 */
public class ActionResult {
    private final HttpResponseData response;

    public ActionResult(HttpResponseData response) {
        this.response = response;
    }

    public HttpResponseData getResponse() { return response; }

    public static ActionResult fromResponse(HttpResponseData response) {
        return new ActionResult(response);
    }
}
