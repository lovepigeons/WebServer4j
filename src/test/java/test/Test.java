package test;

import org.oldskooler.webserver4j.http.HttpMethod;
import org.oldskooler.webserver4j.server.WebServer;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        WebServer server = new WebServer(8080, "wwwroot");
        server.routes().map(HttpMethod.GET, "/health", ctx -> ctx.html("OK"));
        server.addControllers();
        server.start();

    }
}