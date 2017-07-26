package com.birdbraintechnologies.birdblocks.Dropbox;

import com.birdbraintechnologies.birdblocks.Project.ZipTask;
import com.dropbox.core.v2.DbxClientV2;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxZipAndUploadTask extends ZipTask {
    String TAG = this.getClass().getName();

    private DbxClientV2 dropboxClient;

    public DropboxZipAndUploadTask(DbxClientV2 dropboxClient) {
        super();
        this.dropboxClient = dropboxClient;
    }

    @Override
    protected void onPostExecute(String file) {
        if (file != null) {
            super.onPostExecute(file);
            new DropboxUploadTask(dropboxClient).execute(file);
        }
    }
}
