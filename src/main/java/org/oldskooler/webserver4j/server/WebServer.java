package org.oldskooler.webserver4j.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.oldskooler.javadi.ServiceCollection;
import org.oldskooler.webserver4j.controller.ControllerScanner;
import org.oldskooler.webserver4j.error.ErrorRegistry;
import org.oldskooler.webserver4j.http.*;
import org.oldskooler.webserver4j.interceptor.InterceptorRegistry;
import org.oldskooler.webserver4j.results.ActionResult;
import org.oldskooler.webserver4j.routing.MatchedRoute;
import org.oldskooler.webserver4j.routing.Router;
import org.oldskooler.webserver4j.session.Session;
import org.oldskooler.webserver4j.session.SessionManager;
import org.oldskooler.webserver4j.staticfiles.MimeTypes;
import org.oldskooler.webserver4j.staticfiles.StaticFileService;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A lightweight MVC-style web server built on Netty.
 * <p>
 * Features include:
 * <ul>
 *   <li>Routing via {@link Router}</li>
 *   <li>Interceptor support</li>
 *   <li>Controller scanning and registration</li>
 *   <li>Session management</li>
 *   <li>Static file serving</li>
 *   <li>Error handling</li>
 * </ul>
 */
public class WebServer {
    public static final class Builder {
        private int port = 8080;
        private String wwwroot = "wwwroot";
        private final ServiceCollection services = new ServiceCollection();

        public Builder port(int port) { this.port = port; return this; }
        public Builder wwwroot(String path) { this.wwwroot = path; return this; }
        public Builder services(java.util.function.Consumer<ServiceCollection> fn) { fn.accept(services); return this; }

        public WebServer build() { return new WebServer(port, wwwroot, services); }
    }

    /** Defaults: port 8080, wwwroot "wwwroot", empty services. */
    public static void run(java.util.function.Consumer<WebServer> app) throws InterruptedException {
        WebServer s = new Builder().build();
        app.accept(s);
        s.start();
    }

    /** Configure with a builder, then configure routes, then start. */
    public static void run(java.util.function.Consumer<Builder> configure,
                                java.util.function.Consumer<WebServer> app) throws InterruptedException {
        Builder b = new Builder();
        configure.accept(b);
        WebServer s = b.build();
        app.accept(s);
        s.start();
    }

    private final int port;
    private final Router router = new Router();
    private final InterceptorRegistry interceptors = new InterceptorRegistry();
    private final ErrorRegistry errors = new ErrorRegistry();
    private final StaticFileService staticFiles;
    private final SessionManager sessions;
    private final ObjectMapper json = new ObjectMapper();
    private final ServiceCollection services;
    private final ControllerScanner scanner;

    /**
     * Constructs a new {@code WebServer}.
     *
     * @param port     TCP port for the server to listen on
     * @param wwwroot  root directory for static file serving
     * @param services dependency injection service container
     */
    public WebServer(int port, String wwwroot, ServiceCollection services) {
        this.port = port;
        this.staticFiles = new StaticFileService(wwwroot);
        this.sessions = new SessionManager(TimeUnit.HOURS.toMillis(24));
        this.services = services;
        this.scanner = new ControllerScanner(services);
    }

    /**
     * Constructs a new {@code WebServer}.
     *
     * @param port     TCP port for the server to listen on
     * @param wwwroot  root directory for static file serving
     */
    public WebServer(int port, String wwwroot) {
        this(port, wwwroot, new ServiceCollection());
    }

    /**
     * Returns the router used to define routes.
     *
     * @return router instance
     */
    public Router routes() {
        return router;
    }

    /**
     * Returns the interceptor registry.
     *
     * @return interceptor registry
     */
    public InterceptorRegistry interceptors() {
        return interceptors;
    }

    /**
     * Registers controllers by scanning the given base package.
     *
     * @param basePackage package to scan for annotated controllers
     */
    public void addControllers(String basePackage) {
        scanner.scanAndRegister(router, basePackage);
    }

