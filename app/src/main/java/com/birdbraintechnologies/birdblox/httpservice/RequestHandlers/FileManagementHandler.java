package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.birdbraintechnologies.birdblox.MainWebView;
import com.birdbraintechnologies.birdblox.Util.ZipUtility;
//import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.Status;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxSignInInfo;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxSignedIn;

/**
 * Request handler for managing files on the device.
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 * @author Zhendong Yuan (yzd1998111)
 */
public class FileManagementHandler implements RequestHandler {
    private static final String TAG = FileManagementHandler.class.getSimpleName();
    private static final String BIRDBLOCKS_SAVE_DIR = "Saved";

    private static final String FILES_PREFS_KEY = "com.birdbraintechnologies.birdblox.FILE_MANAGEMENT";
    public static SharedPreferences filesPrefs = mainWebViewContext.getSharedPreferences(FILES_PREFS_KEY, Context.MODE_PRIVATE);

    public static final String CURRENT_PREFS_KEY = "com.birdbraintechnologies.birdblox.CURRENT_PROJECT";
    public static final String LAST_PROJECT_KEY = "com.birdbraintechnologies.birdblox.LAST_PROJECT";

    private Context context;

    public FileManagementHandler(Context context) {
        this.context = context;
        filesPrefs = context.getSharedPreferences(FILES_PREFS_KEY, Context.MODE_PRIVATE);
    }

