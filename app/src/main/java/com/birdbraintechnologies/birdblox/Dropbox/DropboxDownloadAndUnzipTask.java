package com.birdbraintechnologies.birdblox.Dropbox;


import android.util.Log;

import com.birdbraintechnologies.birdblox.Project.UnzipTask;
import com.birdbraintechnologies.birdblox.Util.ProgressOutputStream;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.DBX_DOWN_DIR;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;


/**
 * Subclass of UnzipTask that will first download a file from Dropbox
 * @author Shreyan Bakshi (AppyFizz)
 * @author krissie
 */

public class DropboxDownloadAndUnzipTask extends UnzipTask {
    private final String TAG = this.getClass().getSimpleName();

    private DbxClientV2 dropboxClient;
    private String localName;

    public DropboxDownloadAndUnzipTask(DbxClientV2 dropboxClient) {
        super();
        this.dropboxClient = dropboxClient;
        this.progressIsDeterminate = true;
    }


    /**
     * The work of the task. In this case, we must first download the file from Dropbox before it
     * can be unzipped.
     * @param files - array of files. Should be 2: file to download and unzip, and download destination.
     * @return - The String filename if operation is successful, null otherwise.
     */
    @Override
    protected String doInBackground(File... files) {
        /**
         * Implemented own {@link ProgressOutputStream}, since Dropbox API V2 has no built-in download progress.
         */
        File dbxDown; //Local destination for the DropBox download
        File dbxName; //DropBox filename

        if (files[0] == null || files[1] == null){
            Log.e(TAG, "Error: files not specified correctly.");
            return null;
        } else {
            dbxName = files[0];
            dbxDown = files[1];
            localName = FilenameUtils.getBaseName(files[1].getName());
        }

        Log.d(TAG, "doInBackground " + dbxName + " " + dbxDown + " " + localName);
        try {

            //Start by retrieving metadata. If this cannot be done, the task should be cancelled.
            try {
                dropboxClient.files().getMetadata("/" + dbxName.getPath());
            } catch (GetMetadataErrorException e) {
                if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                    Log.e(TAG, "Download: File " + dbxName + " not found.");
                    JSONObject obj = dropboxAppFolderContents();
                    if (obj != null)
                        runJavascript("CallbackManager.cloud.filesChanged('" + bbxEncode(obj.toString()) + "')");
                }
                Log.e(TAG, "Metadata error: " + e.getMessage());
                this.cancel(true);
            }

            //Make the destination folder if it doesn't exist
            if (!dbxDown.exists()) {
                boolean dirMade = dbxDown.getParentFile().mkdirs();
                boolean fileCreated = dbxDown.createNewFile();
                if (!dirMade || !fileCreated) {
                    Log.v(TAG, "while creating download file " + dirMade + " " + fileCreated);
                }
            }

            //Download the file from Dropbox to the destination folder
            FileOutputStream fout = new FileOutputStream(dbxDown);
            FileMetadata downloadData = null;
            try {
                Log.d(TAG, "About to download " + dbxName);
                DbxDownloader<FileMetadata> dbxDownloader = dropboxClient.files().download("/" + dbxName);
                long size = dbxDownloader.getResult().getSize();
                downloadData = dbxDownloader.download(new ProgressOutputStream(size, fout, new ProgressOutputStream.Listener() {
                    @Override
                    public void progress(long completed, long totalSize) {
                        if (isCancelled()) return;
                        publishProgress(((completed / totalSize) * 100));
                    }
                }));
            } finally {
                fout.close();
                if (downloadData != null)
                    Log.d(TAG, "MetadataDownload: " + downloadData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to download file: " + e.getMessage());
            this.cancel(true);
        }

        //If this task has been canceled, do some cleanup. Is this necessary?
        if (isCancelled()) {
            try {
                if (localName != null) {
                    dbxDown.delete();
                    localName = null;
                }
            } catch (SecurityException | IllegalStateException e) {
                Log.e(TAG, "Unable to delete file: " + e.getMessage());
            }
        }

        //If we have made it this far, unzip the downloaded file
        if (localName != null) {
            File to = new File(getBirdbloxDir(), localName);
            String result = super.doInBackground(dbxDown, to);
            return result;
        }

        return null;
    }

    /**
     * Method for display of progress on the UI. Invoked on the UI thread after a call to
     * publishProgress(Progress...).
     * Since we are using a determinate progress bar for the download task, we can
     * update that bar with progress.
     * @param progress - Long value of progress
     */
    @Override
    protected void onProgressUpdate(Long... progress) {
        super.onProgressUpdate(progress);
        progressDialog.progressBar.setProgress(Math.round(progress[0]));
    }

    /**
     * In this case, we just want to notify the frontend that the download has completed.
     * @param name - String result of the task. Should be the name of the file unzipped.
     */
    @Override
    protected void onPostExecute(String name) {
        if (name != null) {
            runJavascript("CallbackManager.cloud.downloadComplete('" + bbxEncode(name) + "')");
        }
        super.onPostExecute(name);
    }
}
