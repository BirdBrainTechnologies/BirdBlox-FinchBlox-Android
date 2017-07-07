package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblocks.MainWebView.webView;
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

    private SharedPreferences prefsCurrent;
    static final String CURRENT_PREFS_KEY = "com.birdbraintechnologies.birdblocks.CURRENT_PROJECT";
    private SharedPreferences prefsNamed;
    private static final String NAMED_PREFS_KEY = "com.birdbraintechnologies.birdblocks.IS_FILE_NAMED";

    private HttpService service;

    public FileManagementHandler(HttpService service) {
        this.service = service;
        prefsCurrent = service.getSharedPreferences(CURRENT_PREFS_KEY, Context.MODE_PRIVATE);
        prefsNamed = service.getSharedPreferences(NAMED_PREFS_KEY, Context.MODE_PRIVATE);
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        switch (path[0]) {
//            case "save":
//                if (m.get("options") == null) {
//                    responseBody = saveFile(m.get("filename").get(0), session, null, ".bbx");
//                } else if (m.get("options").get(0).equals("new") || m.get("options").get(0).equals("soft")) {
//                    responseBody = saveFile(m.get("filename").get(0), session, m.get("options").get(0), ".bbx");
//                } else {
//                    // bad request
//                    return NanoHTTPD.newFixedLengthResponse(
//                            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "");
//                }
//                if (responseBody == null) {
//                    return NanoHTTPD.newFixedLengthResponse(
//                            NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "");
//                }
//                Log.d("AutoSave",  "Save: " + responseBody);
//                break;
//            case "load":
//                Log.d("MainWebView", "Open called: " + m.get("filename").get(0));
//                responseBody = loadFile(m.get("filename").get(0), ".bbx");
//                break;
//            case "rename":
//                if (m.get("options") == null) {
//                    responseBody = renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0), null, ".bbx");
//                } else if (m.get("options").get(0).equals("soft")) {
//                    responseBody = renameFile(m.get("oldFilename").get(0), m.get("newFilename").get(0), m.get("options").get(0), ".bbx");
//                } else {
//                    // bad request
//                    return NanoHTTPD.newFixedLengthResponse(
//                            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "");
//                }
//                if (responseBody == null) {
//                    responseBody = "";
//                } else if (responseBody.equals("409")) {
//                    return NanoHTTPD.newFixedLengthResponse(
//                            NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "");
//                } else {
//                    return NanoHTTPD.newFixedLengthResponse(
//                            NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "");
//                }
//                Log.d("AutoSave",  "Rename: " + responseBody);
//                break;
//            case "delete":
//                if (m.get("recording") == null || m.get("recording").get(0).equals("false"))
//                    deleteFile(m.get("filename").get(0), false, ".bbx");
//                else if (m.get("recording").get(0).equals("true"))
//                    deleteFile(m.get("filename").get(0), true, ".m4a");
//                else {
//                    //bad request
//                }
//                break;
            case "open":
                return openProject(m.get("filename").get(0));
            case "rename":
                return renameProject(m.get("oldFilename").get(0), m.get("newFilename").get(0));
            case "close":
                return closeProject();
            case "delete":
                return deleteProject(m.get("filename").get(0));
            case "files":
                return listProjects();
            case "export":
                return exportProject(m.get("filename").get(0));
            case "autoSave":
                break;
            case "new":
                break;
            case "getAvailableName":
                break;
            case "duplicate":
                break;
//            case "files":
//                responseBody = listFiles(".bbx");
//                break;
//            case "export":
//                exportFile(m.get("filename").get(0), ".bbx", session);
//                break;
//            case "getAvailableName":
//                try {
//                    responseBody = getAvailableName(m.get("filename").get(0), ".bbx");
//                } catch (NullPointerException e) {
//                    return NanoHTTPD.newFixedLengthResponse(
//                            NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "");
//                }
//                Log.d("AutoSave",  "GetAvailableName: " + responseBody);
//                break;
        }

        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
        return r;
    }


    /**
     * Opens the 'program.xml' file of the given project, if it exists.
     *
     * @param name The name of the project to be opened.
     * @return A 'OK' response if opening was successful,
     *             and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response openProject(String name) {
        if (!isNameSanitized(name)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, name + " is not a valid project name!");
        }
        File program = new File(getBirdblocksDir(), name + "/program.xml");
        if (!program.exists()) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Project " + name + " was not found!");
        }
        String encodedXML;
        String encodedName;
        try {
            encodedXML = URLEncoder.encode(getStringFromFile(program), "utf-8");
            encodedName = URLEncoder.encode(name, "utf-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Error while percent encoding file contents: " + e.getMessage());
            // TODO: Toast - File corrupted
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.UNSUPPORTED_MEDIA_TYPE, MIME_PLAINTEXT, "File corrupted");
        }
        if (webView != null && encodedXML != null) {
            webView.loadUrl("javascript:CallbackManager.data.open('" + encodedName + "', \"" + encodedXML + "\", true);");
            prefsCurrent.edit().putString(CURRENT_PREFS_KEY, name).apply();
            prefsNamed.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, name + " successfully opened.");
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while opening " + name);
    }


    /**
     * Renames a given project to a new (given) name.
     *
     * @param oldName The old project name
     * @param newName The new project name
     * @return A 'OK' response if opening was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response renameProject(String oldName, String newName) {
        if (!projectExists(oldName) || !isNameSanitized(newName)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Given name(s) invalid, or project " + oldName + " doesn't exist.");
        } else if (!oldName.equals(newName) && projectExists(newName)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "Project called " + newName + " already exists");
        } else if (oldName.equals(prefsCurrent.getString(CURRENT_PREFS_KEY, "")) && webView != null) {
            try {
                String encodedNewName = URLEncoder.encode(newName, "utf-8");
                webView.loadUrl("javascript:CallbackManager.data.setName('" + encodedNewName + "');");
                prefsNamed.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Couldn't percent encode new project name '" + newName + "': " + e.getMessage());
            }
        }
        try {
            File file = new File(getBirdblocksDir(), oldName);
            if (file.renameTo(new File(getBirdblocksDir(), newName))) {
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Project " + oldName + " renamed to " + newName + " successfully");
            }
        } catch (SecurityException | NullPointerException e) {
            Log.e("TAG", "Error while renaming " + oldName + " to " + newName + ": " + e.getMessage());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while renaming " + oldName + " to " + newName + ".");
    }


    /**
     * Closes the currently opened project, if any.
     *
     * @return A 'OK' response.
     */
    private NanoHTTPD.Response closeProject() {
        prefsCurrent.edit().putString(CURRENT_PREFS_KEY, null).apply();
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Current project closed successfully");
    }


    /**
     * Deletes a given project, if it exists.
     *
     * @param name The name of the project to be deleted.
     * @return A 'OK' response if deletion was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response deleteProject(String name) {
        if (!isNameSanitized(name)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Given name invalid.");
        } else if (!projectExists(name)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Project " + name + " was not found!");
        } else if (prefsCurrent.getString(CURRENT_PREFS_KEY, "").equals(name) && webView != null) {
            // TODO: callback here
        }
        try {
            // TODO: Actually delete here.
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, name + " successfully deleted.");
        } catch (SecurityException e) {
            Log.e(TAG, "Error while deleting " + name + ": " + e.getMessage());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while deleting " + name);
    }


    private NanoHTTPD.Response listProjects() {
        File[] files = getBirdblocksDir().listFiles();
        String responseBody = "";
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    responseBody += file.getName() + "\n";
                }
            }
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody.trim());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while listing projects.");
    }

    private NanoHTTPD.Response exportProject(String name) {

        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while exporting project " + name);
    }


    /**
     * Starts a share command for a saved file on the device
     *
     * @param filename Name of the file to share
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @param session  HttpRequest containing the most up to date contents of the file
     */
