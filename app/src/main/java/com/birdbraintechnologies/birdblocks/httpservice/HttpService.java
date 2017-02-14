package com.birdbraintechnologies.birdblocks.httpservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

public class HttpService extends Service {
    public static final String TAG = "HTTPService";
    public static final int DEFAULT_PORT = 22179;
    private Server server;
    private BluetoothHelper btService;

    public HttpService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            btService = new BluetoothHelper(this);
            server = new Server(DEFAULT_PORT, this);
        } catch (IOException e) {
            Log.d(TAG, "Unable to start service " + e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }

    public BluetoothHelper getBluetoothHelper() {
        return this.btService;
    }


    private static class Server extends NanoHTTPD {
        public static final String TAG = "NanoHTTPServer";
        private RequestRouter router;

        public Server(int port, HttpService service) throws IOException {
            super(port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            router = new RequestRouter(service);
            Log.d(TAG, "Started server on port " + port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String requestPath = session.getUri();
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
