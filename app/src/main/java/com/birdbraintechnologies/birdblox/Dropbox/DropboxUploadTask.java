package com.birdbraintechnologies.birdblox.Dropbox;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.birdbraintechnologies.birdblox.MainWebView;
import com.birdbraintechnologies.birdblox.R;
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
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxSearch;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.showDropboxDialog;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

class DropboxUploadTask extends AsyncTask<String, Long, String> {
    private final String TAG = this.getClass().getSimpleName();

    private DbxClientV2 dropboxClient;
    private WriteMode uploadMode;

    private AlertDialog.Builder builder;
    private AlertDialog uploadDialog;
    private ProgressBar progressBar;
    private Button cancelButton;
    private TextView showText;

    private File localFile;

    DropboxUploadTask(DbxClientV2 dropboxClient, WriteMode uploadMode) {
        super();
        this.dropboxClient = dropboxClient;
        this.uploadMode = uploadMode;

        builder = new AlertDialog.Builder(mainWebViewContext);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                builder.setCancelable(false);
                uploadDialog = builder.create();
                final View dialogView = uploadDialog.getLayoutInflater().inflate(R.layout.progress_indeterminate, null);
                builder.setView(dialogView);
                progressBar = (ProgressBar) dialogView.findViewById(R.id.indeterminate_pb);
                progressBar.setVisibility(View.VISIBLE);
                cancelButton = (Button) dialogView.findViewById(R.id.indeterminate_btn);
                cancelButton.setText(MainWebView.cancel_text);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DropboxUploadTask.this.cancel(true);
                        uploadDialog.cancel();
                    }
                });
                showText = (TextView) dialogView.findViewById(R.id.indeterminate_tv);
                //showText.setText("Uploading...");
                showText.setText(MainWebView.loading_text);
                uploadDialog = builder.create();
                uploadDialog.show();
            }
        });
    }

    @Override
    protected String doInBackground(String... files) {
        try {
            if (files[0] != null) {
                localFile = new File(files[0]);
                String name = FilenameUtils.getBaseName(localFile.getAbsolutePath());
                String availableName = dropboxSearch(name, ".bbx");
                if ((!availableName.equals(name)) && uploadMode == WriteMode.ADD) {
                    showDropboxDialog(name, availableName, DropboxOperation.UPLOAD);
                } else {
                    try (InputStream in = new FileInputStream(localFile)) {
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
                        return files[0];
                    }
                }
            }
        } catch (IOException | DbxException | SecurityException | IllegalArgumentException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Unable to upload file: " + e.getMessage());
        } finally {
            try {
                if (localFile != null) {
                    localFile.delete();
                    localFile = null;
                }
            } catch (SecurityException | IllegalStateException e) {
                Log.e(TAG, "Unable to delete file: " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(String file) {
        if (file != null) super.onPostExecute(file);
        try {
            if (localFile != null) {
                localFile.delete();
                localFile = null;
            }
            progressBar.setVisibility(View.INVISIBLE);
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        }
        try {
            uploadDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close upload dialog: " + e.getMessage());
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onCancelled(String file) {
        if (file != null) super.onCancelled(file);
        else super.onCancelled();
        try {
            if (localFile != null) {
                localFile.delete();
                localFile = null;
            }
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        } finally {
            try {
                uploadDialog.cancel();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to close upload dialog: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        try {
            if (localFile != null) {
                localFile.delete();
                localFile = null;
            }
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        } finally {
            try {
                uploadDialog.cancel();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to close upload dialog: " + e.getMessage());
            }
        }
    }
}