//    private String exportFile(String filename, String extension, NanoHTTPD.IHTTPSession session) {
//        /* SAVING HERE NO LONGER REQUIRED */
//        // Save the updated contents (in case they were updated)
//        // saveFile(filename, session);
//        try {
//            // Create share intent on the main activity
//            File file = new File(getBirdblocksDir(), filename + extension);
//            if (file.exists()) {
//                Intent showDialog = new Intent(MainWebView.SHARE_FILE);
//                //showDialog.putExtra("file_uri", Uri.fromFile(file));
//                showDialog.putExtra("file_path", file.getAbsolutePath());
//                LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
//            }
//            return filename;
//        } catch (Exception e) {
//            Log.e("Export", e.getMessage());
//            return null;
//        }
//    }


    /**
     * Checks if input filename contains any illegal characters.
     *
     * @param name Input filename
     * @return Returns false if 'name' contains any illegal characters,
     *             and true otherwise.
     */
    private static boolean isNameSanitized(String name) {
        // Illegal characters are:
        // '\', '/', ':', '*', '?', '<', '>', '|', '.', '\n', '\r', '\0', '"', '$'
        return (name != null) && !name.matches(".*[\\\\/:*?<>|.\n\r\0\"$].*");
    }


    /**
     * Sanitizes filename, with any illegal characters replaced with underscores.
     *
     * @param name Input filename
     * @return Returns sanitized name.
     * (Returns null if name is null).
     */
    private static String sanitizeName(String name) {
        if (name == null) return null;
        if (isNameSanitized(name)) return name;
        // else
        return name.replaceAll("[\\\\/:*?<>|.\n\r\0\"$]", "_");
    }


    /**
     * @param dir       Directory in which the file is located
     * @param name      Input filename
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @return Returns false if there is already a file with the filename
     * 'name' in the directory 'dir', and true otherwise
     */
    private static boolean isNameAvailable(File dir, String name, String extension) {
        if (name == null) return true;
        name += extension;
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.getName().equals(name)) return false;
        }
        return true;
    }

    /**
     * @param dir       Directory in which the file is located
     * @param name      Input filename
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @return Returns an available name for a file with filename 'name'
     * in the directory 'dir'. (Returns null if error occurs)
     */
    public static String findAvailableName(File dir, String name, String extension) {
        try {
            if (name == null)
                // raise 409
                return null;
            name = sanitizeName(name);
            if (isNameAvailable(dir, name, extension)) return name;
            // else
            File[] files = dir.listFiles();
            int n = 2;
            if (name.length() > 2 && name.endsWith(")")) {
                int startIndex = name.length() - 2;
                while (startIndex >= 0) {
                    if (name.charAt(startIndex) == '(') break;
                    startIndex--;
                }
                if (startIndex < name.length() - 2) {
                    String number = name.substring(startIndex + 1, name.length() - 1);
                    // if the String 'number' actually contains a number 2 onwards
                    if (number.matches("^[1-9]\\d*$") && !number.equals("1"))
                        n = Integer.parseInt(number);
                    // remove the "(number)" part from the end of name
                    name = name.substring(0, name.length() - (number.length() + 2));
                }
            }
            for (int i = n; i <= files.length + n; i++) {
                String newName = name + "(" + i + ")";
                if (isNameAvailable(dir, newName, extension)) return newName;
            }
        } catch (SecurityException | NullPointerException e) {
            Log.e("FindName", e.getMessage());
        }
        return null;
    }




    /**
     * @param filename
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @return Returns an available name for 'filename'
     */
    private String getAvailableName(String filename, String extension) {
        try {
            JSONObject nameObject = new JSONObject();
            nameObject.put("availableName", findAvailableName(getBirdblocksDir(), filename, extension));
            nameObject.put("alreadySanitized", isNameSanitized(filename));
            nameObject.put("alreadyAvailable", isNameAvailable(getBirdblocksDir(), filename, extension));
            return nameObject.toString();
        } catch (JSONException | NullPointerException e) {
            Log.e("AvailableName", e.getMessage());
            return null;
        }
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

    // -----------------------------------------------------------------------------

    // HELPER FUNCTIONS.

    /**
     * Checks if the input name corresponds to an existing project.
     *
     * @param name The input project name
     * @return True if the given project exists on disk, false otherwise.
     */
    private boolean projectExists(String name) {
        return isNameSanitized(name) && new File(getBirdblocksDir(), name).exists();
    }

    // ------------------------------------------------------------------------------

    // UTILITY FUNCTIONS

    /**
     * Converts the contents of a given file to a String.
     * <p>
     * SOURCE: https://stackoverflow.com/questions/12910503/read-file-as-string
     *
     * @param file The required file
     * @return The contents of 'file' as a single String
     */
    private static String getStringFromFile(File file) {
        String XML;
        try {
            FileInputStream fin = new FileInputStream(file);
            XML = convertStreamToString(fin);
            fin.close();
        } catch (IOException e) {
            Log.e(TAG, "Error while reading XML file: " + e.getMessage());
            XML = null;
        }
        return XML;
    }

    /**
     * Converts a given FileInputStream to a String.
     * <p>
     * SOURCE: http://www.java2s.com/Code/Java/File-Input-Output/ConvertInputStreamtoString.htm
     *
     * @param is The FileInputStream to be converted to a String
     * @return The String form of the given FileInputStream
     * @throws IOException
     */
    private static String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        Boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                sb.append(line);
                firstLine = false;
            } else {
                sb.append("\n").append(line);
            }
        }
        reader.close();
        return sb.toString();
    }


    // -------------------------------------------------------------------------------------------
    // EVERYTHING BELOW IS NOW DEPRECATED.

    /**
     * Saves a file to the device (Only supports POST requests)
     *
     * @param filename Name of the file to save
     * @param session  HttpRequest to get the POST body of
     * @param option   Can be null, "soft", or "new"
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @return
     */

    //    private String saveFile(String filename, NanoHTTPD.IHTTPSession session, String option, String extension) {
