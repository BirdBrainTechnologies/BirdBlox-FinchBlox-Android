package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblox.MainWebView;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DebugRequestHandler implements RequestHandler {
    private final String TAG = this.getClass().getName();

    private final static String LOG_DIR = "LOG";

    HttpService service;

    public DebugRequestHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "log":
                return appendToLog(m.get("msg").get(0));
            case "shareLog":
                return shareLog();
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Error in Debug command.");
    }


    /**
     * @param message
     * @return
     */
    private NanoHTTPD.Response appendToLog(String message) {
        try {
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
                // TODO: Implementing splitting in half correctly
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
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully modified log file.");
        } catch (IOException | SecurityException | NullPointerException | IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "Error while writing to log file: " + e.getMessage());
        }
        // Return Error Response
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while storing log message.");
    }

    /**
     * @return
     */
    private NanoHTTPD.Response shareLog() {
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
                LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
                // Return OK Response
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully shared log file");
            }
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Error sharing log file: " + e.getMessage());
        }
        // Return Error Response
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while sharing log file.");
    }
}