    /**
     * Registers controllers by scanning the entire runtime classpath
     * (all directories and JARs reachable by the application class loader).
     *
     * <p>
     * Use this when you cannot (or prefer not to) specify a base package and
     * want to discover controllers across the whole application. For large
     * deployments this may increase startup time.
     * </p>
     */
    public void addControllers() {
        scanner.scanAllAndRegister(router);
    }

    /**
     * Starts the Netty web server.
     * <p>
     * This is a blocking call that binds to the configured port and
     * blocks until the server channel is closed.
     *
     * @throws InterruptedException if the server thread is interrupted
     */
    public void start() throws InterruptedException {
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        EventLoopGroup worker = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(32 * 1024 * 1024));
                            p.addLast(new HttpContentCompressor());
                            p.addLast(new ChunkedWriteHandler());
                            p.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
                                    handle(ctx, msg);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    cause.printStackTrace();
                                }
                            });
                        }
                    })
                    .childOption(ChannelOption.TCP_NODELAY, true);

            Channel ch = b.bind(port).sync().channel();
            ch.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    /**
     * Returns the error registry for registering error handlers.
     *
     * @return error registry
     */
    public ErrorRegistry errors() {
        return errors;
    }

    /**
     * Core request handler for HTTP requests.
     * <p>
     * This method parses cookies, sessions, query parameters, form data,
     * and file uploads, then dispatches the request through interceptors,
     * routes, static files, and error handlers in order.
     *
     * @param chx Netty channel handler context
     * @param req the incoming HTTP request
     * @throws Exception if processing fails
     */
    private void handle(ChannelHandlerContext chx, FullHttpRequest req) throws Exception {
        String uri = req.uri();
        String path = uri.split("\\?")[0];
        org.oldskooler.webserver4j.http.HttpMethod method = mapMethod(req.method());

        // --- Cookies and session handling ---
        Map<String, Cookie> cookies = new HashMap<>();
        String cookieHeader = req.headers().get(HttpHeaderNames.COOKIE);
        if (cookieHeader != null) {
            Set<Cookie> parsed = ServerCookieDecoder.STRICT.decode(cookieHeader);
            for (Cookie c : parsed) {
                cookies.put(c.name(), c);
            }
        }
        String sessionId = cookies.containsKey("SESSIONID") ? cookies.get("SESSIONID").value() : null;
        Session session = sessions.getOrCreate(sessionId);

        // --- Query parameters ---
        QueryStringDecoder qd = new QueryStringDecoder(req.uri(), StandardCharsets.UTF_8);
        Map<String, List<String>> queryMap = new HashMap<>(qd.parameters());

        // --- Form and file uploads ---
        Map<String, List<String>> formMap = new HashMap<>();
        List<UploadedFile> files = new ArrayList<>();
        byte[] rawBody = new byte[0];
        String contentType = req.headers().get(HttpHeaderNames.CONTENT_TYPE);

        if (req.method().equals(HttpMethod.POST) ||
                req.method().equals(HttpMethod.PUT) ||
                req.method().equals(HttpMethod.PATCH)) {

            rawBody = new byte[req.content().readableBytes()];
            req.content().readBytes(rawBody);

            if (contentType != null &&
                    contentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString())) {
                QueryStringDecoder decoder = new QueryStringDecoder(new String(rawBody, StandardCharsets.UTF_8), false);
                formMap.putAll(decoder.parameters());
            } else if (contentType != null &&
                    contentType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString())) {
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
        }

        Optional<MatchedRoute> matched = router.match(method, path);

        Map<String, String> routeParams = new HashMap<>();
        List<String> wildcardParts = new ArrayList<String>();

        matched.ifPresent(m -> {
            routeParams.putAll(m.params);
            wildcardParts.addAll(m.wildcards);
        });


        HttpRequestData request = new HttpRequestData(
                method,
                path,
                routeParams,
                new QueryParams(queryMap),
                new QueryParams(formMap),
                files,
                cookies,
                rawBody,
                contentType == null ? "" : contentType,
                wildcardParts
        );

        HttpResponseData resp = new HttpResponseData();
        HttpContext ctx = new HttpContext(request, resp, session, json);

        try {
            // Apply interceptors
            if (interceptors.apply(path, ctx)) {
                writeResponse(chx, ctx, req, session, resp);
                return;
            }

            // Route handler
            if (matched.isPresent()) {
                ActionResult result = matched.get().route.handler.handle(ctx);
                writeResponse(chx, ctx, req, session, resp);
                return;
            }

            // Static file
            File file = staticFiles.resolve(path);
            if (file != null) {
                sendFile(session, chx, req, file);
                return;
            }

            // Fallback 404
            boolean handled = errors.handleStatus(ctx, 404);
            if (!handled) {
                resp.setStatus(404);
                resp.setContentType("text/plain; charset=UTF-8");
                resp.setBody(("404 Not Found: " + path).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ex) {
            boolean handled = errors.handleException(ctx, ex);
            if (!handled) {
                resp.setStatus(500);
                resp.setContentType("text/plain; charset=UTF-8");
                resp.setBody("Internal Server Error".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            writeResponse(chx, ctx, req, session, resp);
        }
    }

    /**
     * Sends a static file response to the client.
     *
     * @param session current session
     * @param chx     Netty channel context
     * @param req     original HTTP request
     * @param file    file to send
     * @throws Exception if reading or sending fails
     */
    private void sendFile(Session session, ChannelHandlerContext chx, FullHttpRequest req, File file) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        String ext = file.getName().contains(".")
                ? file.getName().substring(file.getName().lastIndexOf('.') + 1)
                : "";
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, MimeTypes.get(ext));

        applySessionCookie(res, req, session);

        long length = raf.length();
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
        chx.write(res);

        boolean acceptsGzip = req.headers().get(HttpHeaderNames.ACCEPT_ENCODING, "").contains("gzip");

        if (acceptsGzip) {
            byte[] uncompressed = Files.readAllBytes(file.toPath());
            chx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(uncompressed)));
        } else {
            chx.write(new ChunkedFile(raf));
        }

        ChannelFuture last = chx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (!HttpUtil.isKeepAlive(req)) {
            last.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Writes an HTTP response (dynamic or static) to the client.
     *
     * @param chx     Netty channel context
     * @param ctx     HTTP context
     * @param req     original request
     * @param session current session
     * @param data    response data to send
     */
    private void writeResponse(ChannelHandlerContext chx, HttpContext ctx,
                               FullHttpRequest req, Session session, HttpResponseData data) {
        try {
            if (data.getFilePath() != null) {
                sendFile(session, chx, req, new File(data.getFilePath()));
                return;
            }

            FullHttpResponse res = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(data.getStatus()),
                    Unpooled.wrappedBuffer(data.getBody())
            );
            res.headers().set(HttpHeaderNames.CONTENT_TYPE, data.getContentType());
            res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, data.getBody().length);
            data.getHeaders().forEach(res.headers()::set);

            applySessionCookie(res, req, session);

            boolean keepAlive = HttpUtil.isKeepAlive(req);
            if (keepAlive) {
                res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ChannelFuture f = chx.writeAndFlush(res);
            if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE);
        } catch (Throwable ex) {
            boolean handled = errors.handleException(ctx, ex);
            if (!handled) {
                HttpResponseData resp = new HttpResponseData();
                resp.setStatus(500);
                resp.setContentType("text/plain; charset=UTF-8");
                resp.setBody("Internal Server Error".getBytes(StandardCharsets.UTF_8));
                writeResponse(chx, ctx, req, session, resp);
            }
        }
    }

    /**
     * Adds or updates the session cookie in the HTTP response.
     *
     * @param res     response to add the cookie to
     * @param req     original request
     * @param session current session
     */
    private void applySessionCookie(HttpResponse res, FullHttpRequest req, Session session) {
        Cookie cookie = new DefaultCookie("SESSIONID", sessions.ensureId(session));
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        String v = ServerCookieEncoder.STRICT.encode(cookie);
        res.headers().add(HttpHeaderNames.SET_COOKIE, v);
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