//        if (session.getMethod() != NanoHTTPD.Method.POST) {
//            Log.d(TAG, "Save must be done via POST request");
//            return null;
//        }
//        Map<String, String> postFiles = new HashMap<>();
//        if (option == null) {
//            // forcibly attempt to write, and overwrite file with same name, if it exists
//            // if name is not sanitized, throw 409 error
//            if(!isNameSanitized(filename)) {
//                // raise 409
//                return null;
//            }
//        } else if (option.equals("soft")) {
//            // try to save, and respond with 409 if name unavailable ot name is not sanitized
//            if ((!isNameSanitized(filename)) || !isNameAvailable(getBirdblocksDir(), filename, extension)) {
//                // raise 409
//                Log.d("AutoSaveSave", "One of them fails");
//                return null;
//            }
//        } else if (option.equals("new")) {
//            // try to save, and automatically find an available name
//            filename = findAvailableName(getBirdblocksDir(), filename, extension);
//            if (filename == null) {
//                // raise 409
//                return null;
//            }
//        }
//        // actually save file here
//        File newFile = new File(getBirdblocksDir(), filename + extension);
//        try {
//            // Parse POST body to get parameters
//            session.parseBody(postFiles);
//            // Write POST["data"] to file
//            FileWriter writer = new FileWriter(newFile);
//            writer.write(postFiles.get("postData"));
//            writer.close();
//        } catch (IOException e) {
//            newFile.delete();
//            Log.e(TAG, e.toString());
//            return null;
//        } catch (NanoHTTPD.ResponseException e) {
//            Log.e(TAG, e.toString());
//            return null;
//        }
//        return filename;
//    }

    /**
     * Loads a file from the device
     *
     * @param filename  Name of the file to load
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @return String contents of the file
     */
