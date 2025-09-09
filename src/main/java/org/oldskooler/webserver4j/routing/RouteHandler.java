package org.oldskooler.webserver4j.routing;

import org.oldskooler.webserver4j.http.HttpContext;
import org.oldskooler.webserver4j.controller.ActionResult;

/**
 * Functional handler for registered routes.
 */
@FunctionalInterface
public interface RouteHandler {
    ActionResult handle(HttpContext ctx) throws Exception;
}
