package test;

import org.oldskooler.webserver4j.controller.Controller;
import org.oldskooler.webserver4j.controller.FromRoute;
import org.oldskooler.webserver4j.controller.HttpGet;
import org.oldskooler.webserver4j.http.HttpContext;
import org.oldskooler.webserver4j.controller.ActionResult;

import java.util.HashMap;
import java.util.Map;

@Controller(route = "/home")
public class HomeController {
    @HttpGet("/greet/{name}")
    public ActionResult greet(HttpContext ctx, @FromRoute("name") String name) {
        Map<String, String> payload = new HashMap<String, String>();
        payload.put("message", "Hello " + name);
        return ctx.json(payload);
    }
}
