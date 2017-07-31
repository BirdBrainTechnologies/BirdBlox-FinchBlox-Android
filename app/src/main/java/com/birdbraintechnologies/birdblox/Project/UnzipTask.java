package com.birdbraintechnologies.birdblox.Project;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.birdbraintechnologies.birdblox.R;
import com.birdbraintechnologies.birdblox.Util.ZipUtility;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class UnzipTask extends AsyncTask<File, Long, String> {
    private final String TAG = this.getClass().getName();

    private AlertDialog.Builder builder;
    private AlertDialog unzipDialog;
    private ProgressBar progressBar;
    private Button cancelButton;
    private TextView showText;

    private File zipFile;

    public UnzipTask() {
        super();
        builder = new AlertDialog.Builder(mainWebViewContext);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                builder.setCancelable(false);
                unzipDialog = builder.create();
                final View dialogView = unzipDialog.getLayoutInflater().inflate(R.layout.progress_indeterminate, null);
                builder.setView(dialogView);
                progressBar = (ProgressBar) dialogView.findViewById(R.id.indeterminate_pb);
                progressBar.setVisibility(View.VISIBLE);
                cancelButton = (Button) dialogView.findViewById(R.id.indeterminate_btn);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UnzipTask.this.cancel(true);
                        unzipDialog.cancel();
                    }
                });
                showText = (TextView) dialogView.findViewById(R.id.indeterminate_tv);
                showText.setText("Processing...");
                unzipDialog = builder.create();
                unzipDialog.show();
            }
        });
    }

    @Override
    protected String doInBackground(File... files) {
        // TODO: Check downloaded file
        try {
            try {
                if (files[0] != null && files[1] != null) {
                    zipFile = files[0];
                    File to = files[1];
                    if (isCancelled()) return null;
                    ZipUtility.unzip(zipFile, to);
                    return FilenameUtils.getBaseName(to.getName());
                }
            } catch (ZipException e) {
                // TODO: Legacy Support here
                String contents = FileUtils.readFileToString(zipFile, "utf-8");
                File to = new File(files[1], "program.xml");
                if (!to.getParentFile().exists()) {
                    to.getParentFile().mkdirs();
                }
                FileUtils.writeStringToFile(to, contents, "utf-8", false);
            }
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unzip: " + e.getMessage());
            return null;
        } finally {
            try {
                if (zipFile != null) {
                    zipFile.delete();
                    zipFile = null;
                }
            } catch (SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(String name) {
        try {
            if (zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
            progressBar.setVisibility(View.INVISIBLE);
        } catch (SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
        }
        if (name != null) {
            super.onPostExecute(name);
            runJavascript("CallbackManager.cloud.downloadComplete('" + bbxEncode(name) + "')");
        }
        try {
            unzipDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close unzip dialog: " + e.getMessage());
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);
        // update unzip progress in the progress bar here ...
    }

    @Override
    protected void onCancelled(String s) {
        if (s != null) super.onCancelled(s);
        else super.onCancelled();
        try {
            if (zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
        } catch (SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
        } finally {
            try {
                unzipDialog.cancel();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to close unzip dialog: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        try {
            if (zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
        } catch (SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
        } finally {
            try {
                unzipDialog.cancel();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Unable to close unzip dialog: " + e.getMessage());
            }
        }
    }
}
