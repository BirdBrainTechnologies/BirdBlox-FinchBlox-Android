package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.MainWebView;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblocks.util.ZipUtility;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblocks.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblocks.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.DropboxRequestHandler.dropboxSignedIn;
import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.DropboxRequestHandler.getDropboxSignIn;
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

    private static final String FILES_PREFS_KEY = "com.birdbraintechnologies.birdblocks.FILE_MANAGEMENT";
    static SharedPreferences filesPrefs = mainWebViewContext.getSharedPreferences(FILES_PREFS_KEY, Context.MODE_PRIVATE);

    static final String CURRENT_PREFS_KEY = "com.birdbraintechnologies.birdblocks.CURRENT_PROJECT";
    static final String NAMED_PREFS_KEY = "com.birdbraintechnologies.birdblocks.IS_FILE_NAMED";

    private HttpService service;

    public FileManagementHandler(HttpService service) {
        this.service = service;
        filesPrefs = service.getSharedPreferences(FILES_PREFS_KEY, Context.MODE_PRIVATE);
        filesPrefs.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "open":
                return openProject(m.get("filename").get(0));
            case "rename":
                if (m.get("type").get(0).equals("file"))
                    return renameProject(m.get("oldFilename").get(0), m.get("newFilename").get(0));
                else if (m.get("type").get(0).equals("recording"))
                    return renameRecording(m.get("oldFilename").get(0), m.get("newFilename").get(0));
            case "close":
                return closeProject();
            case "delete":
                if (m.get("type").get(0).equals("file"))
                    return deleteProject(m.get("filename").get(0));
                else if (m.get("type").get(0).equals("recording"))
                    return deleteRecording(m.get("filename").get(0));
            case "files":
                return listProjects();
            case "export":
                return exportProject(m.get("filename").get(0));
            case "autoSave":
                return autosaveProject(session);
            case "new":
                return newProject(session);
            case "getAvailableName":
                if (m.get("type").get(0).equals("file"))
                    return getProjectName(m.get("filename").get(0));
                else if (m.get("type").get(0).equals("recording"))
                    return getRecordingName(m.get("filename").get(0));
            case "duplicate":
                return duplicateProject(m.get("filename").get(0), m.get("newFilename").get(0));
            case "markAsNamed":

        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request");
    }


    /**
     * Opens the 'program.xml' file of the given project, if it exists.
     *
     * @param name The name of the project to be opened.
     * @return A 'OK' response if opening was successful,
     * and an 'ERROR' response otherwise.
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
        try {
            String encodedXML = bbxEncode(FileUtils.readFileToString(program, "utf-8"));
            String encodedName = bbxEncode(name);
            boolean isNamed = filesPrefs.getBoolean(NAMED_PREFS_KEY, false);
            if (encodedXML != null) {
                runJavascript("CallbackManager.data.open('" + encodedName + "', \"" + encodedXML + "\", " + isNamed + ");");
                filesPrefs.edit().putString(CURRENT_PREFS_KEY, name).apply();
                filesPrefs.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, name + " successfully opened.");
            }
        } catch (SecurityException | IOException e) {
            Log.e(TAG, "Open: " + e.getMessage());
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
        // Not a recording
        if (!projectExists(oldName) || !isNameSanitized(newName)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Given name(s) invalid, or project " + oldName + " doesn't exist.");
        } else if (!oldName.equals(newName) && projectExists(newName)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "Project called " + newName + " already exists");
        } else if (oldName.equals(filesPrefs.getString(CURRENT_PREFS_KEY, ""))) {
            boolean isNamed = filesPrefs.getBoolean(NAMED_PREFS_KEY, false);
            runJavascript("CallbackManager.data.setName('" + bbxEncode(newName) + "', " + isNamed + ");");
            filesPrefs.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
            filesPrefs.edit().putString(CURRENT_PREFS_KEY, newName).apply();
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
     * Rename a given recording associated with the currently opened project.
     *
     * @param oldName The old name of the recording
     * @param newName The new name of the recording
     * @return A 'OK' response if renaming was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response renameRecording(String oldName, String newName) {
        if (!isNameSanitized(newName)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Given newName is invalid.");
        }
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            File dir = new File(getBirdblocksDir(), currProj + "/recordings/");
            if (dir.exists()) {
                File oldFile = new File(dir, oldName + ".m4a");
                File newFile = new File(dir, newName + ".m4a");
                if (oldFile.exists()) {
                    if (!newFile.exists()) {
                        if (oldFile.renameTo(newFile)) {
                            return NanoHTTPD.newFixedLengthResponse(
                                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Recording " + oldName + " renamed to " + newName + " successfully");
                        } else {
                            return NanoHTTPD.newFixedLengthResponse(
                                    NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while renaming " + oldName + " to " + newName + ".");
                        }
                    } else {
                        return NanoHTTPD.newFixedLengthResponse(
                                NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "Recording called " + newName + " already exists");
                    }
                }
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File " + oldName + " was not found.");
    }

    /**
     * Closes the currently opened project, if any.
     *
     * @return A 'OK' response.
     */
    private NanoHTTPD.Response closeProject() {
        filesPrefs.edit().putString(CURRENT_PREFS_KEY, null).apply();
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
        } else if (filesPrefs.getString(CURRENT_PREFS_KEY, "").equals(name)) {
            runJavascript("CallbackManager.data.close();");
        }
        try {
            deleteRecursive(new File(getBirdblocksDir(), name));
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, name + " successfully deleted.");
        } catch (SecurityException e) {
            Log.e(TAG, "Error while deleting " + name + ": " + e.getMessage());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while deleting " + name);
    }

    /**
     * Deletes a given recording associated with the currently opened project, if it exists.
     *
     * @param name The name of the recording.
     * @return A 'OK' response if deletion was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response deleteRecording(String name) {
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            File rec = new File(getBirdblocksDir(), currProj + "/recordings/" + name + ".m4a");
            if (rec.exists()) {
                if (rec.delete()) {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, name + " successfully deleted.");
                } else {
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while deleting " + name);
                }
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Recording " + name + " was not found!");
    }

    /**
     * Lists all current projects.
     *
     * @return A 'OK' response if listing was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response listProjects() {
        try {
            File[] files = getBirdblocksDir().listFiles();
            JSONArray fileList = new JSONArray();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        fileList.put(file.getName());
                    }
                }
                JSONObject sendObj = new JSONObject();
                sendObj.put("files", fileList);
                if (dropboxSignedIn()) {
                    sendObj.put("signedIn", true);
                    sendObj.put("account", getDropboxSignIn());
                }
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, sendObj.toString());
            }
        } catch (JSONException | SecurityException | NullPointerException e) {
            Log.e(TAG, "List Projects: " + e.getMessage());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while listing projects.");
    }


    /**
     * Exports the given project.
     *
     * @param name The name of the project to be exported.
     * @return A 'OK' response if exporting was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response exportProject(String name) {
        if (!projectExists(name)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Project " + name + " doesn't exist.");
        }
        try {
            File dir = new File(getBirdblocksDir(), name);
            File zip = new File(getBirdblocksDir(), name + ".bbx");
            ZipUtility.zipDirectory(dir, zip);
            if (zip.exists()) {
                Intent showDialog = new Intent(MainWebView.SHARE_FILE);
                showDialog.putExtra("file_path", zip.getAbsolutePath());
                LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully exported project " + name);
            }
        } catch (SecurityException | IOException e) {
            Log.e(TAG, "Export: " + e.getMessage());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while exporting project " + name);
    }


    /**
     * Saves the currently opened project (if any).
     *
     * @param session HttpRequest to get the POST body of.
     * @return A 'OK' response if autosaving was successful,
     * and an 'ERROR' response otherwise.
     */
    public static NanoHTTPD.Response autosaveProject(NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            Log.d(TAG, "Autosave: Save must be done via POST request");
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Please send a POST request.");
        }
        Map<String, String> postFiles = new HashMap<>();
        String name = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (name != null) {
            // actually save project here
            File newFile = new File(getBirdblocksDir(), name + "/program.xml");
            try {
                if (!newFile.exists()) {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                }
                // Parse POST body to get parameters
                session.parseBody(postFiles);
                // Write POST["data"] to file
                FileUtils.writeStringToFile(newFile, postFiles.get("postData"), "utf-8");
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully saved project: " + name);
            } catch (IOException | NanoHTTPD.ResponseException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while autosaving project " + name);
    }

    /**
     * Creates a new project, with the default temporary name 'new program'.
     *
     * @param session HttpRequest to get the POST body of.
     * @return A 'OK' response if creating new project was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response newProject(NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            Log.d(TAG, "New: Save must be done via POST request");
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Please send a POST request.");
        }
        Map<String, String> postFiles = new HashMap<>();
        String name = findAvailableName(getBirdblocksDir(), "new program", "");
        if (name != null) {
            // actually create project here
            File newFile = new File(getBirdblocksDir(), name + "/program.xml");
            try {
                if (!newFile.exists()) {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                }
                // Parse POST body to get parameters
                session.parseBody(postFiles);
                FileUtils.writeStringToFile(newFile, postFiles.get("postData"), "utf-8");
                filesPrefs.edit().putString(CURRENT_PREFS_KEY, name).apply();
                // boolean isNamed = filesPrefs.getBoolean(NAMED_PREFS_KEY, false);
                filesPrefs.edit().putBoolean(NAMED_PREFS_KEY, false).apply();
                runJavascript("CallbackManager.data.setName('" + bbxEncode(name) + "', false);");
                filesPrefs.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully created new project: " + name);
            } catch (IOException e) {
                if (newFile.exists())
                    newFile.delete();
                Log.e(TAG, e.getMessage());
            } catch (NanoHTTPD.ResponseException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while making new project");
    }

    /**
     * Gets an available project name, for the given 'name'.
     *
     * @param name The project name for which we need to find an available name.
     * @return A 'OK' response containing an available name for 'name',
     * if successful, and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response getProjectName(String name) {
        try {
            JSONObject nameObject = new JSONObject();
            nameObject.put("availableName", findAvailableName(getBirdblocksDir(), name, ""));
            nameObject.put("alreadySanitized", isNameSanitized(name));
            nameObject.put("alreadyAvailable", isNameAvailable(getBirdblocksDir(), name, ""));
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, nameObject.toString());
        } catch (JSONException | NullPointerException e) {
            Log.e("AvailableName", e.getMessage());
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error getting available project name for: " + name);
        }
    }

    /**
     * Gets an available recording name (associated with the currently opened project),
     * for the given 'name'.
     *
     * @param name The recording name for which we need to find an available name.
     * @return A 'OK' response containing an available name for 'name',
     * if successful, and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response getRecordingName(String name) {
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            try {
                File dir = new File(getBirdblocksDir(), currProj + "/recordings");
                if (!dir.exists()) dir.mkdirs();
                JSONObject nameObject = new JSONObject();
                nameObject.put("availableName", findAvailableName(dir, name, ".m4a"));
                nameObject.put("alreadySanitized", isNameSanitized(name));
                nameObject.put("alreadyAvailable", isNameAvailable(dir, name, ".m4a"));
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, nameObject.toString());
            } catch (JSONException | NullPointerException | SecurityException e) {
                Log.e("AvailableName", e.getMessage());
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error getting available project name for: " + name);
    }

    /**
     * Duplicates a given project.
     *
     * @param name    Name of the project to be duplicated.
     * @param newName The name of the duplicated copy.
     * @return A 'OK' response if duplicating was successful,
     * and an 'ERROR' response otherwise.
     */
    private NanoHTTPD.Response duplicateProject(String name, String newName) {
        if (!projectExists(name)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Project " + name + " doesn't exist.");
        } else if (!isNameSanitized(newName)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.FORBIDDEN, MIME_PLAINTEXT, newName + " is not a valid project name.");
        } else if (!isNameAvailable(getBirdblocksDir(), newName, "")) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.CONFLICT, MIME_PLAINTEXT, "Project " + newName + " already exists.");
        }
        try {
            File srcDir = new File(getBirdblocksDir(), name);
            File destDir = new File(getBirdblocksDir(), newName);
            if (destDir.mkdirs()) {
                FileUtils.copyDirectory(srcDir, destDir);
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully duplicated project " + name + " to " + newName);
            }
        } catch (SecurityException | IOException e) {
            Log.e(TAG, "Duplicate: " + e.getMessage());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error duplicating project " + name + " to " + newName);
    }

    /**
     * Sets the 'isNamed' value stored in SharedPreferences to true.
     *
     * @return A 'OK' response
     */
    private NanoHTTPD.Response markNamed() {
        filesPrefs.edit().putBoolean(NAMED_PREFS_KEY, true).apply();
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully marked named");
    }


    // -----------------------------------------------------------------------------

    // HELPER FUNCTIONS.


    /**
     * Checks if input filename contains any illegal characters.
     *
     * @param name Input filename
     * @return Returns false if 'name' contains any illegal characters,
     * and true otherwise.
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
        return (name == null) || !new File(dir, name + extension).exists();
    }

    /**
     * @param dir       Directory in which the file is located
     * @param name      Input filename
     * @param extension File extension (put in empty string if no extension)
     *                  Eg: "", ".bbx", ".m4a", etc
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
     * Checks if the input name corresponds to an existing project.
     *
     * @param name The input project name
     * @return True if the given project exists on disk, false otherwise.
     */
    private boolean projectExists(String name) {
        return isNameSanitized(name) && new File(getBirdblocksDir(), name).exists();
    }


    /**
     * Recursive function to delete a file / directory and all of its contents.
     * <p>
     * SOURCE: https://stackoverflow.com/questions/4943629/how-to-delete-a-whole-folder-and-content
     *
     * @param fileOrDirectory The file or directory to be deleted.
     */
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);
        fileOrDirectory.delete();
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
