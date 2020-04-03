package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.util.Log;

import com.birdbraintechnologies.birdblox.MainWebView;
//import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.Status;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.MainWebView.setUiVis;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.CURRENT_PREFS_KEY;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.LAST_PROJECT_KEY;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.filesPrefs;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class UIRequestHandler implements RequestHandler {
    private static final String TAG = "UIRequestHandler";

    //HttpService service;

    //public UIRequestHandler(HttpService service) {
    //    this.service = service;
    //}
    public UIRequestHandler() {}

    @Override
    //public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
    public NativeAndroidResponse handleRequest(NativeAndroidSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "hideNavigationBar":
                setUiVis();
                return new NativeAndroidResponse(Status.OK, "navigation bar hidden");
            case "contentLoaded":
                Log.d(TAG, "contentLoaded");
                String currentFile = filesPrefs.getString(CURRENT_PREFS_KEY, null);
                String lastFile = filesPrefs.getString(LAST_PROJECT_KEY, null);
                if (currentFile != null) {
                    Log.d(TAG, "default filename " + currentFile);
                    runJavascript("CallbackManager.setFilePreference('" + currentFile + "');");
                } else if (lastFile != null) {
                    Log.d(TAG, "last filename " + lastFile);
                    runJavascript("CallbackManager.setFilePreference('" + lastFile + "');");
                }

//                return loadContent();
                //break;
                return new NativeAndroidResponse(Status.OK, "content loaded");
            case "translatedStrings":
                Log.d(TAG, "translatedStrings");
                MainWebView.name_error_already_exists = m.get("Name_error_already_exists").get(0);
                MainWebView.cancel_text = m.get("Cancel").get(0);
                MainWebView.rename_text = m.get("Rename").get(0);
                MainWebView.ok_text = m.get("OK").get(0);
                MainWebView.enter_new_name = m.get("Enter_new_name").get(0);
                MainWebView.delete_text = m.get("Delete").get(0);
                MainWebView.delete_question = m.get("Delete_question").get(0);
                MainWebView.loading_text = m.get("Loading").get(0);

                //break;
                return new NativeAndroidResponse(Status.OK, "String translations received.");
        }
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Error in UI command.");
        return new NativeAndroidResponse(Status.BAD_REQUEST, "Error in UI command.");
    }

/*    public static NanoHTTPD.Response loadContent() {
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            try {
                File file = new File(getBirdbloxDir(), currProj + "/program.xml");
                if (file.exists()) {
                    runJavascript("CallbackManager.data.open('" + bbxEncode(currProj) + "', \"" + bbxEncode(FileUtils.readFileToString(file, "utf-8")) + "\");");
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Project " + currProj + " loaded.");
                }
            } catch (SecurityException | IOException e) {
                Log.e(TAG, "Error while opening file: " + e.getMessage());
            }
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "No current projects found.");
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while opening current project.");
    }*/

}
