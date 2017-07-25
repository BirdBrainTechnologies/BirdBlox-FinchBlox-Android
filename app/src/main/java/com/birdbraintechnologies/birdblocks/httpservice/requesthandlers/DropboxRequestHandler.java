package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblocks.util.ProgressOutputStream;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static android.content.Context.MODE_PRIVATE;
import static com.birdbraintechnologies.birdblocks.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxRequestHandler implements RequestHandler {

    private final String TAG = this.getClass().getName();

    HttpService service;

    private final static String DB_APP_KEY = "fgml2igl5ka67lr";

    public static DbxRequestConfig dropboxConfig;
    public static DbxClientV2 dropboxClient;

    SharedPreferences dropboxPrefs;

    public static final String DB_PREFS_KEY = "com.birdbraintechnologies.birdblocks.DROPBOX_ACCESS_TOKEN";

    public DropboxRequestHandler(HttpService service) {
        this.service = service;

        // If access token already present, intialize.
        dropboxPrefs = mainWebViewContext.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE);
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken != null) {
            // Create Dropbox client
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, accessToken);
        }
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "signIn":
                return dropboxSignIn();
            case "signOut":
                return dropboxSignOut();
            case "list":
                return listDropboxFolder();
            case "download":
                return startDropboxDownload(m.get("filename").get(0));
            case "upload":
                return startDropboxUpload(m.get("filename").get(0));
            case "rename":
                break;
            case "delete":
                break;
            default:
                break;
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request");
    }

    private NanoHTTPD.Response dropboxSignIn() {
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken == null) {
            obtainDropboxAccessToken();
        } else {
            // Create Dropbox client
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, accessToken);
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Sign In Process started");
    }

    private void obtainDropboxAccessToken() {
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Auth.startOAuth2Authentication(mainWebViewContext.getApplicationContext(), DB_APP_KEY);
            }
        });
    }

    static String getDropboxSignIn() {
        if (dropboxClient != null) {
            try {
                // Get current account email
                FullAccount account = dropboxClient.users().getCurrentAccount();
                return account.getEmail();
            } catch (DbxException e) {
                Log.e("DropboxRequestHandler", "Exception getting dropbox account ID: " + e.getMessage());
            }
        }
        return null;
    }

    static boolean dropboxSignedIn() {
        return mainWebViewContext.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE).getString("access-token", null) != null;
    }

    private NanoHTTPD.Response dropboxSignOut() {
        try {
            if (dropboxClient != null) {
                dropboxClient = null;
            }
            if (dropboxConfig != null) {
                dropboxConfig = null;
            }
            if (dropboxPrefs.getString("access-token", null) != null) {
                dropboxPrefs.edit().putString("access-token", null).apply();
            }
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Signed out successfully");
        } catch (Exception e) {
            Log.e(TAG, "Dropbox sign out: " + e.getMessage());
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while signing out: " + e.getMessage());
        }
    }

    private NanoHTTPD.Response listDropboxFolder() {
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken == null) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No Dropbox account detected.");
        } else if (dropboxClient == null) {
            // Create Dropbox client
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, accessToken);
        }
        JSONObject obj = dropboxAppFolderContents();
        if (obj != null) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, obj.toString());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Error while listing contents of Dropbox folder.");
    }

    private JSONObject dropboxAppFolderContents() {
        try {
            JSONArray arr = new JSONArray();
            ListFolderResult result = dropboxClient.files().listFolder("");
            while (true) {
                for (Metadata metadata : result.getEntries()) {
                    arr.put(metadata.getPathLower());
                }
                if (!result.getHasMore()) {
                    break;
                }
                result = dropboxClient.files().listFolderContinue(result.getCursor());
            }
            JSONObject obj = new JSONObject();
            obj.put("files", arr);
            return obj;
        } catch (DbxException | JSONException e) {
            Log.e(TAG, "listFolder: " + e.getMessage());
        }
        return null;
    }

    private NanoHTTPD.Response startDropboxDownload(final String name) {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully started download and unzip of project: " + name);
    }

    private void dropboxDownload(final String name) {
        /**
         * Implemented own {@link ProgressOutputStream}, since Dropbox API V2 has no built-in download progress.
         */
        String dropboxPath = "/" + name + ".bbx";
        File dbxDown = new File(mainWebViewContext.getFilesDir(), dropboxPath);
        try {
            try {
                dropboxClient.files().getMetadata(dropboxPath);
            } catch (GetMetadataErrorException e) {
                if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                    Log.e(TAG, "Download: File " + name + " not found.");
                    // TODO: Display this error to the user
                    runJavascript("CallbackManager.cloud.filesChanged(" + dropboxAppFolderContents().toString() + ")");
                } else {
                    throw e;
                }
            }
            if (!dbxDown.exists()) {
                dbxDown.getParentFile().mkdirs();
                dbxDown.createNewFile();
            }
            FileOutputStream fout = new FileOutputStream(dbxDown);
            try {
                DbxDownloader<FileMetadata> dbxDownloader = dropboxClient.files().download("/" + name + ".bbx");
                long size = dbxDownloader.getResult().getSize();
                dbxDownloader.download(new ProgressOutputStream(size, fout, new ProgressOutputStream.Listener() {
                    @Override
                    public void progress(long completed, long totalSize) {
                        // TODO: Display progress dialog to the user
                        // update progress bar here ...
                        if (completed == totalSize) {
                            runJavascript("CallbackManager.cloud.downloadComplete(" + name + ")");
                        }
                    }
                }));
            } finally {
                fout.close();
            }
        } catch (DbxException | IOException e) {
            Log.e(TAG, "Unable to download file " + name + ": " + e);
        }
    }

    private NanoHTTPD.Response startDropboxUpload(String name) {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully started upload of project: " + name);
    }

}

