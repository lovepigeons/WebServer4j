package test;

import org.oldskooler.webserver4j.http.HttpMethod;
import org.oldskooler.webserver4j.server.WebServer;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        WebServer server = new WebServer.Builder()
                .sslSelfSigned()
                .build();

        server.routes().map(HttpMethod.GET, "/health", ctx -> ctx.html("OK"));
        server.addControllers();
        server.start();

    }
}