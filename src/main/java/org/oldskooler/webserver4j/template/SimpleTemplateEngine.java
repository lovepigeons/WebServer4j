package org.oldskooler.webserver4j.template;

import java.util.Map;

/**
 * Minimal template engine that replaces {{key}} with values.
 */
public class SimpleTemplateEngine extends TemplateEngine {
    @Override
    public String render(String templateText, Map<String, Object> model) {
        String out = templateText; // we treat templateName as the template text for simplicity
        if (model == null) return out;
        for (Map.Entry<String,Object> e : model.entrySet()) {
            String key = "{{" + e.getKey() + "}}";
            out = out.replace(key, String.valueOf(e.getValue()));
        }
        return out;
    }
}
