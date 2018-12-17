package com.birdbraintechnologies.birdblox.Dropbox;

import android.util.Log;

import com.birdbraintechnologies.birdblox.Project.ZipTask;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.WriteMode;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxSearch;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.showDropboxDialog;

/**
 * Async Task to zip and upload a .bbx file to Dropbox
 * @author Shreyan Bakshi (AppyFizz)
 * @author krissie
 */

public class DropboxZipAndUploadTask extends ZipTask {
    String TAG = this.getClass().getSimpleName();

    private DbxClientV2 dropboxClient;
    private WriteMode uploadMode;

    public DropboxZipAndUploadTask(DbxClientV2 dropboxClient, WriteMode uploadMode) {
        super();
        this.dropboxClient = dropboxClient;
        this.uploadMode = uploadMode;
    }

    /**
     * The work of the task. In this case, the file will be zipped when we call
     * super.doInBackground. Then, the file is uploaded to dropbox.
     * @param files - array of files. Should be 2: directory to zip, and destination.
     * @return - String name of uploaded zip file, or null.
     */
    @Override
    protected String doInBackground(File... files) {
        super.doInBackground(files);

        //Since we will upload the file to dropbox, the local zipfile can be deleted
        shouldDeleteZipFile = true;

        if (isCancelled()) return null;

        String name = FilenameUtils.getBaseName(to.getAbsolutePath());
        String availableName = dropboxSearch(name, ".bbx");
        if ((!availableName.equals(name)) && uploadMode == WriteMode.ADD) {
            Log.d(TAG, "showing dropbox dialog");
            showDropboxDialog(name, availableName, DropboxOperation.UPLOAD);
        } else {
            try (InputStream in = new FileInputStream(to)) {
                // TODO: Modified?? YES -> get actual last modified date as param
                // .withClientModified(new Date(file.lastModified()*1000))
                // TODO: Monitor progress?? Not for now
                String useName = (uploadMode == WriteMode.OVERWRITE) ? name : availableName;
                if (isCancelled()) return null;
                FileMetadata metadata = dropboxClient.files().uploadBuilder("/" + useName + ".bbx").withMute(true).withMode(uploadMode).uploadAndFinish(in);
                Log.d(TAG, "MetadataUpload: " + metadata);
                if (isCancelled()) return null;
                JSONObject newFiles = dropboxAppFolderContents();
                if (isCancelled()) return null;
                if (newFiles != null)
                    runJavascript("CallbackManager.cloud.filesChanged('" + bbxEncode(newFiles.toString()) + "')");
                return name;
            } catch (Exception e) {
                Log.e(TAG, "error: " + e.getLocalizedMessage());
            }
        }

        return null;
    }

    //TODO: delete dropbox file if present when operation is canceled?
}
