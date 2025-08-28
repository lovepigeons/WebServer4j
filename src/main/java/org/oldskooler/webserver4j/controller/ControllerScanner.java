package org.oldskooler.webserver4j.controller;

import org.oldskooler.inject4j.Scope;
import org.oldskooler.inject4j.ServiceCollection;
import org.oldskooler.inject4j.ServiceProvider;
import org.oldskooler.webserver4j.http.HttpContext;
import org.oldskooler.webserver4j.http.HttpMethod;
import org.oldskooler.webserver4j.results.ActionResult;
import org.oldskooler.webserver4j.routing.RouteHandler;
import org.oldskooler.webserver4j.routing.Router;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Registers controller classes and their routes with a {@link Router}.
 * <p>
 * Supports two discovery modes:
 * <ol>
 *   <li><strong>Package mode:</strong> If a non-empty {@code basePackage} is provided, only that package and
 *       its subpackages are scanned.</li>
 *   <li><strong>Project-wide mode:</strong> If {@code basePackage} is {@code null} or blank, the entire application
 *       classpath (directories and JARs on {@code java.class.path}) is scanned.</li>
 * </ol>
 * </p>
 *
 * <h2>Controller discovery</h2>
 * A class is considered a controller if either:
 * <ul>
 *   <li>It is annotated with {@link Controller}, or</li>
 *   <li>Its simple name ends with <em>"Controller"</em> and it is not abstract.</li>
 * </ul>
 *
 * <h2>Route mapping</h2>
 * Routes are derived from HTTP method annotations on controller methods
 * ({@link HttpGet}, {@link HttpPost}, {@link HttpPut}, {@link HttpDelete}).
 * A controller-level {@link Controller#route()} prefix is honored when present.
 *
 * <h2>Dependency injection</h2>
 * Controllers are registered as scoped services via the provided
 * {@link ServiceCollection}, and resolved from a request scope when handling
 * a route. Constructor injection is supported by the DI container.
 *
 * <h2>Parameter binding</h2>
 * Parameters are bound from {@link HttpContext} using supported annotations
 * {@link FromRoute}, {@link FromQuery}, {@link FromForm}, {@link FromSession},
 * with a few convenience fallbacks (e.g., route/query by parameter name for
 * String parameters). Methods may return an {@link ActionResult}, {@link String}
 * (treated as <code>text/plain</code> OK), or any object (serialized via
 * {@link HttpContext#json(Object)}).
 */
public class ControllerScanner {
    private final ServiceCollection services;
    private ServiceProvider provider;

    /**
     * Creates a new instance backed by the given DI service collection.
     *
     * @param services the DI service collection used to register controllers
     */
    public ControllerScanner(ServiceCollection services) {
        this.services = services;
    }

    /**
     * Scans for controllers and registers their routes.
     * <p>
     * If {@code basePackage} is {@code null} or blank, scans the entire application classpath
     * (both directories and JARs on {@code java.class.path}). Otherwise, scans only the given
     * package and its subpackages using the context class loader resources.
     * </p>
     *
     * @param router      router to register routes with
     * @param basePackage base package to scan; {@code null}/blank = scan whole project
     * @throws RuntimeException if an I/O error occurs during scanning
     */
    public void scanAndRegister(Router router, String basePackage) {
        Set<Class<?>> candidates = (basePackage == null || isBlank(basePackage))
                ? findAllProjectClasses()
                : findClassesInPackage(basePackage);

        for (Class<?> c : candidates) {
            if (!isController(c)) continue;
            registerController(router, c);
        }

        this.provider = this.services.buildServiceProvider();
    }

    private static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Registers all routes from the provided controller classes.
     *
     * @param router           router to register routes with
     * @param controllerTypes  explicit list of controller classes
     */
    @SafeVarargs
    public final void registerControllers(Router router, Class<?>... controllerTypes) {
        if (controllerTypes == null) return;
        for (Class<?> c : controllerTypes) {
            if (c == null || !isController(c)) continue;
            registerController(router, c);
        }
        this.provider = this.services.buildServiceProvider();
    }

    /**
     * Registers all routes from the provided controller classes.
     *
     * @param router          router to register routes with
     * @param controllerTypes iterable of controller classes
     */
    public void registerControllers(Router router, Iterable<Class<?>> controllerTypes) {
        if (controllerTypes == null) return;
        for (Class<?> c : controllerTypes) {
            if (c == null || !isController(c)) continue;
            registerController(router, c);
        }
        this.provider = this.services.buildServiceProvider();
    }

    /**
     * Determines whether a given class should be treated as a controller.
     *
     * @param c the class to check
     * @return true if it is a controller, false otherwise
     */
    private boolean isController(Class<?> c) {
        if (c == null) return false;
        if (c.isAnnotationPresent(Controller.class)) return true;
        return c.getSimpleName().endsWith("Controller") && !Modifier.isAbstract(c.getModifiers());
    }

    /**
     * Registers handler methods from a controller type onto the router.
     *
     * @param router         the router instance
     * @param controllerType the controller class type
     */
    private void registerController(Router router, Class<?> controllerType) {
        String prefix = "";
        Controller ctlAnn = controllerType.getAnnotation(Controller.class);
        if (ctlAnn != null && !ctlAnn.route().isEmpty()) {
            if (!ctlAnn.route().startsWith("/")) {
                prefix = prefix + "/" + ctlAnn.route();
            } else {
                prefix = prefix + ctlAnn.route();
            }
        }

        this.services.addScoped(controllerType);

        for (Method m : controllerType.getDeclaredMethods()) {
            String route = resolveMethodRoute(m);
            if (route == null) continue;
            HttpMethod verb = resolveVerb(m);
            String template = normalize(prefix, route);
            RouteHandler handler = (ctx) -> invokeController(controllerType, m, ctx);
            router.map(verb, template, handler);
        }
    }

    /**
     * Combines a controller-level prefix with a method route into a normalized template.
     *
     * @param prefix controller-level prefix
     * @param route  method-level route
     * @return normalized route string
     */
    private String normalize(String prefix, String route) {
        if (prefix == null || prefix.isEmpty()) return route;
        if (route.startsWith("/")) return prefix + route;
        return prefix + "/" + route;
    }

    /**
     * Extracts the route template from supported HTTP method annotations.
     *
     * @param m method to inspect
     * @return route template, or null if none
     */
    private String resolveMethodRoute(Method m) {
        if (m.isAnnotationPresent(HttpGet.class)) return m.getAnnotation(HttpGet.class).value();
        if (m.isAnnotationPresent(HttpPost.class)) return m.getAnnotation(HttpPost.class).value();
        if (m.isAnnotationPresent(HttpPut.class)) return m.getAnnotation(HttpPut.class).value();
        if (m.isAnnotationPresent(HttpDelete.class)) return m.getAnnotation(HttpDelete.class).value();
        return null;
    }

    /**
     * Resolves the HTTP verb from supported annotations; defaults to GET.
     *
     * @param m method to inspect
     * @return HTTP method
     */
    private HttpMethod resolveVerb(Method m) {
        if (m.isAnnotationPresent(HttpGet.class)) return HttpMethod.GET;
        if (m.isAnnotationPresent(HttpPost.class)) return HttpMethod.POST;
        if (m.isAnnotationPresent(HttpPut.class)) return HttpMethod.PUT;
        if (m.isAnnotationPresent(HttpDelete.class)) return HttpMethod.DELETE;
        return HttpMethod.GET;
    }

    /**
     * Invokes a controller method within a request scope.
     *
     * @param controllerType the controller type
     * @param method         the method to invoke
     * @param ctx            the HTTP context
     * @return action result
     * @throws Exception if invocation fails
     */
    private ActionResult invokeController(Class<?> controllerType, Method method, HttpContext ctx) throws Exception {
        try (Scope scope = this.provider.createScope()) {
            Object controller = scope.getService(controllerType);
            Object[] args = bindParameters(method, ctx);
            Object result = method.invoke(controller, args);
            if (result instanceof ActionResult) {
                return (ActionResult) result;
            }
            if (result instanceof String) {
                return ctx.ok((String) result);
            }
            return ctx.json(result);
        }
    }

    /**
     * Binds parameters for a controller method from the HTTP context.
     *
     * @param method the method to inspect
     * @param ctx    HTTP context
     * @return bound parameter values
     */
    private Object[] bindParameters(Method method, HttpContext ctx) {
        Parameter[] params = method.getParameters();
        Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> t = p.getType();
            if (t.isAssignableFrom(HttpContext.class)) { values[i] = ctx; continue; }

            FromRoute fr = p.getAnnotation(FromRoute.class);
            if (fr != null) { values[i] = ctx.request().getRouteParams().get(fr.value()); continue; }

            FromQuery fq = p.getAnnotation(FromQuery.class);
            if (fq != null) { values[i] = ctx.request().getQuery().get(fq.value()); continue; }

            FromForm ff = p.getAnnotation(FromForm.class);
            if (ff != null) { values[i] = ctx.request().getForm().get(ff.value()); continue; }

            FromSession fs = p.getAnnotation(FromSession.class);
            if (fs != null) { values[i] = ctx.session().data().get(fs.value()); continue; }

            String name = p.getName();
            String v = ctx.request().getRouteParams().getOrDefault(name, ctx.request().getQuery().get(name));
            if (v != null && t == String.class) { values[i] = v; continue; }

            values[i] = null;
        }
        return values;
    }

    // ===== Class discovery =====

    /**
     * Finds classes within a base package.
     *
     * @param basePackage package to scan
     * @return set of classes found
     */
    private Set<Class<?>> findClassesInPackage(String basePackage) {
        Set<Class<?>> classes = new HashSet<>();
        String path = basePackage.replace('.', '/');
        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    File dir = new File(url.getFile());
                    scanDirectory(basePackage, dir.toPath(), classes);
                } else if ("jar".equals(protocol)) {
                    String urlPath = url.getPath();
                    int bang = urlPath.indexOf('!');
                    String jarPath = urlPath.substring(5, bang);
                    scanJar(jarPath, path, classes);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return classes;
    }

    /**
     * Finds all classes in the project classpath.
     *
     * @return set of classes found
     */
    private Set<Class<?>> findAllProjectClasses() {
        Set<Class<?>> classes = new HashSet<>();
        String cp = System.getProperty("java.class.path", "");
        String[] entries = cp.split(File.pathSeparator);
        for (String entry : entries) {
            if (entry == null || isBlank(entry)) continue;
            Path p = Paths.get(entry);
            if (Files.isDirectory(p)) {
                scanClasspathDirectoryRoot(p, classes);
            } else if (entry.endsWith(".jar")) {
                scanJar(entry, "", classes);
            }
        }
        return classes;
    }

    /**
     * Scans a directory recursively for classes.
     *
     * @param basePackage base package name
     * @param dir         directory to scan
     * @param classes     set to collect classes
     */
    private void scanDirectory(String basePackage, Path dir, Set<Class<?>> classes) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.list(dir).forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        scanDirectory(basePackage + "." + path.getFileName(), path, classes);
                    } else if (path.getFileName().toString().endsWith(".class")) {
                        String name = path.getFileName().toString();
                        String simple = name.substring(0, name.length() - 6);
                        addClassIfLoadable(basePackage + "." + simple, classes);
                    }
                } catch (RuntimeException ex) {
                }
            });
        } catch (IOException ignored) { }
    }

    /**
     * Scans an entire classpath root directory.
     *
     * @param root    root directory
     * @param classes set to collect classes
     */
    private void scanClasspathDirectoryRoot(Path root, Set<Class<?>> classes) {
        if (root == null || !Files.exists(root)) return;
        try {
            Files.walk(root)
                    .filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> {
                        String rel = root.relativize(p).toString();
                        if (rel.contains("$")) return;
                        String className = rel.replace(File.separatorChar, '.');
                        className = className.substring(0, className.length() - 6);
                        addClassIfLoadable(className, classes);
                    });
        } catch (IOException ignored) { }
    }

    /**
     * Scans a JAR file for classes.
     *
     * @param jarPath     path to JAR
     * @param packagePath package path filter
     * @param classes     set to collect classes
     */
    private void scanJar(String jarPath, String packagePath, Set<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class") || name.contains("$")) continue;
                if (packagePath != null && !packagePath.isEmpty() && !name.startsWith(packagePath)) continue;
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                addClassIfLoadable(className, classes);
            }
        } catch (IOException ignored) { }
    }

    /**
     * Attempts to load a class by name.
     *
     * @param className fully qualified class name
     * @param classes   set to collect classes
     */
    private void addClassIfLoadable(String className, Set<Class<?>> classes) {
        try {
            Class<?> c = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            classes.add(c);
        } catch (LinkageError | ClassNotFoundException ignored) { }
    }

    /**
     * Scans the entire project classpath and registers controllers.
     *
     * @param router router to register routes with
     */
    public void scanAllAndRegister(Router router) {
        scanAndRegister(router, null);
    }
}