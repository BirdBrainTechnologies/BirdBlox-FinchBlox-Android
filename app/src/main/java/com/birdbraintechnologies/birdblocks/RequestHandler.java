package com.birdbraintechnologies.birdblocks;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public interface RequestHandler {

    /**
     * Handles a request
     * @param session HttpSession generated
     * @return Response to the requeset
     */
    NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session);
    // TODO: Make this also take in the match object (or list of matching args)
}
