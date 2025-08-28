package org.oldskooler.webserver4j.routing;

import java.util.List;
import java.util.Map;

public class MatchedRoute {
    public RouteDefinition route;
    public Map<String, String> params;
    public List<String> wildcards;

    public MatchedRoute(RouteDefinition route, Map<String, String> params, List<String> wildcards) {
        this.route = route;
        this.params = params;
        this.wildcards = wildcards;
    }


}
