package org.oldskooler.webserver4j.template;

import org.oldskooler.webserver4j.http.HttpContext;
import org.oldskooler.webserver4j.controller.ActionResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Pluggable template engine abstraction.
 */
public abstract class TemplateEngine {
    private String templateText;
    private Map<String, Object> model;

    /**
     * Set template string
     */
    public TemplateEngine html(String templateText) {
        this.templateText = templateText;
        return this;
    }

    /**
     * Set template model
     */
    public TemplateEngine model(Map<String, Object> model) {
        this.model = model;
        return this;
    }

    public abstract String render(String templateText, Map<String, Object> model);

    /**
     * Render a template file (lookup/load the template from disk or classpath).
     * By default, unsupported unless overridden by a subclass.
     */
    public TemplateEngine file(String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");

        String templateText = loadTemplate(fileName);

        if (this.templateText == null) {
            throw new IllegalArgumentException("Template not found on classpath or filesystem: " + fileName);
        }

        this.html(templateText);

        return this;
    }

    /**
     * Loads template text, first trying the classpath, then the filesystem.
     * Interprets content as UTF-8.
     */
    private static String loadTemplate(String nameOrPath) {
        Path p = FileSystems.getDefault().getPath(nameOrPath);

        if (Files.exists(p) && Files.isRegularFile(p)) {
            try {
                byte[] bytes = Files.readAllBytes(p);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read template file: " + nameOrPath, e);
            }
        }

        // not found
        return null;
    }

    /**
     * Helper to directly return an HTML response, decreasing verbosity.
     * Will use {@link #render(String, Map)} by default.
     */
    public ActionResult build(HttpContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        return ctx.html(render(this.templateText, this.model));
    }
}
