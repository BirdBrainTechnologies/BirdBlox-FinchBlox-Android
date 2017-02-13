package com.birdbraintechnologies.birdblocks;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public class RequestRouter {

    private HashMap<Pattern, RequestHandler> routes;

    public RequestRouter() {
        routes = new HashMap();
        init();
    }

    private void init() {
        addRoute("^/hummingbird/(.*)", new HummingbirdRequestHandler());
    }

    private void addRoute(String regex, RequestHandler handler) {
        routes.put(Pattern.compile(regex), handler);
    }

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
                return e.getValue().handleRequest(session);
            }
        }

        return null;
    }
}
