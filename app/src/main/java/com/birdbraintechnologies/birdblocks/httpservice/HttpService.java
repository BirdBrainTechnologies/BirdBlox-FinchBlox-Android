package com.birdbraintechnologies.birdblocks.httpservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

/**
 * Service that bundles the HTTP server and bluetooth
 *
 * @author Terence Sun (tsun1215)
 */
public class HttpService extends Service {
    public static final String TAG = "HTTPService";
    public static final int DEFAULT_PORT = 22179;
    private Server server;
    private BluetoothHelper btService;

    // To allow external requests (for debugging purposes),
    // just make the String below null.
    private static final String HTTPAccessFlag = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            btService = new BluetoothHelper(this);
            // TODO: Handle errors in getting the Bluetooth service
            server = new Server(DEFAULT_PORT, this);
        } catch (IOException e) {
            Log.d(TAG, "Unable to start service " + e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "HttpService destroyed");
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Gets the bluetooth helper object
     * @return Bluetooth helper object
     */
    public BluetoothHelper getBluetoothHelper() {
        return this.btService;
    }


    /**
     * HTTP server that serves all requests to this service
     */
    private static class Server extends NanoHTTPD {
        public static final String TAG = "NanoHTTPServer";
        private RequestRouter router;

        public Server(int port, HttpService service) throws IOException {
            super(HTTPAccessFlag, port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            router = new RequestRouter(service);
            Log.d(TAG, "Started server on port " + port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String requestPath = session.getUri();

            Log.d("SessionURI", session.getUri());
            Log.d("SessionParameters", session.getParameters().toString());

            Method requestMethod = session.getMethod();
            Log.d(TAG, session.getRemoteIpAddress() + " " + requestMethod + " " + requestPath);

            // Route request
            Response response = router.routeAndDispatch(session);

            if (response == null) {
                response = newFixedLengthResponse(Response.Status.NOT_IMPLEMENTED,
                        NanoHTTPD.MIME_PLAINTEXT, "");
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;
        }
    }
}
