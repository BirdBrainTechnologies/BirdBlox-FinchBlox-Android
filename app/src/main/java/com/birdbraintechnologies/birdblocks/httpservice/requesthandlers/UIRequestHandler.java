package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblocks.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblocks.httpservice.HttpService.TAG;
import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.FileManagementHandler.CURRENT_PREFS_KEY;
import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.FileManagementHandler.NAMED_PREFS_KEY;
import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.FileManagementHandler.filesPrefs;
import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.FileManagementHandler.getBirdblocksDir;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author AppyFizz (Shreyan Bakshi)
 */

public class UIRequestHandler implements RequestHandler {

    HttpService service;

    public UIRequestHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        switch (path[0]) {
            case "contentLoaded":
                return loadContent();
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Error in UI command.");
    }

    public static NanoHTTPD.Response loadContent() {
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        boolean isNamed = filesPrefs.getBoolean(NAMED_PREFS_KEY, false);
        if (currProj != null) {
            currProj = bbxEncode(currProj);
            try {
                File file = new File(getBirdblocksDir(), currProj + "/program.xml");
                if (file.exists()) {
                    runJavascript("CallbackManager.data.open('" + currProj + "', \"" + bbxEncode(FileUtils.readFileToString(file, "utf-8")) + "\", " + isNamed + ");");
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