//    private String loadFile(String filename, String extension) {
//        File file = new File(getBirdblocksDir(), filename + extension);
//        if (!file.exists()) {
//            return FILE_NOT_FOUND_RESPONSE;
//        }
//        StringBuilder response = new StringBuilder();
//        try {
//            BufferedReader reader = new BufferedReader(new FileReader(file));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                response.append(line + "\n");
//            }
//        } catch (FileNotFoundException e) {
//            return FILE_NOT_FOUND_RESPONSE;
//        } catch (IOException e) {
//            Log.d(TAG, "Error reading saved file: " + e.toString());
//        }
//        return response.toString().trim();
//    }

    /**
     * Renames a saved file on the device
     *
     * @param oldFilename Old file name
     * @param newFilename New name of file
     * @param option      Can be null or "soft", depending on which
     *                    this method performs different actions
     * @param extension   File extension (put in empty string if no extension)
     *                    Eg: ".bbx", ".m4a", etc
     * @return Returns null if successful, otherwise returns
     *                    error code ("409" or "503") as string
     */
//    private String renameFile(String oldFilename, String newFilename, String option, String extension) {
//        File dirToPass = getBirdblocksDir();
//        File file = new File(dirToPass, oldFilename + extension);
//        if (!file.exists() || !isNameSanitized(newFilename)) {
//            // 409 if oldFile doesn't exist, or newFilename is corrupt
//            return "409";
//        }
//        if (option == null) {
//            // force rename if newFilename is valid, and overwrite file if it exists
//            // do nothing extra here
//        } else if (option.equals("soft")) {
//            // throw 409 error if new name file already exists
//            // else rename
//            if (!isNameAvailable(dirToPass, newFilename, ".bbx"))
//                return "409";
//        }
//        try {
//            // actually rename file here
//            file.renameTo(new File(dirToPass, newFilename + extension));
//            return null;
//        } catch (Exception e) {
//            Log.e("Rename", e.getMessage());
//            // 503
//            return "503";
//        }
//    }

    /**
     * Deletes a saved file on the device
     *
     * @param filename Name of file to delete
     * @param recording true if recorded file, false otherwise
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     */
//    private void deleteFile(String filename, boolean recording, String extension) {
//        File file;
//        if (recording) {
//            file = new File(FileManagementHandler.SecretFileDirectory.getAbsolutePath() + "/Recordings/" + filename + extension);
//            Log.d("Cool", file.getAbsolutePath());
//        } else {
//            file = new File(getBirdblocksDir(), filename + extension);
//        }
//        if (!file.exists()) {
//            return;
//        }
//        file.delete();
//    }

    /**
     * Starts a share command for a saved file on the device
     *
     * @param filename Name of the file to share
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @param session  HttpRequest containing the most up to date contents of the file
     */
