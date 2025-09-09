package org.oldskooler.webserver4j.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.oldskooler.inject4j.ServiceCollection;
import org.oldskooler.webserver4j.controller.ControllerScanner;
import org.oldskooler.webserver4j.error.ErrorRegistry;
import org.oldskooler.webserver4j.http.HttpRequestHandler;
import org.oldskooler.webserver4j.interceptor.InterceptorRegistry;
import org.oldskooler.webserver4j.routing.Router;
import org.oldskooler.webserver4j.session.SessionManager;
import org.oldskooler.webserver4j.staticfiles.StaticFileService;

import javax.net.ssl.SSLException;
import java.io.File;
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
 *   <li>Optional SSL/TLS support</li>
 * </ul>
 */
public class WebServer {
    public static final class Builder {
        private int port = 8080;
        private String wwwroot = "wwwroot";
        private final ServiceCollection services = new ServiceCollection();
        private boolean sslEnabled = false;
        private String certificatePath;
        private String privateKeyPath;
        private String keyPassword;
        private boolean useSelfSignedCert = false;
        private String sslHostname; // Add to Builder

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder wwwroot(String path) {
            this.wwwroot = path;
            return this;
        }

        public Builder services(java.util.function.Consumer<ServiceCollection> fn) {
            fn.accept(services);
            return this;
        }

        public Builder sslHostname(String hostname) {
            this.sslHostname = hostname;
            return this;
        }

        /**
         * Enables SSL with a custom certificate and private key.
         *
         * @param certificatePath path to the certificate file (PEM format)
         * @param privateKeyPath path to the private key file (PEM format)
         * @return this builder
         */
        public Builder ssl(String certificatePath, String privateKeyPath) {
            this.sslEnabled = true;
            this.certificatePath = certificatePath;
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        /**
         * Enables SSL with a custom certificate, private key, and key password.
         *
         * @param certificatePath path to the certificate file (PEM format)
         * @param privateKeyPath path to the private key file (PEM format)
         * @param keyPassword password for the private key
         * @return this builder
         */
        public Builder ssl(String certificatePath, String privateKeyPath, String keyPassword) {
            this.sslEnabled = true;
            this.certificatePath = certificatePath;
            this.privateKeyPath = privateKeyPath;
            this.keyPassword = keyPassword;
            return this;
        }

        /**
         * Enables SSL with a self-signed certificate (for development/testing only).
         * <p>
         * <strong>Warning:</strong> Self-signed certificates should never be used in production
         * as they provide no verification of identity and will cause browser warnings.
         * <p>
         * If this fails due to security provider issues, consider generating a certificate manually:
         * <pre>
         * openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 365 -nodes \
         *   -subj "/CN=localhost"
         * </pre>
         * Then use {@link #ssl(String, String)} instead.
         *
         * @return this builder
         */
        public Builder sslSelfSigned() {
            this.sslEnabled = true;
            this.useSelfSignedCert = true;
            return this;
        }

        public WebServer build() {
            return new WebServer(port, wwwroot, services, sslEnabled,
                    certificatePath, privateKeyPath, keyPassword, useSelfSignedCert, sslHostname);
        }
    }

    /** Defaults: port 8080, wwwroot "wwwroot", empty services, no SSL. */
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
    private final ServiceCollection services;
    private final ControllerScanner scanner;
    private final HttpRequestHandler requestHandler;
    private final SslContext sslContext;
    private final String sslHostname;

    /**
     * Constructs a new {@code WebServer} with SSL support.
     *
     * @param port               TCP port for the server to listen on
     * @param wwwroot           root directory for static file serving
     * @param services          dependency injection service container
     * @param sslEnabled        whether SSL is enabled
     * @param certificatePath   path to certificate file (if using custom cert)
     * @param privateKeyPath    path to private key file (if using custom cert)
     * @param keyPassword       password for private key (optional)
     * @param useSelfSignedCert whether to use a self-signed certificate
     */
    public WebServer(int port, String wwwroot, ServiceCollection services,
                     boolean sslEnabled, String certificatePath, String privateKeyPath,
                     String keyPassword, boolean useSelfSignedCert, String sslHostname) {
        this.port = port;
        this.staticFiles = new StaticFileService(wwwroot);
        this.sessions = new SessionManager(TimeUnit.HOURS.toMillis(24));
        this.services = services;
        this.scanner = new ControllerScanner(services);
        this.requestHandler = new HttpRequestHandler(router, interceptors, errors, staticFiles, sessions);
        this.sslHostname = sslHostname;
        this.sslContext = createSslContext(sslEnabled, certificatePath, privateKeyPath, keyPassword, useSelfSignedCert, sslHostname);
    }

    /**
     * Constructs a new {@code WebServer}.
     *
     * @param port     TCP port for the server to listen on
     * @param wwwroot  root directory for static file serving
     * @param services dependency injection service container
     */
    public WebServer(int port, String wwwroot, ServiceCollection services) {
        this(port, wwwroot, services, false, null, null, null, false, null);
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

    private SslContext createSslContext(boolean sslEnabled, String certificatePath,
                                        String privateKeyPath, String keyPassword,
                                        boolean useSelfSignedCert, String sslHostname) {
        if (!sslEnabled) {
            return null;
        }

        try {
            if (useSelfSignedCert) {
                //System.out.println("WARNING: Using self-signed certificate. This should only be used for development/testing!");
                //SelfSignedCertificate ssc = new SelfSignedCertificate();
                //return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
                throw new IllegalArgumentException("Self-signed certificates must be generated outside of this library");
            } else {
                File certFile = new File(certificatePath);
                File keyFile = new File(privateKeyPath);

                if (!certFile.exists()) {
                    throw new IllegalArgumentException("Certificate file not found: " + certificatePath);
                }
                if (!keyFile.exists()) {
                    throw new IllegalArgumentException("Private key file not found: " + privateKeyPath);
                }

                SslContextBuilder builder = null;

                if (keyPassword == null || keyPassword.isEmpty()) {
                    builder = SslContextBuilder.forServer(certFile, keyFile);
                }
                else {
                    builder = SslContextBuilder.forServer(certFile, keyFile, keyPassword);
                }

                return builder.build();
            }
        } catch (SSLException e) {
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    /**
     * Returns whether SSL is enabled for this server.
     *
     * @return true if SSL is enabled, false otherwise
     */
    public boolean isSslEnabled() {
        return sslContext != null;
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
     * Returns the error registry for registering error handlers.
     *
     * @return error registry
     */
    public ErrorRegistry errors() {
        return errors;
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

                            // Add SSL handler first if SSL is enabled
                            if (sslContext != null) {
                                p.addLast(sslContext.newHandler(ch.alloc(), sslHostname, port));
                            }

                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(32 * 1024 * 1024));
                            p.addLast(new HttpContentCompressor());
                            p.addLast(new ChunkedWriteHandler());
                            p.addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
                                    requestHandler.handle(ctx, msg);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    cause.printStackTrace();
                                }
                            });
                        }
                    })
                    .childOption(ChannelOption.TCP_NODELAY, true);

            //String protocol = sslContext != null ? "HTTPS" : "HTTP";
            //System.out.println("Starting " + protocol + " server on port " + port);

            Channel ch = b.bind(port).sync().channel();
            ch.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}