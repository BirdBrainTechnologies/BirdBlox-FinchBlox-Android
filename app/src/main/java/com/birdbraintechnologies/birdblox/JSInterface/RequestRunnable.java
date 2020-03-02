package com.birdbraintechnologies.birdblox.JSInterface;

import android.util.Log;

import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestRouter;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;

public class RequestRunnable implements Runnable {
    private final String TAG = this.getClass().getSimpleName();
    private String jsonRequest;
    private RequestRouter router;

    public RequestRunnable (String jsonRequest, RequestRouter router) {
        this.jsonRequest = jsonRequest;
        this.router = router;
    }

    public void run () {
        //Log.d(TAG, "Got frontend request: " + jsonRequest);
        NativeAndroidSession session = new NativeAndroidSession(jsonRequest);

        NativeAndroidResponse response = router.routeAndDispatch(session);

        String id = session.getRequestId();
        String status = response.getStatus();
        String body = response.getBody();

        runJavascript("CallbackManager.httpResponse('" + bbxEncode(id) + "', " + status + ", '" + bbxEncode(body) + "');");
    }
}

