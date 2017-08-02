package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.util.Log;

import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.HttpService.TAG;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.CURRENT_PREFS_KEY;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.filesPrefs;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class UIRequestHandler implements RequestHandler {

    HttpService service;

    public UIRequestHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        switch (path[0]) {
            case "contentLoaded":
//                return loadContent();
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Error in UI command.");
    }

    public static NanoHTTPD.Response loadContent() {
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
    }

}
