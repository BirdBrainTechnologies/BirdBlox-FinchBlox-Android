package com.birdbraintechnologies.birdblox.Dropbox;

import com.birdbraintechnologies.birdblox.Project.ZipTask;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxZipAndUploadTask extends ZipTask {
    String TAG = this.getClass().getName();

    private DbxClientV2 dropboxClient;
    private WriteMode uploadMode;

    public DropboxZipAndUploadTask(DbxClientV2 dropboxClient, WriteMode uploadMode) {
        super();
        this.dropboxClient = dropboxClient;
        this.uploadMode = uploadMode;
    }

    @Override
    protected void onPostExecute(String file) {
        if (file != null) {
            super.onPostExecute(file);
            new DropboxUploadTask(dropboxClient, uploadMode).execute(file);
        }
    }
}