//    private String exportFile(String filename, String extension, NanoHTTPD.IHTTPSession session) {
//        /* SAVING HERE NO LONGER REQUIRED */
//        // Save the updated contents (in case they were updated)
//        // saveFile(filename, session);
//        try {
//            // Create share intent on the main activity
//            File file = new File(getBirdblocksDir(), filename + extension);
//            if (file.exists()) {
//                Intent showDialog = new Intent(MainWebView.SHARE_FILE);
//                //showDialog.putExtra("file_uri", Uri.fromFile(file));
//                showDialog.putExtra("file_path", file.getAbsolutePath());
//                LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
//            }
//            return filename;
//        } catch (Exception e) {
//            Log.e("Export", e.getMessage());
//            return null;
//        }
//    }

    /**
     * Lists the files on the device
     *
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: ".bbx", ".m4a", etc
     * @return List of files on the device separated by \n
     */
//    private String listFiles(String extension) {
//        File[] files = getBirdblocksDir().listFiles();
//        String response = "";
//        if (files == null) {
//            return response;
//        }
//        for (int i = 0; i < files.length; i++) {
//            String filename = files[i].getName();
//            if (MainWebView.last4(filename).equals(extension))
//                response += filename.substring(0, filename.length() - 4);
//            if (i < files.length - 1) response += "\n";
//        }
//        return response;
//    }

}
