package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
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
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DebugRequestHandler implements RequestHandler {
    private final String TAG = this.getClass().getSimpleName();

    private final static String LOG_DIR = "LOG";

    //HttpService service;
    private Context context;

    //public DebugRequestHandler(HttpService service) {
    //    this.service = service;
    public DebugRequestHandler(Context context) {
        this.context = context;
    }

    @Override
    //public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
    public NativeAndroidResponse handleRequest(NativeAndroidSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        switch (path[0]) {
            case "log":
                return appendToLog(session);
            case "shareLog":
                return shareLog();
        }
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Error in Debug command.");
        return new NativeAndroidResponse(Status.BAD_REQUEST, "Error in Debug command.");
    }

    /**
     * Appends the received message to the log file, creating the file
     * if it doesn't exist.
     *
     * @param session HttpRequest to get the POST body of.
     * @return A 'OK' response if logging was successful,
     * and an 'ERROR' response otherwise.
     */
    //private NanoHTTPD.Response appendToLog(NanoHTTPD.IHTTPSession session) {
    private NativeAndroidResponse appendToLog(NativeAndroidSession session) {
        //if (session.getMethod() != NanoHTTPD.Method.POST) {
        //    Log.d(TAG, "Log: Messages must be sent via POST request");
        //    return NanoHTTPD.newFixedLengthResponse(
        //            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Please send a POST request.");
        //}
        String message = session.getBody();
        if (message.equals("")) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "String to append to log is empty.");
        }
        try {
            //Map<String, String> postFiles = new HashMap<>();
            // Parse POST body to get parameters
            //session.parseBody(postFiles);
            // Obtain the message to be written to file from "postData"
            //String message = postFiles.get("postData");

            // Get the log file
            File logFile = new File(mainWebViewContext.getFilesDir(), LOG_DIR + "/Log.txt");
            // If the log file doesn't exist yet, create a blank log file
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }
            // Size of the Log file, in kilobytes
            long file_size = FileUtils.sizeOf(logFile) / 1024;
            // If size less than 40 Kb, append new message to the log file
            if (file_size < 40) {
                // Create the String to be written to the file
                String toWrite = DateFormat.getDateTimeInstance().format(new Date());
                toWrite += "\n";
                toWrite += message;
                toWrite += "\n\n\n";
                // Append the String to the log file
                FileUtils.writeStringToFile(logFile, toWrite, "utf-8", true);
            }
            // Else, split log file in half, discard first half, append new message to second half
            else {
                // Get second 'half' of previous file contents as a String
                String toWrite = FileUtils.readFileToString(logFile, "utf-8");
                toWrite = toWrite.substring(toWrite.length() / 2);
                // Append new log message to this String
                toWrite += "\n\n\n";
                toWrite += DateFormat.getDateTimeInstance().format(new Date());
                toWrite += "\n";
                toWrite += message;
                toWrite += "\n\n\n";
                // Overwrite the log file with this String
                FileUtils.writeStringToFile(logFile, toWrite, "utf-8", false);
            }
            // Return OK Response
            //return NanoHTTPD.newFixedLengthResponse(
            //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully modified log file.");
            return new NativeAndroidResponse(Status.OK, "Successfully modified log file.");
        //} catch (IOException | SecurityException | NullPointerException | IllegalStateException | IllegalArgumentException | NanoHTTPD.ResponseException e) {
        } catch (IOException | SecurityException | NullPointerException | IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "Error while writing to log file: " + e.getMessage());
        }
        // Return Error Response
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while storing log message.");
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while storing log message.");
    }

    /**
     * Opens a 'share dialog' through which the user can export
     * the log file.
     *
     * @return A 'OK' response if opening the dialog was successful,
     * and an 'ERROR' response otherwise.
     */
    //private NanoHTTPD.Response shareLog() {
    private NativeAndroidResponse shareLog() {
        try {
            File logFile = new File(mainWebViewContext.getFilesDir(), LOG_DIR + "/Log.txt");
            // If the log file doesn't exist yet, create a blank log file
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                FileUtils.writeStringToFile(logFile, "BEGIN ERROR LOG:\n\n\n", "utf-8", true);
            }
            if (logFile.exists()) {
                // Send broadcast to MainWebView, to show share dialog for sharing the log file
                Intent showDialog = new Intent(MainWebView.SHARE_LOG);
                showDialog.putExtra("log_file_path", logFile.getAbsolutePath());
                //LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
                LocalBroadcastManager.getInstance(context).sendBroadcast(showDialog);
                // Return OK Response
                //return NanoHTTPD.newFixedLengthResponse(
                //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully shared log file");
                return new NativeAndroidResponse(Status.OK, "Successfully shared log file");
            }
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Error sharing log file: " + e.getMessage());
        }
        // Return Error Response
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while sharing log file.");
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while sharing log file.");
    }
}
