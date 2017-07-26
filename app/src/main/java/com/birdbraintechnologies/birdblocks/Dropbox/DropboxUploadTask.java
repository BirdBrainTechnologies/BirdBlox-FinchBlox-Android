package com.birdbraintechnologies.birdblocks.Dropbox;

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

import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblocks.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxUploadTask extends AsyncTask<String, Long, String> {
    private final String TAG = this.getClass().getName();

    private DbxClientV2 dropboxClient;

    public DropboxUploadTask(DbxClientV2 dropboxClient) {
        super();
        this.dropboxClient = dropboxClient;
    }

    @Override
    protected String doInBackground(String... files) {
        try {
            File file = new File(files[0]);
            String filename = FilenameUtils.getName(file.getAbsolutePath());
            String name = FilenameUtils.getBaseName(filename);
            try (InputStream in = new FileInputStream(file)) {
                // TODO: Enforce upper limit on file size??
                // TODO: Mute?? Mode?? Modified??
                // TODO: Monitor progress??
                FileMetadata metadata = dropboxClient.files().uploadBuilder("/" + filename).withMode(WriteMode.OVERWRITE).uploadAndFinish(in);
                Log.d(TAG, "MetadataUpload: " + metadata);
                JSONObject newFiles = dropboxAppFolderContents();
                if (newFiles != null)
                    runJavascript("CallbackManager.cloud.filesChanged(" + newFiles.toString() + ")");
                return name;
            }
        } catch (IOException | DbxException | SecurityException | IllegalArgumentException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Unable to upload file: " + e.getMessage());
            return null;
        }
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
