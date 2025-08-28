package org.oldskooler.webserver4j.controller;

import org.oldskooler.webserver4j.http.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base annotation for HTTP verb mapping.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HttpVerb {
    HttpMethod method();
    String value() default "";
}
