# WebServer4J

A lightweight, Netty-based MVC style web server for Java. It favors practical conventions inspired by ASP.NET Core, including constructor injection, controller auto discovery, sessions, file uploads, interceptors, templating, and both explicit and attribute-based routing.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [User Guide](#user-guide)
  - [Routing](#routing)
  - [Controllers and Auto Discovery](#controllers-and-auto-discovery)
  - [Reading Parameters](#reading-parameters)
  - [Sessions](#sessions)
  - [File Uploads](#file-uploads)
  - [Static Files](#static-files)
  - [Interceptors](#interceptors)
  - [Error Handling](#error-handling)
  - [Templating](#templating)
  - [Custom Responses](#custom-responses)
  - [Wildcards in Routes](#wildcards-in-routes)
  - [Explicit Routes](#explicit-routes)
  - [Dependency Injection](#dependency-injection)
- [Advanced Examples](#advanced-examples)
- [FAQ](#faq)
- [License](#license)

## Overview

WebServer4J gives you two ways to build HTTP apps:

- **Explicit routes** for small or microservice style endpoints
- **Annotated controllers** for larger applications with clean separation of concerns

It supports serving static assets, reading query and form data, binding route and session values, streaming files, returning JSON, and issuing redirects. It also includes a simple template engine interface that you can replace.

## Installation

Add the dependency to your build.

```xml
<dependency>
  <groupId>org.oldskooler</groupId>
  <artifactId>webserver4j</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quick Start

A minimal server with a single route and static file root:

```java
var server = new WebServer(8080, "wwwroot", services);
server.routes().map(HttpMethod.GET, "/health", ctx -> ctx.ok("OK"));
server.start();
```

Place an `index.html` in `wwwroot` to serve a landing page.

## User Guide

### Routing

Register explicit routes without controllers. Useful for microservices, probes, and utilities.

```java
server.routes().map(HttpMethod.GET, "/status", ctx -> ctx.ok("running"));
server.routes().map(HttpMethod.POST, "/echo", ctx -> {
    return ctx.ok(ctx.request().getBodyAsString());
});
```

### Controllers and Auto Discovery

Controllers are annotated classes whose methods map to routes. Auto discovery scans a package and wires everything based on annotations and constructor injection.

```java
// During startup
server.addControllers("com.example.app.controllers");

// Or scan everywhere (all packages)
server.addControllers();
```

```java
import org.oldskooler.webserver4j.controller.*;

@Controller(route = "/home")
public class HomeController {
    @HttpGet("/greet/{name}")
    public ActionResult greet(HttpContext ctx, @FromRoute("name") String name) {
        return ctx.json(Map.of("message", "Hello " + name));
    }
}
```

### Reading Parameters

Bind values from different sources using annotations.

```java
@HttpGet("/items/{id}")
public ActionResult get(
    HttpContext ctx,
    @FromRoute("id") long id,
    @FromQuery("expand") String expand) {
    return ctx.json(Map.of("id", id, "expand", expand));
}

@HttpPost("/profile")
public ActionResult update(
    HttpContext ctx,
    @FromForm("email") String email,
    @FromForm("phone") String phone) {
    return ctx.json(Map.of("email", email, "phone", phone));
}

@HttpGet("/me")
public ActionResult me(HttpContext ctx, @FromSession("user") String user) {
    if (user == null) {
        ctx.response().setStatus(401);
        return ctx.json(Map.of("authenticated", false));
    }
    return ctx.json(Map.of("authenticated", true, "user", user));
}
```

### Sessions

Sessions are persisted using a SESSIONID cookie. You can access a per-session map.

```java
// Increment a counter
@HttpGet("/counter")
public ActionResult counter(HttpContext ctx) {
    int n = (int) ctx.session().data().getOrDefault("count", 0);
    ctx.session().data().put("count", n + 1);
    return ctx.json(Map.of("count", n + 1));
}

// Set and read user
ctx.session().data().put("user", "alice");
String user = (String) ctx.session().data().get("user");
```

### File Uploads

Handle multipart/form-data and read uploaded parts.

```java
@HttpPost("/upload")
public ActionResult upload(HttpContext ctx) {
    int fileCount = ctx.request().getFiles().size();
    return ctx.json(Map.of("files", fileCount));
}
```

### Static Files

Static assets are served from the directory given to WebServer. Use this for HTML, CSS, JS, and images.

```java
WebServer server = new WebServer(8080, "wwwroot", services);
```

### Interceptors

Interceptors run before the route handler. They are ideal for logging, authentication, and cross-cutting headers.

```java
// Global header
server.interceptors().add("/**", ctx -> {
    ctx.header("X-Powered-By", "WebServer4J");
    return false;
});

// Protect everything under /api
server.interceptors().add("/api/**", ctx -> {
    if (ctx.session().data().get("user") == null) {
        ctx.response().setStatus(401);
        ctx.ok("Unauthorized");
        return true; // stop processing
    }
    return false;
});
```

### Error Handling

Add error handlers that can transform exceptions or status codes into responses.

```java
server.errors().add((ctx, status, error) -> {
    if (error != null) {
        ctx.response().setStatus(500);
        ctx.ok("Internal error: " + error.getMessage());
        return true;
    }
    return false;
});

server.errors().add((ctx, status, error) -> {
    if (status == 404) {
        ctx.html("<h1>Page not found</h1>");
        return true;
    }
    return false;
});
```

Return `true` to indicate the error was handled.

### Templating

Use the provided TemplateEngine interface to render dynamic views. A simple implementation is available and can be replaced.

The TemplateEngine interface provides two methods:
- `render()` for inline template strings
- `file()` for template files

Register the engine during startup:

```java
services.addSingleton(TemplateEngine.class, SimpleTemplateEngine.class);
```

Example controller with constructor-injected TemplateEngine:

```java
@Controller(route = "/pages")
public class PageController {
    private final TemplateEngine templates;

    public PageController(TemplateEngine templates) {
        this.templates = templates;
    }

    @HttpGet("/view")
    public ActionResult view(HttpContext ctx, @FromQuery("name") String name) {
        Map<String,Object> model = new HashMap<>();
        model.put("name", name == null ? "world" : name);
        
        String html = templates.render("<h1>Hello {{name}}</h1>", model);
        return ctx.html(html);
    }

    @HttpGet("/profile")
    public ActionResult profile(HttpContext ctx, @FromSession("user") String user) {
        Map<String,Object> model = new HashMap<>();
        model.put("user", user);
        model.put("title", "User Profile");
        
        String html = templates.file("profile.html", model);
        return ctx.html(html);
    }
}
```

### Custom Responses

Return JSON, text, bytes, redirects, and arbitrary HTTP status codes.

```java
ctx.json(Map.of("ok", true));
ctx.ok("plain text");

ctx.response().setStatus(418);
ctx.ok("teapot");

return ctx.redirect("/home/view?name=site", 302);

// Send bytes
ctx.contentType("application/octet-stream");
ctx.setBody(bytes);
return ActionResult.fromResponse(ctx.response());
```

### Wildcards in Routes

You can declare routes with wildcards to capture parts of the URL path without naming them explicitly.

#### `*` single-segment wildcard

Matches exactly one path segment (the part of the URL between slashes).

Example:

```java
@HttpGet("/article/*/comment/*")
public ActionResult show(HttpContext ctx) {
    String articleId = ctx.wildcard(0); // "421"
    String commentId = ctx.wildcard(1); // "33"
    return ctx.json(Map.of("articleId", articleId, "commentId", commentId));
}
```

Examples of matching:
* `/article/421/comment/33` matches, wildcards = `["421", "33"]`
* `/article/421/comment/33/extra` does not match (too many segments)

#### `**` multi-segment (catch-all) wildcard

Matches zero or more segments, including embedded slashes.
Useful for catch-all scenarios such as serving static files, downloads, or single-page app fallbacks.

Example:

```java
@HttpGet("/files/**")
public ActionResult files(HttpContext ctx) {
    String path = ctx.wildcard(0);
    return ctx.ok("Requested file path: " + path);
}
```

Examples of matching:
* `/files/foo.txt` wildcards = `["foo.txt"]`
* `/files/images/2025/08/28/photo.jpg` wildcards = `["images/2025/08/28/photo.jpg"]`

#### Accessing wildcards

* Use `ctx.wildcard(index)` to get a specific wildcard value
* Use `ctx.wildcards()` to get all captured values as a list
* Wildcards are positional: the first `*` or `**` in your route is index 0, the next is index 1, and so on

### Explicit Routes

Wildcards can also be used in interceptors and explicit routes.

```java
server.interceptors().add("/assets/**", ctx -> {
    ctx.header("Cache-Control", "public, max-age=3600");
    return false;
});

server.routes().map(HttpMethod.GET, "/metrics/**", ctx -> ctx.ok("metrics endpoint"));
```

### Dependency Injection

WebServer4J supports constructor injection through java-di. Services are registered in a ServiceCollection and resolved when controllers are created.

You can register interfaces with their implementations:

```java
public interface GreeterService {
    String greet(String name);
}

public class DefaultGreeterService implements GreeterService {
    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}

// During startup
ServiceCollection services = new ServiceCollection();
services.addSingleton(GreeterService.class, DefaultGreeterService.class);

WebServer server = new WebServer(8080, "wwwroot", services);
```

Controllers can then request the interface:

```java
@Controller(route = "/greet")
public class GreetController {
    private final GreeterService greeter;

    public GreetController(GreeterService greeter) {
        this.greeter = greeter; // injected as DefaultGreeterService
    }

    @HttpGet("/{name}")
    public ActionResult greet(HttpContext ctx, @FromRoute("name") String name) {
        return ctx.json(Map.of("message", greeter.greet(name)));
    }
}
```

This pattern encourages clean abstractions and makes testing easier by allowing you to swap implementations.

## Advanced Examples

### Login and Session-Backed APIs

```java
@Controller(route = "/account")
public class AccountController {
    private final UserService users;
    
    public AccountController(UserService users) { 
        this.users = users; 
    }

    @HttpPost("/login")
    public ActionResult login(
        HttpContext ctx,
        @FromForm("username") String username,
        @FromForm("password") String password) {

        if (users.authenticate(username, password)) {
            ctx.session().data().put("user", username);
            return ctx.json(Map.of("ok", true));
        }
        ctx.response().setStatus(401);
        return ctx.json(Map.of("ok", false));
    }

    @HttpGet("/me")
    public ActionResult me(HttpContext ctx, @FromSession("user") String user) {
        if (user == null) {
            ctx.response().setStatus(401);
            return ctx.json(Map.of("authenticated", false));
        }
        return ctx.json(Map.of("authenticated", true, "user", user));
    }
}
```

## FAQ

### How are controllers discovered?

Call `server.addControllers("your.base.package")` to scan one package, or call `server.addControllers()` with no arguments to scan all available packages in the classpath.

### Can I use only explicit routes?

Yes. Use `server.routes().map(...)` for a minimal server without controllers.

### Do I need to use dependency injection?

No. You can create `new WebServer(port, root)` without a ServiceCollection. If you want constructor injection in controllers, use `new WebServer(port, root, services)`.

### How do I register an interface and implementation?

Register it in the ServiceCollection:

```java
services.addSingleton(MyInterface.class, MyImplementation.class);
```

Controllers depending on MyInterface will automatically receive MyImplementation.

### How do I change the template engine?

Bind your implementation to TemplateEngine in ServiceCollection.

### How do I return a custom status?

Set `ctx.response().setStatus(code)` before returning an ActionResult or using helpers like `ctx.ok`.

## License

WebServer4J is licensed under the GNU General Public License v3.0.  
See the LICENSE file for the full text.
