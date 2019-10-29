package com.birdbraintechnologies.birdblox.httpservice;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class NativeAndroidSession {
    private final String TAG = this.getClass().getSimpleName();
    private String request;
    private String body;
    private String id;
    private String inBackground;
    private Map<String, List<String>> parms;

    public NativeAndroidSession(String jsonRequest) {
        try {
            JSONObject jsonObject = new JSONObject(jsonRequest);
            request = "/" + jsonObject.getString("request");
            body = jsonObject.getString("body");
            id = jsonObject.getString("id");
            inBackground = jsonObject.getString("inBackground");

            if (body == null) {
                body = "";
            }

            Log.d(TAG, "Parts: request='" + request + "' body='" + body + "' id='" + id + "' inBackground='" + inBackground + "'");

            /*JSONObject newJSON = jsonObject.getJSONObject("stat");
            System.out.println(newJSON.toString());
            jsonObject = new JSONObject(newJSON.toString());
            System.out.println(jsonObject.getString("rcv"));
            System.out.println(jsonObject.getJSONArray("argv"));*/
        } catch (JSONException e) {
            e.printStackTrace();
        }

        parms = new HashMap<String, List<String>>();

        String [] split = request.split("\\?", 2);
        if (split.length == 2) {
            String [] parmStrings = split[1].split("&");
            for (String parmString : parmStrings) {
                String[] splitParm = parmString.split("=", 2);
                String key = splitParm[0];
                String value = "";
                if (splitParm.length == 2) { value = splitParm[1]; }

                List<String> values = parms.get(key);
                if (values == null) {
                    values = new ArrayList<String>();
                    parms.put(key, values);
                }

                values.add(value);
            }

        }
    }

    public String getRequestId () {
        return id;
    }

    public String getUri () {
        String uri = request;
        int qmi = uri.indexOf('?');
        if (qmi >= 0) {
            uri = uri.substring(0, qmi);
        }
        return uri;
    }

    public Map<String, List<String>> getParameters() {
        return parms;
    }

    public String getBody() {
        return body;
    }
}
