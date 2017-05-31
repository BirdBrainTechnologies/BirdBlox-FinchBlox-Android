package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.MainWebView;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Request handler for managing files on the device.
 *
 * @author Terence Sun (tsun1215), Shreyan Bakshi (AppyFizz)
 */
public class FileManagementHandler implements RequestHandler {
    private static final String TAG = FileManagementHandler.class.getName();
    private static final String BIRDBLOCKS_SAVE_DIR = "Birdblocks";
    private static final String FILE_NOT_FOUND_RESPONSE = "File Not Found";

    private HttpService service;

    public FileManagementHandler(HttpService service) {
        this.service = service;
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "save":
                saveFile(m.get("filename").get(0), session);
                break;
            case "load":
                responseBody = loadFile(m.get("filename").get(0));
                break;
            case "rename":
                renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0));
                break;
            case "delete":
                deleteFile(m.get("filename").get(0));
                break;
            case "files":
                responseBody = listFiles();
                break;
            case "export":
                exportFile(m.get("filename").get(0), session);
                break;
        }

        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }


    /**
     * Saves a file to the device (Only supports POST requests)
     *
     * @param filename Name of the file to save
     * @param session  HttpRequest to get the POST body of
     */
    private void saveFile(String filename, NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            Log.d(TAG, "Save must be done via POST request");
            return;
        }
        Map<String, String> postFiles = new HashMap<>();
        File newFile = new File(getBirdblocksDir(), filename);
        try {
            // Parse POST body to get parameters
            session.parseBody(postFiles);
            // Write POST["data"] to file
            FileWriter writer = new FileWriter(newFile);
            writer.write(postFiles.get("postData"));
            writer.close();
        } catch (IOException e) {
            newFile.delete();
            Log.e(TAG, e.toString());
        } catch (NanoHTTPD.ResponseException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Loads a file from the device
     *
     * @param filename Name of the file to load
     * @return String contents of the file
     */
    private String loadFile(String filename) {
        File file = new File(getBirdblocksDir(), filename);
        if (!file.exists()) {
            return FILE_NOT_FOUND_RESPONSE;
        }
        StringBuilder response = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line + "\n");
            }
        } catch (FileNotFoundException e) {
            return FILE_NOT_FOUND_RESPONSE;
        } catch (IOException e) {
            Log.d(TAG, "Error reading saved file: " + e.toString());
        }
        return response.toString().trim();
    }

    /**
     * Renames a saved file on the device
     *
     * @param oldFilename Old file name
     * @param newFilename New name of file
     */
    private void renameFile(String oldFilename, String newFilename) {
        File file = new File(getBirdblocksDir(), oldFilename);
        if (!file.exists()) {
            return;
        }
        file.renameTo(new File(getBirdblocksDir(), newFilename));
    }

    /**
     * Deletes a saved file on the device
     *
     * @param filename Name of file to delete
     */
    private void deleteFile(String filename) {
        File file = new File(getBirdblocksDir(), filename);
        if (!file.exists()) {
            return;
        }
        file.delete();
    }

    /**
     * Lists the files on the device
     *
     * @return List of files on the device separated by \n
     */
    private String listFiles() {
        File[] files = getBirdblocksDir().listFiles();
        String response = "";
        if (files == null) {
            return response;
        }
        for (int i = 0; i < files.length; i++) {
            response += files[i].getName() + "\n";
        }
        return response;
    }

    /**
     * Starts a share command for a saved file on the device
     *
     * @param filename Name of the file to share
     * @param session  HttpRequest containing the most up to date contents of the file
     */
    private void exportFile(String filename, NanoHTTPD.IHTTPSession session) {
        // Save the updated contents (in case they were updated)
        saveFile(filename, session);

        // Create share intent on the main activity
        File file = new File(getBirdblocksDir(), filename);
        if (file.exists()) {
            Intent showDialog = new Intent(MainWebView.SHARE_FILE);
            showDialog.putExtra("file_uri", Uri.fromFile(file));
            LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
        }
    }

    /**
     * Gets the BirdBlocks save directory
     *
     * @return File object for the save directory
     */
    private File getBirdblocksDir() {
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), BIRDBLOCKS_SAVE_DIR);
        if (!file.mkdirs()) {
            return file;
        }
        Log.d(TAG, "Created BirdBlocks save directory: " + file.getPath());
        return file;
    }

}
