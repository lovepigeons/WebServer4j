package org.oldskooler.webserver4j.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a controller. Naming convention also supported: *Controller.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Controller {
    /** Optional route prefix like in ASP.NET Core. */
    String route() default "";
}
