package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.SharedPreferences;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.users.FullAccount;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

import static android.content.Context.MODE_PRIVATE;
import static com.birdbraintechnologies.birdblocks.MainWebView.mainWebViewContext;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxRequestHandler implements RequestHandler {

    private final String TAG = this.getClass().getName();

    HttpService service;

    private final static String DB_APP_KEY = "fgml2igl5ka67lr";

    public static String DB_ACCESS_TOKEN;

    private static DbxRequestConfig dropboxConfig;
    private static DbxClientV2 dropboxClient;

    SharedPreferences sharedPrefs;

    public static final String DB_PREFS_KEY = "com.birdbraintechnologies.birdblocks.DROPBOX_ACCESS_TOKEN";

    public DropboxRequestHandler(HttpService service) {
        this.service = service;

        // Get Dropbox Access Token
        // Reuses old token if already present.
        sharedPrefs = mainWebViewContext.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE);
        String accessToken = sharedPrefs.getString(DB_PREFS_KEY, null);
        if (accessToken == null) {
//            DB_ACCESS_TOKEN = getDropboxAccessToken();
        } else {
            DB_ACCESS_TOKEN = accessToken;
            // Create Dropbox client
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, DB_ACCESS_TOKEN);
        }

    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        switch (path[0]) {
            case "signIn":
                responseBody = getDropboxAccessToken();
                Log.d("DPBX", responseBody);
                break;
            case "isSignedIn":
                break;
            case "signOut":
                break;
            case "list":
                break;
            case "open":
                break;
            case "rename":
                break;
            case "delete":
                break;
            case "export":
                break;
            default:
                break;
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private String getDropboxAccessToken() {
        Auth.startOAuth2Authentication(mainWebViewContext, DB_APP_KEY);
        DB_ACCESS_TOKEN = Auth.getOAuth2Token();
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(DB_PREFS_KEY, DB_ACCESS_TOKEN);
        editor.apply();
        return DB_ACCESS_TOKEN;
    }

    private String getDropboxSignIn() {
        try {
            // Get current account info
            FullAccount account = dropboxClient.users().getCurrentAccount();
            return account.getAccountId();
        } catch (DbxException e) {
            Log.e(TAG, "Exception getting dropbox account ID: " + e.getMessage());
        }
        return null;
    }

    ;

//    private void loadData() {
//        new GetCurrentAccountTask(DropboxClientFactory.getClient(), new GetCurrentAccountTask.Callback() {
//            public void onComplete(FullAccount result) {
//                // result.getEmail();
//                // result.getName().getDisplayName();
//                // result.getAccountType().name();
//
//            }
//
//            public void onError(Exception e) {
//                Log.e(getClass().getName(), "Failed to get account details.", e);
//            }
//        }).execute();
//    };
//
//    protected boolean hasToken() {
//        SharedPreferences prefs = mainWebViewContext.getSharedPreferences("dropbox-sample", MODE_PRIVATE);
//        String accessToken = prefs.getString("access-token", null);
//        return accessToken != null;
//    }

}

