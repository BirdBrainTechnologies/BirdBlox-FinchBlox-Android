package com.birdbraintechnologies.birdblocks.httpservice;

import com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.HummingbirdRequestHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public class RequestRouter {

    private HashMap<Pattern, RequestHandler> routes;
    private HttpService service;

    public RequestRouter(HttpService service) {
        this.routes = new HashMap();
        this.service = service;
        init();
    }

    private void init() {
        addRoute("^/hummingbird/(.*)$", new HummingbirdRequestHandler(service));
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
                List<String> args = new ArrayList<>();
                for (int i = 1; i <= match.groupCount(); i++) {
                    args.add(match.group(i));
                }
                return e.getValue().handleRequest(session, args);
            }
        }

        return null;
    }
}
