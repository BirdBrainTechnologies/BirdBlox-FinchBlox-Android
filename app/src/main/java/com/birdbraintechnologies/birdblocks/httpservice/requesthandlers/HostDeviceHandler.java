package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/17/17.
 */

public class HostDeviceHandler implements RequestHandler {
    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        switch(path[0]) {
            case "shake":
                break;
            case "location":
                break;
            case "ssid":
                break;
            case "pressure":
                break;
            case "altitude":
                break;
            case "orientation":
                break;
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }
}