    @Override
    public NativeAndroidResponse handleRequest(NativeAndroidSession session, List<String> args) {
        Log.d(TAG, "handleRequest " + args.toString());
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
                return newProjectWithName(m.get("filename").get(0), session);
            case "getAvailableName":
                if (m.get("type").get(0).equals("file"))
                    return getProjectName(m.get("filename").get(0));
                else if (m.get("type").get(0).equals("recording"))
                    return getRecordingName(m.get("filename").get(0));
            case "duplicate":
                return duplicateProject(m.get("filename").get(0), m.get("newFilename").get(0));
            case "markAsNamed":
                return markNamed();
        }
        return new NativeAndroidResponse(Status.BAD_REQUEST, "Bad Request");
    }


    /**
     * Opens the 'program.xml' file of the given project, if it exists. Project must be unzipped and
     * in the birdblox directory before this method is called.
     *
     * @param name The name of the project to be opened.
     * @return A 'OK' response if opening was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse openProject(String name) {
        Log.d(TAG, "openProject " + name);
        File program = null;
        if (!isNameSanitized(name)) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, name + " is not a valid project name!");
        }
        program = new File(getBirdbloxDir(), name + "/program.xml");

        if (program == null || !program.exists()) {
            Log.e(TAG, "project not found " + program.getPath());
            return new NativeAndroidResponse(Status.NOT_FOUND, "Project " + name + " was not found!");
        }
        try {
            Log.d(TAG, "opening project " + program.getAbsolutePath());
            String encodedXML = bbxEncode(FileUtils.readFileToString(program, "utf-8"));
            String encodedName = bbxEncode(name);
            Log.d(TAG, "project named '" + encodedName + "' with contents '" + encodedXML + "'");
            if (encodedXML != null) {
                runJavascript("CallbackManager.data.open('" + encodedName + "', \"" + encodedXML + "\");");
                filesPrefs.edit().putString(CURRENT_PREFS_KEY, name).apply();
                runJavascript("SaveManager.autoSave();");
                return new NativeAndroidResponse(Status.OK, name + " successfully opened.");
            }
        } catch (SecurityException | IOException e) {
            Log.e(TAG, "Open: " + e.getMessage());
        }

        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while opening " + name);
    }


    /**
     * Renames a given project to a new (given) name.
     *
     * @param oldName The old project name
     * @param newName The new project name
     * @return A 'OK' response if opening was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse renameProject(String oldName, String newName) {
        // Not a recording
        if (!projectExists(oldName) || !isNameSanitized(newName)) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "Given name(s) invalid, or project " + oldName + " doesn't exist.");
        } else if (!oldName.equals(newName) && projectExists(newName)) {
            return new NativeAndroidResponse(Status.CONFLICT, "Project called " + newName + " already exists");
        } else if (oldName.equals(filesPrefs.getString(CURRENT_PREFS_KEY, ""))) {
            runJavascript("CallbackManager.data.setName('" + bbxEncode(newName) + "');");
            filesPrefs.edit().putString(CURRENT_PREFS_KEY, newName).apply();
        }
        try {
            File file = new File(getBirdbloxDir(), oldName);
            if (file.renameTo(new File(getBirdbloxDir(), newName))) {
                return new NativeAndroidResponse(Status.OK, "Project " + oldName + " renamed to " + newName + " successfully");
            }
        } catch (SecurityException | NullPointerException e) {
            Log.e("TAG", "Error while renaming " + oldName + " to " + newName + ": " + e.getMessage());
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while renaming " + oldName + " to " + newName + ".");
    }

    /**
     * Rename a given recording associated with the currently opened project.
     *
     * @param oldName The old name of the recording
     * @param newName The new name of the recording
     * @return A 'OK' response if renaming was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse renameRecording(String oldName, String newName) {
        if (!isNameSanitized(newName)) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "Given newName is invalid.");
        }
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            File dir = new File(getBirdbloxDir(), currProj + "/recordings/");
            if (dir.exists()) {
                File oldFile = new File(dir, oldName + ".m4a");
                File newFile = new File(dir, newName + ".m4a");
                if (oldFile.exists()) {
                    if (!newFile.exists()) {
                        if (oldFile.renameTo(newFile)) {
                            return new NativeAndroidResponse(Status.OK, "Recording " + oldName + " renamed to " + newName + " successfully");
                        } else {
                            return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while renaming " + oldName + " to " + newName + ".");
                        }
                    } else {
                        return new NativeAndroidResponse(Status.CONFLICT, "Recording called " + newName + " already exists");
                    }
                }
            }
        }
        Log.e(TAG, "recording not found " + oldName);
        return new NativeAndroidResponse(Status.NOT_FOUND, "File " + oldName + " was not found.");
    }

    /**
     * Closes the currently opened project, if any.
     *
     * @return A 'OK' response.
     */
    private NativeAndroidResponse closeProject() {
        String filename = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        filesPrefs.edit().putString(LAST_PROJECT_KEY, filename).apply();
        filesPrefs.edit().putString(CURRENT_PREFS_KEY, null).apply();
        return new NativeAndroidResponse(Status.OK, "Current project closed successfully");
    }


    /**
     * Deletes a given project, if it exists.
     *
     * @param name The name of the project to be deleted.
     * @return A 'OK' response if deletion was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse deleteProject(String name) {
        if (!isNameSanitized(name)) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "Given name invalid.");
        } else if (!projectExists(name)) {
            Log.e(TAG, "could not delete project " + name + " (not found)");
            return new NativeAndroidResponse(Status.NOT_FOUND, "Project " + name + " was not found!");
        } else if (filesPrefs.getString(CURRENT_PREFS_KEY, "").equals(name)) {
            filesPrefs.edit().putString(CURRENT_PREFS_KEY, null).apply();
            runJavascript("CallbackManager.data.close();");
        }
        try {
            deleteRecursive(new File(getBirdbloxDir(), name));
            return new NativeAndroidResponse(Status.OK, name + " successfully deleted.");
        } catch (SecurityException e) {
            Log.e(TAG, "Error while deleting " + name + ": " + e.getMessage());
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while deleting " + name);
    }

    /**
     * Deletes a given recording associated with the currently opened project, if it exists.
     *
     * @param name The name of the recording.
     * @return A 'OK' response if deletion was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse deleteRecording(String name) {
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            File rec = new File(getBirdbloxDir(), currProj + "/recordings/" + name + ".m4a");
            if (rec.exists()) {
                if (rec.delete()) {
                    return new NativeAndroidResponse(Status.OK, name + " successfully deleted.");
                } else {
                    return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while deleting " + name);
                }
            }
        }
        Log.e(TAG, "could not delete recording " + name + " (not found)");
        return new NativeAndroidResponse(Status.NOT_FOUND, "Recording " + name + " was not found!");
    }

    /**
     * Lists all current projects.
     *
     * @return A 'OK' response if listing was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse listProjects() {
        try {
            File[] files = getBirdbloxDir().listFiles();
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
                    sendObj.put("account", dropboxSignInInfo);
                }
                return new NativeAndroidResponse(Status.OK, sendObj.toString());
            }
        } catch (JSONException | SecurityException | NullPointerException e) {
            Log.e(TAG, "List Projects: " + e.getMessage());
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while listing projects.");
    }


    /**
     * Exports the given project. Response to the data/export call from frontend.
     *
     * @param name The name of the project to be exported.
     * @return A 'OK' response if exporting was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse exportProject(String name) {
        Log.d(TAG, "exportProject");
        if (!projectExists(name)) {
            return new NativeAndroidResponse(Status.NOT_FOUND, "Project " + name + " doesn't exist.");
        }
        /*try {
            File dir = new File(getBirdbloxDir(), name);*/
            String zipName = name + ".bbx";
            Intent showDialog = new Intent(MainWebView.SHARE_FILE);
            showDialog.putExtra("file_name", zipName);
            showDialog.putExtra("base_name", name);
            LocalBroadcastManager.getInstance(context).sendBroadcast(showDialog);
            return new NativeAndroidResponse(Status.OK, "Successfully exported project " + name);



            /*File zip = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), zipName);
            ZipUtility.zipDirectory(dir, zip);
            if (zip.exists()) {
                Intent showDialog = new Intent(MainWebView.SHARE_FILE);
                showDialog.putExtra("file_name", zipName);
                    //LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
                LocalBroadcastManager.getInstance(context).sendBroadcast(showDialog);
                //return NanoHTTPD.newFixedLengthResponse(
                //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully exported project " + name);
                return new NativeAndroidResponse(Status.OK, "Successfully exported project " + name);
            }
            //TODO: remove zip file once transfer is complete? Consider using a different directory for the temp file.
        } catch (SecurityException | IOException e) {
            Log.e(TAG, "Export: " + e.getMessage());
        }*/
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while exporting project " + name);
        //return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while exporting project " + name);
    }


    /**
     * Saves the currently opened project (if any).
     *
     * @param session HttpRequest to get the POST body of.
     * @return A 'OK' response if autosaving was successful,
     * and an 'ERROR' response otherwise.
     */
    private static NativeAndroidResponse autosaveProject(NativeAndroidSession session) {
        String projectContent = session.getBody();
        if (projectContent.equals("")) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "Project is empty.");
        }
        String name = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (name != null) {
            // actually save project here
            File newFile = new File(getBirdbloxDir(), name + "/program.xml");
            try {
                if (!newFile.exists()) {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                }
                FileUtils.writeStringToFile(newFile, projectContent, "utf-8", false);
                return new NativeAndroidResponse(Status.OK, "Successfully saved project: " + name);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while autosaving project " + name);
    }

    /**
     * Creates a new project, with the name provided.
     *
     * @param name    The name of the new project to be created.
     * @param session HttpRequest to get the POST body of.
     * @return A 'OK' response if creating new project was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse newProjectWithName(String name, NativeAndroidSession session) {
        String projectContent = session.getBody();
        if (projectContent.equals("")) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "Project is empty.");
        }
        if (!isNameSanitized(name)) {
            return new NativeAndroidResponse(Status.BAD_REQUEST, "Given name is invalid.");
        }
        if (projectExists(name)) {
            return new NativeAndroidResponse(Status.CONFLICT, "Project " + name + " already exists.");
        }
        if (name != null) {
            // actually create project here
            File newFile = new File(getBirdbloxDir(), name + "/program.xml");
            try {
                if (!newFile.exists()) {
                    newFile.getParentFile().mkdirs();
                    newFile.createNewFile();
                }
                FileUtils.writeStringToFile(newFile, projectContent, "utf-8", false);
                filesPrefs.edit().putString(CURRENT_PREFS_KEY, name).apply();
                runJavascript("CallbackManager.data.setName('" + bbxEncode(name) + "');");
                return new NativeAndroidResponse(Status.OK, "Successfully created new project: " + name);
            } catch (IOException e) {
                if (newFile.exists())
                    newFile.delete();
                Log.e(TAG, e.getMessage());
            }
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error while making new project " + name);
    }


    /**
     * Gets an available project name, for the given 'name'.
     *
     * @param name The project name for which we need to find an available name.
     * @return A 'OK' response containing an available name for 'name',
     * if successful, and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse getProjectName(String name) {
        try {
            JSONObject nameObject = new JSONObject();
            nameObject.put("availableName", findAvailableName(getBirdbloxDir(), name, ""));
            nameObject.put("alreadySanitized", isNameSanitized(name));
            nameObject.put("alreadyAvailable", isNameAvailable(getBirdbloxDir(), name, ""));
            return new NativeAndroidResponse(Status.OK, nameObject.toString());
        } catch (JSONException | NullPointerException e) {
            Log.e("AvailableName", e.getMessage());
            return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error getting available project name for: " + name);
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
    private NativeAndroidResponse getRecordingName(String name) {
        String currProj = filesPrefs.getString(CURRENT_PREFS_KEY, null);
        if (currProj != null) {
            try {
                File dir = new File(getBirdbloxDir(), currProj + "/recordings");
                if (!dir.exists()) dir.mkdirs();
                JSONObject nameObject = new JSONObject();
                nameObject.put("availableName", findAvailableName(dir, name, ".m4a"));
                nameObject.put("alreadySanitized", isNameSanitized(name));
                nameObject.put("alreadyAvailable", isNameAvailable(dir, name, ".m4a"));
                return new NativeAndroidResponse(Status.OK, nameObject.toString());
            } catch (JSONException | NullPointerException | SecurityException e) {
                Log.e("AvailableName", e.getMessage());
            }
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error getting available project name for: " + name);
    }

    /**
     * Duplicates a given project.
     *
     * @param name    Name of the project to be duplicated.
     * @param newName The name of the duplicated copy.
     * @return A 'OK' response if duplicating was successful,
     * and an 'ERROR' response otherwise.
     */
    private NativeAndroidResponse duplicateProject(String name, String newName) {
        if (!projectExists(name)) {
            return new NativeAndroidResponse(Status.NOT_FOUND, "Project " + name + " doesn't exist.");
        } else if (!isNameSanitized(newName)) {
            return new NativeAndroidResponse(Status.FORBIDDEN, newName + " is not a valid project name.");
        } else if (!isNameAvailable(getBirdbloxDir(), newName, "")) {
            return new NativeAndroidResponse(Status.CONFLICT, "Project " + newName + " already exists.");
        }
        try {
            File srcDir = new File(getBirdbloxDir(), name);
            File destDir = new File(getBirdbloxDir(), newName);
            if (destDir.mkdirs()) {
                FileUtils.copyDirectory(srcDir, destDir);
                return new NativeAndroidResponse(Status.OK, "Successfully duplicated project " + name + " to " + newName);
            }
        } catch (SecurityException | IOException e) {
            Log.e(TAG, "Duplicate: " + e.getMessage());
        }
        return new NativeAndroidResponse(Status.INTERNAL_ERROR, "Error duplicating project " + name + " to " + newName);
    }

    /**
     * Sets the 'isNamed' value stored in SharedPreferences to true.
     *
     * @return A 'OK' response
     */
    private NativeAndroidResponse markNamed() {
        return new NativeAndroidResponse(Status.OK, "Successfully marked named");
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
    static boolean isNameSanitized(String name) {
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
    public static String sanitizeName(String name) {
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
            int n = 1;
            if (name.length() > 3 && name.endsWith(")")) {
                int startIndex = name.length() - 2;
                while (startIndex >= 0) {
                    if (name.charAt(startIndex) == '(') break;
                    startIndex--;
                }
                if (startIndex < name.length() - 2 && name.charAt(startIndex - 1) == ' ') {
                    String number = name.substring(startIndex + 1, name.length() - 1);
                    // if the String 'number' actually contains a number 1 onwards
                    if (number.matches("^[1-9]\\d*$")) {
                        n = Integer.parseInt(number);
                        // remove the " (number)" part from the end of name
                        name = name.substring(0, name.length() - (number.length() + 3));
                    }
                }
            }
            for (int i = n; i <= files.length + n; i++) {
                String newName = name + " (" + i + ")";
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
    static boolean projectExists(String name) {
        return isNameSanitized(name) && new File(getBirdbloxDir(), name).exists();
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
    public static File getBirdbloxDir() {
        File file = new File(mainWebViewContext.getFilesDir(), BIRDBLOCKS_SAVE_DIR);
        if (!file.exists()) {
            try {
                file.mkdirs();
                Log.d(TAG, "Created BirdBlox save directory: " + file.getPath());
            } catch (SecurityException e) {
                Log.e("Save Directory", "" + e);
            }
        }
        Log.d(TAG, "returning save directory: " + file.getPath());
        return file;
    }

}
