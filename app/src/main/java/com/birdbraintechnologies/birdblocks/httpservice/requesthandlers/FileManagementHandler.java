package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.MainWebView;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import org.json.JSONException;
import org.json.JSONObject;

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

import static android.R.attr.name;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * Request handler for managing files on the device.
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */
public class FileManagementHandler implements RequestHandler {
    private static final String TAG = FileManagementHandler.class.getName();
    private static final String BIRDBLOCKS_SAVE_DIR = "Saved";
    private static final String FILE_NOT_FOUND_RESPONSE = "File Not Found";
    public static File SecretFileDirectory;

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
                if (m.get("options") == null) {
                    responseBody = saveFile(m.get("filename").get(0), session, null);
                } else if (m.get("options").get(0).equals("new") || m.get("options").get(0).equals("soft")) {
                    responseBody = saveFile(m.get("filename").get(0), session, m.get("options").get(0));
                } else {
                    // bad request
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "");
                }
                if (responseBody == null) {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "");
                }
                Log.d("AutoSave",  "Save: " + responseBody);
                break;
            case "load":
                responseBody = loadFile(m.get("filename").get(0));
                break;
            case "rename":
                if (m.get("options") == null) {
                    responseBody = renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0), null);
                } else if (m.get("options").get(0).equals("soft")) {
                    responseBody = renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0), m.get("options").get(0));
                } else {
                    // bad request
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "");
                }
                if (responseBody == null) {
                    responseBody = "";
                } else if (responseBody.equals("409")) {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "");
                } else {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "");
                }
                Log.d("AutoSave",  "Rename: " + responseBody);
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
            case "getAvailableName":
                try {
                    responseBody = getAvailableName(m.get("filename").get(0));
                } catch (NullPointerException e) {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "");
                }
                Log.d("AutoSave",  "GetAvailableName: " + responseBody);
                break;
        }

        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Saves a file to the device (Only supports POST requests)
     *
     * @param filename Name of the file to save
     * @param session  HttpRequest to get the POST body of
     * @param option
     * @return
     */
    private String saveFile(String filename, NanoHTTPD.IHTTPSession session, String option) {
//        Log.d("AutoSaveSave", "Command to save with name: " + filename + "option: " + option);
//        Log.d("AutoSaveSave", "Name: " + filename + " is available? " + isNameAvailable(getBirdblocksDir(), filename));
//        Log.d("AutoSaveSave", "Name: " + filename + " is sanitized? " + isNameSanitized(filename));
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            Log.d(TAG, "Save must be done via POST request");
            return null;
        }
        Map<String, String> postFiles = new HashMap<>();
        if (option == null) {
            // forcibly attempt to write, and overwrite file with same name, if it exists
            // if name is not sanitized, throw 409 error
            if(!isNameSanitized(filename)) {
                // raise 409
                return null;
            }
        } else if (option.equals("soft")) {
            // try to save, and respond with 409 if name unavailable ot name is not sanitized
            if ((!isNameSanitized(filename)) || !isNameAvailable(getBirdblocksDir(), filename)) {
                // raise 409
                Log.d("AutoSaveSave", "One of them fails");
                return null;
            }
        } else if (option.equals("new")) {
            // try to save, and automatically find an available name
            filename = findAvailableName(getBirdblocksDir(), filename);
            if (filename == null) {
                // raise 409
                return null;
            }
        }
