package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import com.birdbraintechnologies.birdblox.Dropbox.DropboxDownloadAndUnzipTask;
import com.birdbraintechnologies.birdblox.Dropbox.DropboxOperation;
import com.birdbraintechnologies.birdblox.Dropbox.DropboxZipAndUploadTask;
import com.birdbraintechnologies.birdblox.R;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.SearchMatch;
import com.dropbox.core.v2.files.SearchResult;
import com.dropbox.core.v2.files.WriteMode;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static android.content.Context.MODE_PRIVATE;
import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.findAvailableName;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.isNameSanitized;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.projectExists;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.sanitizeName;
import static com.dropbox.core.android.AuthActivity.EXTRA_ACCESS_SECRET;
import static com.dropbox.core.android.AuthActivity.EXTRA_ACCESS_TOKEN;
import static com.dropbox.core.android.AuthActivity.result;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxRequestHandler implements RequestHandler {

    // TODO: Progress Dialogs
    // TODO: Compare errors with frontend
    // TODO: Separate UNZIP Directory, and invalid file handling

    private final String TAG = this.getClass().getName();

    public static final String DBX_DOWN_DIR = "DbxDownload";
    public static final String DBX_ZIP_DIR = "DbxZip";

    public static final String DB_PREFS_KEY = "com.birdbraintechnologies.birdblox.DROPBOX_ACCESS_TOKEN";

    HttpService service;

    public static DbxRequestConfig dropboxConfig;
    public static DbxClientV2 dropboxClient;

    public static String dropboxSignInInfo;

    SharedPreferences dropboxPrefs;

    public DropboxRequestHandler(HttpService service) {
        this.service = service;

        // If access token already present, initialize.
        dropboxPrefs = mainWebViewContext.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE);
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken != null) {
            createDropboxClient(accessToken);
        }
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "signIn":
                return dropboxSignIn();
            case "signOut":
                return dropboxSignOut();
            case "list":
                return listDropboxFolder();
            case "download":
                return startDropboxDownload(m.get("filename").get(0));
            case "upload":
                return startDropboxUpload(m.get("filename").get(0));
            case "rename":
                return startDropboxRename(m.get("filename").get(0));
            case "delete":
                return startDropboxDelete(m.get("filename").get(0));
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request");
    }

    private NanoHTTPD.Response dropboxSignIn() {
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken == null) {
            obtainDropboxAccessToken();
        } else {
            createDropboxClient(accessToken);
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Sign In Process started");
    }

    private NanoHTTPD.Response dropboxSignOut() {
        try {
            dropboxSignInInfo = null;
            if (dropboxClient != null) {
                dropboxClient = null;
            }
            if (dropboxConfig != null) {
                dropboxConfig = null;
            }
            if (dropboxPrefs.getString("access-token", null) != null) {
                dropboxPrefs.edit().putString("access-token", null).apply();
            }
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Signed out successfully");
        } catch (Exception e) {
            Log.e(TAG, "Dropbox sign out: " + e.getMessage());
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error while signing out: " + e.getMessage());
        }
    }

    private NanoHTTPD.Response listDropboxFolder() {
        JSONObject obj = dropboxAppFolderContents();
        if (obj != null) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, obj.toString());
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Error while listing contents of Dropbox folder.");
    }

    private NanoHTTPD.Response startDropboxDownload(String name) {
        if (!dropboxSignedIn()) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Not signed in to Dropbox");
        }
        name = sanitizeName(name);
        if (checkValidName(name, DropboxOperation.DOWNLOAD)) {
            startDropboxOperation(name, DropboxOperation.DOWNLOAD);
        } else {
            showDropboxDialog(name, DropboxOperation.DOWNLOAD);
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully started download and unzip of project: " + name);
    }

    private NanoHTTPD.Response startDropboxUpload(String name) {
        if (!dropboxSignedIn()) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Not signed in to Dropbox");
        }
        name = sanitizeName(name);
        if (checkValidName(name, DropboxOperation.UPLOAD)) {
            startDropboxOperation(name, DropboxOperation.UPLOAD);
        } else {
            showDropboxDialog(name, DropboxOperation.UPLOAD);
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully started upload of project: " + name);
    }

    private NanoHTTPD.Response startDropboxRename(final String name) {
        if (!dropboxSignedIn()) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Not signed in to Dropbox");
        }
        showRenameDialog(name, DropboxOperation.RENAME);
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully renamed project: " + name);
    }


    private NanoHTTPD.Response startDropboxDelete(final String name) {
        if (!dropboxSignedIn()) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Not signed in to Dropbox");
        }
        checkInput(name, DropboxOperation.DELETE);
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "Successfully started deletion of: " + name);
    }

    private void obtainDropboxAccessToken() {
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Auth.startOAuth2Authentication(mainWebViewContext, mainWebViewContext.getString(R.string.APP_KEY));
            }
        });
    }

    public static void dropboxAppOAuth() {
        Intent intent = result;
        if (intent == null || !intent.hasExtra(EXTRA_ACCESS_TOKEN)) return;
        initializeDropbox(intent.getStringExtra(EXTRA_ACCESS_SECRET));
    }

    public static void dropboxWebOAuth(Intent intent) {
        if (intent == null) return;
        Uri uri = intent.getData();
        if (uri != null && uri.toString().startsWith("db-" + mainWebViewContext.getString(R.string.APP_KEY))) {
            initializeDropbox(uri.getQueryParameter("oauth_token_secret"));
        }
    }

    private static void initializeDropbox(String secret) {
        if (secret != null && !secret.equals("")) {
            Log.d("DROPBOXINTENT", "OAUTH-SECRET: " + secret);
            mainWebViewContext.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE).edit().putString("access-token", secret).apply();
            createDropboxClient(secret);
        }
    }

    private static void createDropboxClient(String accessToken) {
        if (accessToken != null) {
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, accessToken);
            runJavascript("CallbackManager.cloud.signIn()");
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    getDropboxSignInInfo();
                }
            }.start();
        }
    }

    static boolean dropboxSignedIn() {
        return mainWebViewContext.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE).getString("access-token", null) != null;
    }

    private static String getDropboxSignInInfo() {
        if (dropboxClient != null) {
            try {
                // Get current account info
                dropboxSignInInfo = dropboxClient.users().getCurrentAccount().getEmail();
            } catch (DbxException e) {
                Log.e("DropboxRequestHandler", "Exception getting dropbox account ID: " + e.getMessage());
            }
        }
        return null;
    }

    public static JSONObject dropboxAppFolderContents() {
        if (!dropboxSignedIn()) return null;
        try {
            JSONArray arr = new JSONArray();
            ListFolderResult result = dropboxClient.files().listFolder("");
            while (true) {
                for (Metadata metadata : result.getEntries()) {
                    String name = metadata.getName();
                    if (FilenameUtils.isExtension(name, "bbx"))
                        arr.put(FilenameUtils.getBaseName(name));
                }
                if (!result.getHasMore()) {
                    break;
                }
                result = dropboxClient.files().listFolderContinue(result.getCursor());
            }
            JSONObject obj = new JSONObject();
            obj.put("files", arr);
            return obj;
        } catch (DbxException | JSONException e) {
            Log.e("DropboxRequestHandler", "listFolder: " + e.getMessage());
        }
        return null;
    }

    public static void showDropboxDialog(final String name, final DropboxOperation dbxOperation) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mainWebViewContext);
        builder.setTitle(dbxOperation.toString());
        builder.setMessage(getNameError(name, dbxOperation));
        builder.setCancelable(true);
        builder.setPositiveButton(
                "Rename",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        showRenameDialog(name, dbxOperation);
                    }
                });
        builder.setNegativeButton(
                "Overwrite",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        if (dbxOperation == DropboxOperation.UPLOAD) {
                            startDropboxOperation(name, dbxOperation, WriteMode.OVERWRITE);
                        } else {
                            startDropboxOperation(name, dbxOperation);
                        }
                    }
                });
        builder.setNeutralButton(
                "Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    public static void showDropboxDialog(final String oldName, final String newName, final DropboxOperation dbxOperation) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mainWebViewContext);
        builder.setTitle(dbxOperation.toString());
        builder.setMessage(getNameError(oldName, dbxOperation));
        builder.setCancelable(true);
        builder.setPositiveButton(
                "Rename",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        showRenameDialog(oldName, newName, dbxOperation);
                    }
                });
        builder.setNegativeButton(
                "Overwrite",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        if (dbxOperation == DropboxOperation.UPLOAD) {
                            startDropboxOperation(oldName, newName, dbxOperation, WriteMode.OVERWRITE);
                        } else {
                            startDropboxOperation(oldName, newName, dbxOperation);
                        }
                    }
                });
        builder.setNeutralButton(
                "Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private static String getNameError(final String name, DropboxOperation dbxOperation) {
        switch (dbxOperation) {
            case DOWNLOAD:
                return "\"" + name + "\" already exists on disk. Enter a different name.";
            case UPLOAD:
                return "\"" + name + "\" already exists on Dropbox. Enter a different name.";
            case RENAME:
                return "Please enter a different name for the project \"" + name + "\" : ";
        }
        return "Error";
    }

    private static void showRenameDialog(final String name, final DropboxOperation dbxOperation) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mainWebViewContext);
        builder.setTitle("Rename");
        builder.setMessage(getNameError(name, DropboxOperation.RENAME));
        final EditText input = new EditText(mainWebViewContext);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(findAvailableName(getBirdbloxDir(), name, ""));
        input.setSelectAllOnFocus(true);
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                if (dbxOperation == DropboxOperation.UPLOAD) {
                    checkInput(name, input.getText().toString(), dbxOperation, WriteMode.ADD);
                } else if (dbxOperation != DropboxOperation.DELETE) {
                    checkInput(name, input.getText().toString(), dbxOperation);
                } else {
                    checkInput(input.getText().toString(), dbxOperation);
                }
            }
        });
        if (dbxOperation != DropboxOperation.RENAME) {
            builder.setNegativeButton("Back", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                    showDropboxDialog(name, dbxOperation);
                }
            });
        }
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private static void showRenameDialog(final String oldName, final String newName, final DropboxOperation dbxOperation) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mainWebViewContext);
        builder.setTitle("Rename");
        builder.setMessage(getNameError(oldName, DropboxOperation.RENAME));
        final EditText input = new EditText(mainWebViewContext);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(newName);
        input.setSelectAllOnFocus(true);
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                if (dbxOperation == DropboxOperation.UPLOAD) {
                    checkInput(oldName, newName, dbxOperation, WriteMode.ADD);
                } else if (dbxOperation != DropboxOperation.DELETE) {
                    checkInput(oldName, newName, dbxOperation);
                } else {
                    checkInput(newName, dbxOperation);
                }
            }
        });
        if (dbxOperation != DropboxOperation.RENAME) {
            builder.setNegativeButton("Back", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                    showDropboxDialog(oldName, dbxOperation);
                }
            });
        }
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private static void checkInput(String name, DropboxOperation dbxOperation) {
        boolean validName = checkValidName(name, dbxOperation);
        if (validName) {
            startDropboxOperation(name, dbxOperation);
        } else {
            showDropboxDialog(name, dbxOperation);
        }
    }

    private static void checkInput(String oldName, String newName, DropboxOperation dbxOperation) {
        boolean validName = checkValidName(oldName, newName, dbxOperation);
        if (validName) {
            startDropboxOperation(oldName, newName, dbxOperation);
        } else {
            showRenameDialog(oldName, dbxOperation);
        }
    }

    private static void checkInput(String oldName, String newName, DropboxOperation dbxOperation, WriteMode mode) {
        boolean validName = checkValidName(oldName, newName, dbxOperation, mode);
        if (validName) {
            startDropboxOperation(oldName, newName, dbxOperation, mode);
        } else {
            showRenameDialog(oldName, dbxOperation);
        }
    }

    private static boolean checkValidName(String name, DropboxOperation dbxOperation) {
        if (!isNameSanitized(name)) return false;
        switch (dbxOperation) {
            case DOWNLOAD:
                return !projectExists(name);
            case UPLOAD:
            case DELETE:
                return true;
        }
        return false;
    }

    private static boolean checkValidName(String oldName, String newName, DropboxOperation dropboxOperation) {
        if (!isNameSanitized(newName)) return false;
        switch (dropboxOperation) {
            case DOWNLOAD:
                return !projectExists(newName);
            case UPLOAD:
                return true;
            case RENAME:
                // TODO: RENAME CASE HERE
                return true;
        }
        return false;
    }

    private static boolean checkValidName(String oldName, String newName, DropboxOperation dropboxOperation, WriteMode mode) {
        if (!isNameSanitized(newName)) return false;
        switch (dropboxOperation) {
            case UPLOAD:
                return true;
        }
        return false;
    }

    private static void startDropboxOperation(final String name, DropboxOperation dbxOperation) {
        switch (dbxOperation) {
            case DOWNLOAD:
                dropboxDownload(name, name);
                break;
            case UPLOAD:
                dropboxUpload(name, name, WriteMode.ADD);
                break;
            case DELETE:
                dropboxDelete(name);
                break;
        }
    }

    private static void startDropboxOperation(final String name, DropboxOperation dbxOperation, WriteMode mode) {
        switch (dbxOperation) {
            case UPLOAD:
                dropboxUpload(name, name, mode);
                break;
        }
    }

    private static void startDropboxOperation(final String oldName, final String newName, DropboxOperation dropboxOperation) {
        switch (dropboxOperation) {
            case DOWNLOAD:
                dropboxDownload(oldName, newName);
                break;
            case RENAME:
                dropboxRename(oldName, newName);
                break;
        }
    }

    private static void startDropboxOperation(final String oldName, final String newName, DropboxOperation dropboxOperation, WriteMode mode) {
        switch (dropboxOperation) {
            case UPLOAD:
                dropboxUpload(oldName, newName, mode);
                break;
        }
    }

    private static void dropboxDownload(final String oldName, final String newName) {
        new DropboxDownloadAndUnzipTask(dropboxClient).execute(oldName, newName);
    }

    private static void dropboxUpload(final String oldName, final String newName, WriteMode uploadMode) {
        try {
            File projectDir = new File(getBirdbloxDir(), oldName);
            File toDir = new File(mainWebViewContext.getFilesDir(), DBX_ZIP_DIR);
            if (!toDir.exists()) toDir.mkdirs();
            String name = newName;
            if (uploadMode == WriteMode.OVERWRITE) {
                name = oldName;
            }
            File toFile = new File(toDir, name + ".bbx");
            if (!toFile.exists()) toFile.createNewFile();
            new DropboxZipAndUploadTask(dropboxClient, uploadMode).execute(projectDir, toFile);
        } catch (IOException | SecurityException e) {
            Log.e("DropboxRequestHandler", "ZipAndUpload: " + e.getMessage());
        }
    }

    private static void dropboxRename(final String oldName, final String newName) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Metadata moveFile = dropboxClient.files().move("/" + oldName + ".bbx", "/" + newName + ".bbx");
                    JSONObject newFiles = dropboxAppFolderContents();
                    if (newFiles != null)
                        runJavascript("CallbackManager.cloud.filesChanged('" + bbxEncode(newFiles.toString()) + "')");
                    Log.d("DropboxRequestHandler", "MetadataRename: " + moveFile);
                } catch (DbxException e) {
                    Log.e("DropboxRequestHandler", "Rename: " + e.getMessage());
                }
            }
        }.start();
    }

    private static void dropboxDelete(final String name) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Metadata deleteFile = dropboxClient.files().delete("/" + name + ".bbx");
                    JSONObject newFiles = dropboxAppFolderContents();
                    if (newFiles != null)
                        runJavascript("CallbackManager.cloud.filesChanged('" + bbxEncode(newFiles.toString()) + "')");
                    Log.d("DropboxRequestHandler", "MetadataDelete: " + deleteFile);
                } catch (DbxException e) {
                    Log.e("DropboxRequestHandler", "Delete: " + e.getMessage());
                }
            }
        }.start();
    }

    public static String dropboxSearch(final String name, final String extension) {
        try {
            // TODO: If possible, search via regex ->  name + "( ?\\([1-9][0-9]*\\))?" + extension
            SearchResult searchResult = dropboxClient.files().searchBuilder("", name).withMaxResults(1000L).start();
            Log.d("DropboxRequestHandler", "Search Results: " + searchResult.toStringMultiline());
            if (searchResult.getMatches().size() == 0) {
                return name;
            } else {
                return findAvailableDropboxName(name, extension, searchResult);
            }
        } catch (DbxException e) {
            Log.e("DropboxRequestHandler", "Search: " + e.getMessage());
        }
        return name;
    }

    private static String findAvailableDropboxName(final String name, final String extension, SearchResult result) {
        List<SearchMatch> matchesList = result.getMatches();
        if (!matchSearch(name + extension, matchesList)) {
            return name;
        }
        for (int i = 0; i < matchesList.size(); i++) {
            if (!matchSearch(name + " (" + (i + 1) + ")" + extension, matchesList)) {
                return name + " (" + (i + 1) + ")";
            }
        }
        return name;
    }

    private static boolean matchSearch(final String name, List<SearchMatch> matchesList) {
        for (SearchMatch match : matchesList) {
            if (match.getMetadata().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

}

