package com.birdbraintechnologies.birdblox.httpservice;

import android.util.Log;

import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DebugRequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.HostDeviceHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.PropertiesHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RecordingHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.SettingsHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.SoundHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.UIRequestHandler;

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
 * @author Shreyan Bakshi (AppyFizz)
 */
class RequestRouter {

    private HashMap<Pattern, RequestHandler> routes;
    private HttpService service;

    RequestRouter(HttpService service) {
        this.routes = new HashMap<>();
        this.service = service;
        initRoutes();
    }

    /**
     * Initializes all the routes in the router. Place all new routes in this method using the
     * addRoute(regex, handler) function.
     */
    private void initRoutes() {
        // TODO: Make this have a match ordering
        addRoute("^/robot/(.*)$", new RobotRequestHandler(service));
        addRoute("^/tablet/(.*)$", new HostDeviceHandler(service));
        addRoute("^/settings/(.*)$", new SettingsHandler(service));
        addRoute("^/data/(.*)$", new FileManagementHandler(service));
        addRoute("^/sound/recording/(.*)$", new RecordingHandler(service));
        addRoute("^/sound/(?!recording)(.*)$", new SoundHandler(service));
        addRoute("^/properties/(.*)$", new PropertiesHandler(service));
        addRoute("^/cloud/(.*)$", new DropboxRequestHandler(service));
        addRoute("^/ui/(.*)$", new UIRequestHandler(service));
        addRoute("^/debug/(.*)$", new DebugRequestHandler(service));
        /**
         * These were older command patterns (NOT to be used anymore):
         * addRoute("^/hummingbird/(.*)$", new HummingbirdRequestHandler(service));
         *
         * They have now been replaced with:
         * addRoute("^/robot/(.*)$", new RobotRequestHandler(service));
         */
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
    NanoHTTPD.Response routeAndDispatch(NanoHTTPD.IHTTPSession session) {

        String path = session.getUri();

        Log.d("parametersURI", path);

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
                System.out.println("args" + args.toString());
                return e.getValue().handleRequest(session, args);
            }
        }
        // No match
        return null;
    }
}
