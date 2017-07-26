package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Handler for handling getting and setting of arbitrary device properties
 *
 * @author Shreyan Bakshi (AppyFizz)
 */

public class PropertiesHandler implements RequestHandler {

    HttpService service;
    public static DisplayMetrics metrics;

    public PropertiesHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        switch (path[0]) {
            case "dims":
                responseBody = getDeviceScreenSize();
                break;
            case "os":
                responseBody = getDeviceOSVersion();
                break;
            default:
                break;
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Gets the screen size of the device
     *
     * @return String representing the screen "width, height" of the device
     */
    private static String getDeviceScreenSize() {
        // Get device screen width and height in mm
        double mXDpi = metrics.xdpi;
        double screen_height_in_mm = (metrics.heightPixels * 25.4) / (mXDpi);
        double screen_width_in_mm = (metrics.widthPixels * 25.4) / (mXDpi);
        return Double.toString(screen_width_in_mm) + "," + Double.toString(screen_height_in_mm);
    }

    /**
     * Gets the screen size of the device
     *
     * @return String representing the Manufacturer and OS Version
     * "Manufacturer (OS Version)" of the device
     */
    private String getDeviceOSVersion() {
        // Get device manufacturer
        String res;
        Log.d("OS", Build.MANUFACTURER + " " + Build.MODEL);
        if (Build.MANUFACTURER.equals("Amazon")) {
            res = "Kindle (";
        } else {
            res = "Android (";
        }
        res += Build.VERSION.RELEASE + ")";
        return res;
    }

}