//        Log.d("AutoSaveSave", "Actually saving with name: " + filename + "option: " + option);
        // actually save file here
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
            return null;
        } catch (NanoHTTPD.ResponseException e) {
            Log.e(TAG, e.toString());
            return null;
        }
        return filename;
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
     * @param option      Can be null or "soft", depending on which
     *                    this method performs different actions
     * @return            Returns null if successful, otherwise returns
     *                    error code ("409" or "503") as string
     */
    private String renameFile(String oldFilename, String newFilename, String option) {
        File file = new File(getBirdblocksDir(), oldFilename);
        if (!file.exists() || !isNameSanitized(newFilename)) {
            // 409 if oldFile doesn't exist, or newFilename is corrupt
            return "409";
        }
        if (option == null) {
            // force rename if newFilename is valid, and overwrite file if it exists
            // do nothing extra here
        } else if (option.equals("soft")) {
            // throw 409 error if new name file already exists
            // else rename
            if (!isNameAvailable(getBirdblocksDir(), newFilename))
                return "409";
        }
        try {
            // actually rename file here
            file.renameTo(new File(getBirdblocksDir(), newFilename));
            return null;
        } catch (Exception e) {
            Log.e("Rename", e.getMessage());
            // 503
            return "503";
        }
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
            response += files[i].getName();
            if (i < files.length - 1) response += "\n";
        }
        return response;
    }

    /**
     * Starts a share command for a saved file on the device
     *
     * @param filename Name of the file to share
     * @param session  HttpRequest containing the most up to date contents of the file
     */
    private String exportFile(String filename, NanoHTTPD.IHTTPSession session) {
        /* SAVING HERE NO LONGER REQUIRED */
        // Save the updated contents (in case they were updated)
        // saveFile(filename, session);
        try {
            // Create share intent on the main activity
            File file = new File(getBirdblocksDir(), filename);
            if (file.exists()) {
                Intent showDialog = new Intent(MainWebView.SHARE_FILE);
                showDialog.putExtra("file_uri", Uri.fromFile(file));
                LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
            }
            return filename;
        } catch (Exception e) {
            Log.e("Export", e.getMessage());
            return null;
        }
    }

    /**
     *
     *
     * @param filename
     * @return Returns an available name for 'filename'
     */
    private String getAvailableName(String filename) {
        Log.d("AutoSave", "GetAvailableName: " + filename);
        try {
            JSONObject nameObject = new JSONObject();
            Log.d("AutoSave", "FileName 1: " + filename);
            nameObject.put("availableName", findAvailableName(getBirdblocksDir(), filename));
            Log.d("AutoSave", "FileName 2: " + filename);
            nameObject.put("alreadySanitized", isNameSanitized(filename));
            Log.d("AutoSave", "FileName 3: " + filename);
            nameObject.put("alreadyAvailable", isNameAvailable(getBirdblocksDir(), filename));
            Log.d("AutoSave", "FileName 4: " + filename);
            Log.d("AutoSave", "Available name found: " + findAvailableName(getBirdblocksDir(), filename));
            return nameObject.toString();
        } catch (JSONException | NullPointerException e) {
            Log.e("AvailableName", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if input filename contains any illegal characters
     *
     * @param name Input filename
     * @return     Returns false if 'name' contains any illegal characters,
     *             and true otherwise.
     */
    private static boolean isNameSanitized(String name) {
        if (name == null) return false;
        // Illegal characters are:
        // '\', '/', ':', '*', '?', '<', '>', '|', '.', '\n', '\r', '\0', '"', '$'
        return !name.matches(".*[\\\\/:*?<>|.\n\r\0\"$].*");
    }

    /**
     * Sanitizes filename, with any illegal characters replaced with underscores.
     *
     * @param name Input filename
     * @return     Returns sanitized name.
     *             (Returns null if name is null).
     */
    private static String sanitizeName (String name) {
        if (name == null) return null;
        if (isNameSanitized(name)) return name;
        // else
        return name.replaceAll("[\\\\/:*?<>|.\n\r\0\"$]", "_");
    }

    /**
     * @param dir   Directory in which the file is located
     * @param name  Input filename
     * @return      Returns false if there is already a file with the filename
     *              'name' in the directory 'dir', and true otherwise
     */
    private static boolean isNameAvailable(File dir, String name) {
        if (name == null) return true;
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.getName().equals(name)) return false;
        }
        return true;
    }

    /**
     * @param dir   Directory in which the file is located
     * @param name  Input filename
     * @return      Returns an available name for a file with filename 'name'
     *              in the directory 'dir'. (Returns null if error occurs)
     */
    public static String findAvailableName(File dir, String name) {
        if (isNameAvailable(dir, name)) return name;
        Log.d("AutoSave", "Is " + name + " available? " + isNameAvailable(dir, name));
        // else
        name = sanitizeName(name);
        if (name == null)
            // raise 409
            return null;
        try {
            File[] files = dir.listFiles();
            int n = 2;
            if (name.length() >= 3 && name.endsWith(")")) {
                int startIndex = name.length() - 1;
                while (startIndex >= 0) {
                    if (name.charAt(startIndex) == '(') break;
                    startIndex--;
                }
                if(startIndex < name.length() - 2) {
                    String number = name.substring(startIndex+1, name.length()-1);
                    // if the String 'number' actually contains a number 2 onwards
                    if (number.matches("^[1-9]\\d*$") && !number.equals("1"))
                        n = Integer.parseInt(number);
                    // remove the "(number)" part from the end of name
                    name = name.substring(0, name.length() - (number.length() + 2));
                }
            }
            for (int i = n; i <= files.length + n; i++) {
                String newName = name + "(" + i + ")";
                if (isNameAvailable(dir, newName)) return newName;
            }
        } catch (SecurityException e) {
            Log.e("FindName", e.getMessage());
        }
        return null;
    }

    /**
     * Gets the BirdBlocks save directory
     *
     * @return File object for the save directory
     */
    public static File getBirdblocksDir() {
        //File file = new File(Environment.getExternalStoragePublicDirectory(
        //        Environment.DIRECTORY_DOCUMENTS), BIRDBLOCKS_SAVE_DIR);

        File file = new File(SecretFileDirectory, BIRDBLOCKS_SAVE_DIR);
        if (!file.exists()) {
            try {
                file.mkdirs();
            } catch (SecurityException e) {
                Log.e("Save Directory", "" + e);
            }
        }
        Log.d(TAG, "Created BirdBlocks save directory: " + file.getPath());
        return file;
    }

}
