package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.os.Environment;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/23/17.
 */

public class FileManagementHandler implements RequestHandler {
    private static final String TAG = FileManagementHandler.class.getName();
    private static final String BIRDBLOCKS_SAVE_DIR = "Birdblocks";
    private static final String FILE_NOT_FOUND_RESPONSE = "File Not Found";

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");

        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "save":
                saveFile(path[1], session);
                break;
            case "load":
                responseBody = loadFile(path[1]);
                break;
            case "rename":
                renameFile(path[1], path[2]);
                break;
            case "delete":
            case "files":
                responseBody = listFiles();
                break;
            case "export":
        }

        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private void saveFile(String filename, NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            Log.d(TAG, "Save must be done via POST request");
            return;
        }
        Map<String, String> postFiles = new HashMap<>();
        File newFile = new File(getBirdblocksDir(), filename);
        try {
            // Parse post body to get parameters
            session.parseBody(postFiles);

            // Write "data" to file
            FileWriter writer = new FileWriter(newFile);
            writer.write(session.getParameters().get("data").get(0));
            writer.close();
        } catch (IOException e) {
            newFile.delete();
            Log.e(TAG, e.toString());
        } catch (NanoHTTPD.ResponseException e) {
            Log.e(TAG, e.toString());
        }
    }

    private String loadFile(String filename) {
        File file = new File(getBirdblocksDir(), filename);
        if (!file.exists()) {
            return FILE_NOT_FOUND_RESPONSE;
        }
        StringBuilder response = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while((line = reader.readLine()) != null) {
                response.append(line + "\n");
            }
        } catch (FileNotFoundException e) {
            return FILE_NOT_FOUND_RESPONSE;
        } catch (IOException e) {
            Log.d(TAG, "Error reading saved file: " + e.toString());
        }
        return response.toString().trim();
    }

    private void renameFile(String oldFilename, String newFilename) {
        File file = new File(getBirdblocksDir(), oldFilename);
        if (!file.exists()) {
            return;
        }
        file.renameTo(new File(getBirdblocksDir(), newFilename));
    }

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

    private File getBirdblocksDir() {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), BIRDBLOCKS_SAVE_DIR);
        if (!file.mkdirs()) {
            return file;
        }
        Log.d(TAG, "Created BirdBlocks save directory: " + file.getPath());
        return file;
    }

}
