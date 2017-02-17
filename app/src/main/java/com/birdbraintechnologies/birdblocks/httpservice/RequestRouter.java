package com.birdbraintechnologies.birdblocks.httpservice;

import com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.HostDeviceHandler;
import com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.HummingbirdRequestHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * Routes requests from the HttpService to the correct RequestHandlers
 *
 * @author Terence Sun (tsun1215)
 */
public class RequestRouter {

    private HashMap<Pattern, RequestHandler> routes;
    private HttpService service;

    public RequestRouter(HttpService service) {
        this.routes = new HashMap();
        this.service = service;
        initRoutes();
    }

    /**
     * Initializes all the routes in the router. Place all new routes in this method using the
     * addRoute(regex, handler) function.
     */
    private void initRoutes() {
        // TODO: Make this have a match ordering
        addRoute("^/hummingbird/(.*)$", new HummingbirdRequestHandler(service));
        addRoute("^/iPad/(.*)$", new HostDeviceHandler());
    }

    /**
     * Adds a route to the router
     *
     * @param regex   How to match this route
     * @param handler RequestHandler responsible for anything that matches the regex
     */
    private void addRoute(String regex, RequestHandler handler) {
        routes.put(Pattern.compile(regex), handler);
    }

    /**
     * Routes a given HttpRequest to the correct handler and returns the response
     *
     * @param session HttpRequest from the server
     * @return Response to the request
     */
    public NanoHTTPD.Response routeAndDispatch(NanoHTTPD.IHTTPSession session) {
        String uri = session.getUri();
        String path = uri;
        // Remove GET parameters in case they exist
        int paramsIndex = uri.indexOf("?");
        if (paramsIndex != -1) {
            path = path.substring(0, paramsIndex);
        }

        // Route the request
        for (Map.Entry<Pattern, RequestHandler> e : routes.entrySet()) {
            Pattern p = e.getKey();
            Matcher match = p.matcher(path);
            if (match.matches()) {
                // Generate args from the regex groups (if they exist)
                List<String> args = new ArrayList<>();
                for (int i = 1; i <= match.groupCount(); i++) {
                    args.add(match.group(i));
                }
                return e.getValue().handleRequest(session, args);
            }
        }

        // No match
        return null;
    }
}
