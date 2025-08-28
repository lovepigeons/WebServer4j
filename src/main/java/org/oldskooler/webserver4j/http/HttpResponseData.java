package org.oldskooler.webserver4j.http;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable HTTP response builder.
 */
public class HttpResponseData {
    private int status = 200;
    private byte[] body = new byte[0];
    private String contentType = "text/plain; charset=UTF-8";
    private final Map<String,String> headers = new HashMap<>();
    private String filePath;

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Map<String, String> getHeaders() { return headers; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
