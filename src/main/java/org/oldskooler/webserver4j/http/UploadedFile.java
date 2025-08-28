package org.oldskooler.webserver4j.http;

import java.io.File;

/**
 * Represents an uploaded file stored on disk.
 */
public class UploadedFile {
    private final String fieldName;
    private final String originalFilename;
    private final String contentType;
    private final File file;

    public UploadedFile(String fieldName, String originalFilename, String contentType, File file) {
        this.fieldName = fieldName;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.file = file;
    }

    public String getFieldName() { return fieldName; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public File getFile() { return file; }
}
