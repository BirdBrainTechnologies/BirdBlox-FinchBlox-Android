package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.SharedPreferences;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/23/17.
 */

public class SettingsHandler implements RequestHandler {
    public static final String PREFS_NAME = "Settings";
    private static final String DEFAULT_VALUE = "Default";
    private SharedPreferences settings;

    public SettingsHandler(HttpService service) {
        settings = service.getSharedPreferences(PREFS_NAME, 0);
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");

        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "get":
                responseBody = getSetting(path[1]);
                break;
            case "set":
                putSetting(path[1], path[2]);
                break;
        }

        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private String getSetting(String key) {
        return settings.getString(key, DEFAULT_VALUE);
    }
    private void putSetting(String key, String value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(key, value);
        editor.commit();
    }
}
