package com.birdbraintechnologies.birdblox.Dropbox;

import android.os.AsyncTask;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.WriteMode;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxSearch;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.showDropboxDialog;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

class DropboxUploadTask extends AsyncTask<String, Long, String> {
    private final String TAG = this.getClass().getName();

    private DbxClientV2 dropboxClient;
    private WriteMode uploadMode;

    DropboxUploadTask(DbxClientV2 dropboxClient, WriteMode uploadMode) {
        super();
        this.dropboxClient = dropboxClient;
        this.uploadMode = uploadMode;
    }

    @Override
    protected String doInBackground(String... files) {
        try {
            File file = new File(files[0]);
            String name = FilenameUtils.getBaseName(file.getAbsolutePath());
            String availableName = dropboxSearch(name, ".bbx");
            if ((!availableName.equals(name)) && uploadMode == WriteMode.ADD) {
                showDropboxDialog(name, availableName, DropboxOperation.UPLOAD);
            } else {
                try (InputStream in = new FileInputStream(file)) {
                    // TODO: Modified?? YES -> get actual last modified date as param
                    // .withClientModified(new Date(file.lastModified()*1000))
                    // TODO: Monitor progress?? Not for now
                    String useName = (uploadMode == WriteMode.OVERWRITE) ? name : availableName;
                    FileMetadata metadata = dropboxClient.files().uploadBuilder("/" + useName + ".bbx").withMute(true).withMode(uploadMode).uploadAndFinish(in);
                    Log.d(TAG, "MetadataUpload: " + metadata);
                    JSONObject newFiles = dropboxAppFolderContents();
                    if (newFiles != null)
                        runJavascript("CallbackManager.cloud.filesChanged('" + bbxEncode(newFiles.toString()) + "')");
                    return name;
                }
            }
        } catch (IOException | DbxException | SecurityException | IllegalArgumentException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Unable to upload file: " + e.getMessage());
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onCancelled(String s) {
        super.onCancelled(s);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }
}
