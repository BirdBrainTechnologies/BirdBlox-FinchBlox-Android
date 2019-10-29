package com.birdbraintechnologies.birdblox;

import android.content.Context;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestRouter;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;



/**
 * Class for handling javascript requests from the frontend. Replaces the http server.
 */
public class JavascriptInterface {
    private final String TAG = this.getClass().getSimpleName();
    Context mainContext;
    private BluetoothHelper btService;
    private RequestRouter router;

    JavascriptInterface(Context c) {
        mainContext = c;

        btService = new BluetoothHelper(mainContext);
        router = new RequestRouter(mainContext, btService);
    }


    /**
     * Incoming request from the frontend. Will use CallbackManager to send the response.
     * @param jsonRequest
     */
    @android.webkit.JavascriptInterface
    public void sendAndroidRequest(String jsonRequest){
        Log.d(TAG, "Got frontend request: " + jsonRequest);

        NativeAndroidSession session = new NativeAndroidSession(jsonRequest);

        NativeAndroidResponse response = router.routeAndDispatch(session);

        String id = session.getRequestId();
        String status = response.getStatus();
        String body = response.getBody();

        runJavascript("CallbackManager.httpResponse('" + bbxEncode(id) + "', " + status + ", '" + bbxEncode(body) + "');");
    }
}
