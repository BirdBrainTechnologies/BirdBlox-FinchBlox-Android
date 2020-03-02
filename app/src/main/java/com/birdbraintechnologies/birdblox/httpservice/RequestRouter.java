package com.birdbraintechnologies.birdblox.httpservice;

import android.content.Context;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
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
public class RequestRouter {
    private final String TAG = this.getClass().getSimpleName();

    private HashMap<Pattern, RequestHandler> routes;
    private Context context;
    private BluetoothHelper btService;
    /*private HttpService service;

    RequestRouter(HttpService service) {
        this.routes = new HashMap<>();
        this.service = service;
        initRoutes();
    }*/

    public RequestRouter(Context context, BluetoothHelper btService) {
        this.routes = new HashMap<>();
        this.context = context;
        this.btService = btService;
        initRoutes();
    }

    /**
     * Initializes all the routes in the router. Place all new routes in this method using the
     * addRoute(regex, handler) function.
     */
    private void initRoutes() {
        // TODO: Make this have a match ordering
        addRoute("^/robot/(.*)$", new RobotRequestHandler(btService));
        addRoute("^/tablet/(.*)$", new HostDeviceHandler(context));
        addRoute("^/settings/(.*)$", new SettingsHandler(context));
        addRoute("^/data/(.*)$", new FileManagementHandler(context));
        addRoute("^/sound/recording/(.*)$", new RecordingHandler(context));
        addRoute("^/sound/(?!recording)(.*)$", new SoundHandler(context));
        addRoute("^/properties/(.*)$", new PropertiesHandler());
        addRoute("^/cloud/(.*)$", new DropboxRequestHandler());
        addRoute("^/ui/(.*)$", new UIRequestHandler());
        addRoute("^/debug/(.*)$", new DebugRequestHandler(context));
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
    //NanoHTTPD.Response routeAndDispatch(NanoHTTPD.IHTTPSession session) {
    public NativeAndroidResponse routeAndDispatch(NativeAndroidSession session) {

        String path = session.getUri();

        //Log.d("parametersURI", path);

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
                Log.d(TAG, "about to handle request for '" + session.getUri() + "' with args " + args.toString());
                return e.getValue().handleRequest(session, args);
            }
        }
        // No match
        //return null;
        return new NativeAndroidResponse(Status.BAD_REQUEST, "No handler found for the following request: " + path);
    }
}
