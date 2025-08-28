package org.oldskooler.webserver4j.staticfiles;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves safe disk paths within a web root.
 */
public class StaticFileService {
    private final Path root;

    public StaticFileService(String webRoot) {
        this.root = Paths.get(webRoot).toAbsolutePath().normalize();
    }

    /** Resolve a URL path to a safe file under the web root. */
    public File resolve(String urlPath) {
        String p = urlPath.split("\\?")[0];
        if (p.equals("/") || p.isEmpty()) p = "/index.html";
        Path resolved = root.resolve("." + p).normalize();
        if (!resolved.startsWith(root)) return null; // path traversal protection
        File f = resolved.toFile();
        if (f.exists() && f.isFile()) return f;
        return null;
    }
}
