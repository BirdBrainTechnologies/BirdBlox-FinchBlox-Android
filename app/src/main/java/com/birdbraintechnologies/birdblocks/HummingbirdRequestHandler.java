package com.birdbraintechnologies.birdblocks;

import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public class HummingbirdRequestHandler implements RequestHandler {

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session) {
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "2");
        return r;
    }
}
