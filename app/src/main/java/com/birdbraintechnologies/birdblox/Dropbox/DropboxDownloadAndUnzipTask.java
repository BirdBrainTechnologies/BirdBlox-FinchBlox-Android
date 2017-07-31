package com.birdbraintechnologies.birdblox.Dropbox;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.birdbraintechnologies.birdblox.Project.UnzipTask;
import com.birdbraintechnologies.birdblox.R;
import com.birdbraintechnologies.birdblox.Util.ProgressOutputStream;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.DBX_DOWN_DIR;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppFolderContents;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.sanitizeName;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DropboxDownloadAndUnzipTask extends AsyncTask<String, Integer, String> {
    private final String TAG = this.getClass().getName();

    private DbxClientV2 dropboxClient;

    private AlertDialog.Builder builder;
    private AlertDialog downloadDialog;
    private ProgressBar progressBar;
    private Button cancelButton;
    private TextView showText;

    private String localName;

    public DropboxDownloadAndUnzipTask(DbxClientV2 dropboxClient) {
        super();
        this.dropboxClient = dropboxClient;

        builder = new AlertDialog.Builder(mainWebViewContext);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                builder.setCancelable(false);
                downloadDialog = builder.create();
                final View dialogView = downloadDialog.getLayoutInflater().inflate(R.layout.progress_determinate, null);
                builder.setView(dialogView);
                progressBar = (ProgressBar) dialogView.findViewById(R.id.determinate_pb);
                progressBar.setMax(100);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                cancelButton = (Button) dialogView.findViewById(R.id.determinate_btn);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DropboxDownloadAndUnzipTask.this.cancel(true);
                        downloadDialog.cancel();
                    }
                });
                showText = (TextView) dialogView.findViewById(R.id.determinate_tv);
                showText.setText("Downloading...");
                downloadDialog = builder.create();
                downloadDialog.show();
            }
        });
    }

    @Override
    protected String doInBackground(String... names) {
        /**
         * Implemented own {@link ProgressOutputStream}, since Dropbox API V2 has no built-in download progress.
         */
        try {
            if (names[0] != null && names[1] != null) {
                final String dbxName = names[0];
                localName = sanitizeName(names[1]);
                File dbxDownDir = new File(mainWebViewContext.getFilesDir(), DBX_DOWN_DIR);
                if (!dbxDownDir.exists()) dbxDownDir.mkdirs();
                File dbxDown = new File(dbxDownDir, localName + ".bbx");
                try {
                    dropboxClient.files().getMetadata("/" + dbxName + ".bbx");
                } catch (GetMetadataErrorException e) {
                    if (e.errorValue.isPath() && e.errorValue.getPathValue().isNotFound()) {
                        Log.e(TAG, "Download: File " + dbxName + " not found.");
                        downloadDialog.cancel();
                        // TODO: Display this error to the user
                        JSONObject obj = dropboxAppFolderContents();
                        if (obj != null)
                            runJavascript("CallbackManager.cloud.filesChanged('" + bbxEncode(obj.toString()) + "')");
                    } else {
                        throw e;
                    }
                }
                if (!dbxDown.exists()) {
                    dbxDown.getParentFile().mkdirs();
                    dbxDown.createNewFile();
                }
                FileOutputStream fout = new FileOutputStream(dbxDown);
                FileMetadata downloadData = null;
                try {
                    DbxDownloader<FileMetadata> dbxDownloader = dropboxClient.files().download("/" + dbxName + ".bbx");
                    long size = dbxDownloader.getResult().getSize();
                    downloadData = dbxDownloader.download(new ProgressOutputStream(size, fout, new ProgressOutputStream.Listener() {
                        @Override
                        public void progress(long completed, long totalSize) {
                            if (isCancelled()) return;
                            publishProgress((int) ((completed / (double) totalSize) * 100));
                        }
                    }));
                    return localName;
                } finally {
                    fout.close();
                    if (downloadData != null)
                        Log.d(TAG, "MetadataDownload: " + downloadData);
                }
            }
        } catch (DbxException | IOException | SecurityException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Unable to download file: " + e.getMessage());
            downloadDialog.cancel();
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);
        // update download progress in the progress bar here ...
        progressBar.setProgress(progress[0]);
    }

    @Override
    protected void onPostExecute(String name) {
        if (isCancelled()) {
            try {
                if (localName != null)
                    new File(mainWebViewContext.getFilesDir() + "/" + DBX_DOWN_DIR, localName + ".bbx").delete();
            } catch (SecurityException | IllegalStateException e) {
                Log.e(TAG, "Unable to delete file: " + e.getMessage());
            } finally {
                try {
                    downloadDialog.cancel();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Unable to close download dialog: " + e.getMessage());
                }
            }
            return;
        }
        if (localName != null) {
            super.onPostExecute(name);
            try {
                downloadDialog.cancel();
                File zip = new File(mainWebViewContext.getFilesDir() + "/" + DBX_DOWN_DIR, localName + ".bbx");
                File to = new File(getBirdbloxDir(), name);
                new UnzipTask().execute(zip, to);
            } catch (SecurityException e) {
                Log.e(TAG, "Error while unzipping project: " + name);
            }
        }
    }

    @Override
    protected void onCancelled(String name) {
        super.onCancelled(name);
        try {
            if (localName != null)
                new File(mainWebViewContext.getFilesDir() + "/" + DBX_DOWN_DIR, localName + ".bbx").delete();
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        } finally {
            try {
                downloadDialog.cancel();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to close download dialog: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        try {
            if (localName != null)
                new File(mainWebViewContext.getFilesDir() + "/" + DBX_DOWN_DIR, localName + ".bbx").delete();
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        } finally {
            try {
                downloadDialog.cancel();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to close download dialog: " + e.getMessage());
            }
        }
    }
}
