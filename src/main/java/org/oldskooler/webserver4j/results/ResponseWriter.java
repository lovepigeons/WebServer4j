package org.oldskooler.webserver4j.results;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.stream.ChunkedFile;
import org.oldskooler.webserver4j.error.ErrorRegistry;
import org.oldskooler.webserver4j.http.HttpContext;
import org.oldskooler.webserver4j.http.HttpResponseData;
import org.oldskooler.webserver4j.session.Session;
import org.oldskooler.webserver4j.session.SessionManager;
import org.oldskooler.webserver4j.staticfiles.MimeTypes;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Handles writing HTTP responses back to clients.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Converting internal response data to Netty HTTP responses</li>
 *   <li>Managing session cookies</li>
 *   <li>Serving static files efficiently</li>
 *   <li>Handling connection keep-alive</li>
 *   <li>Error response handling</li>
 * </ul>
 */
public class ResponseWriter {
    private final SessionManager sessions;

    public ResponseWriter(SessionManager sessions) {
        this.sessions = sessions;
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
    public void writeResponse(ChannelHandlerContext chx, HttpContext ctx,
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
            setConnectionHeaders(res, req);

            ChannelFuture f = chx.writeAndFlush(res);
            if (!HttpUtil.isKeepAlive(req)) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (Throwable ex) {
            handleWriteError(chx, ctx, req, session, ex);
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
    public void sendFile(Session session, ChannelHandlerContext chx, FullHttpRequest req, File file) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        // Set content type based on file extension
        String ext = getFileExtension(file);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, MimeTypes.get(ext));

        applySessionCookie(res, req, session);

        long length = raf.length();
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
        chx.write(res);

        // Handle compression and file streaming
        if (supportsGzipCompression(req)) {
            sendCompressedFile(chx, file);
        } else {
            chx.write(new ChunkedFile(raf));
        }

        ChannelFuture last = chx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (!HttpUtil.isKeepAlive(req)) {
            last.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handleWriteError(ChannelHandlerContext chx, HttpContext ctx,
                                  FullHttpRequest req, Session session, Throwable ex) {
        // Create a simple 500 error response
        HttpResponseData errorResp = new HttpResponseData();
        errorResp.setStatus(500);
        errorResp.setContentType("text/plain; charset=UTF-8");
        errorResp.setBody("Internal Server Error".getBytes(StandardCharsets.UTF_8));

        // Try to write the error response (without recursion)
        try {
            FullHttpResponse res = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Unpooled.wrappedBuffer(errorResp.getBody())
            );
            res.headers().set(HttpHeaderNames.CONTENT_TYPE, errorResp.getContentType());
            res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, errorResp.getBody().length);

            applySessionCookie(res, req, session);
            setConnectionHeaders(res, req);

            ChannelFuture f = chx.writeAndFlush(res);
            if (!HttpUtil.isKeepAlive(req)) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (Exception writeEx) {
            // Last resort - just close the connection
            chx.close();
        }
    }

    private void setConnectionHeaders(HttpResponse res, FullHttpRequest req) {
        boolean keepAlive = HttpUtil.isKeepAlive(req);
        if (keepAlive) {
            res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
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
        String encodedCookie = ServerCookieEncoder.STRICT.encode(cookie);
        res.headers().add(HttpHeaderNames.SET_COOKIE, encodedCookie);
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        return name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1)
                : "";
    }

    private boolean supportsGzipCompression(FullHttpRequest req) {
        return req.headers().get(HttpHeaderNames.ACCEPT_ENCODING, "").contains("gzip");
    }

    private void sendCompressedFile(ChannelHandlerContext chx, File file) throws Exception {
        byte[] uncompressed = Files.readAllBytes(file.toPath());
        chx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(uncompressed)));
    }
}