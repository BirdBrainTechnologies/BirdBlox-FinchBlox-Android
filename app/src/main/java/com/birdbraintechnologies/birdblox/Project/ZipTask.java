package com.birdbraintechnologies.birdblox.Project;

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
import com.birdbraintechnologies.birdblox.Util.ZipUtility;

import java.io.File;
import java.io.IOException;

import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class ZipTask extends AsyncTask<File, Long, String> {
    private final String TAG = this.getClass().getSimpleName();

    private AlertDialog.Builder builder;
    private AlertDialog zipDialog;
    private ProgressBar progressBar;
    private Button cancelButton;
    private TextView showText;

    private File zipFile;

    public ZipTask() {
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
                zipDialog = builder.create();
                final View dialogView = zipDialog.getLayoutInflater().inflate(R.layout.progress_indeterminate, null);
                builder.setView(dialogView);
                progressBar = (ProgressBar) dialogView.findViewById(R.id.indeterminate_pb);
                progressBar.setVisibility(View.VISIBLE);
                cancelButton = (Button) dialogView.findViewById(R.id.indeterminate_btn);
                cancelButton.setText(MainWebView.cancel_text);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ZipTask.this.cancel(true);
                        zipDialog.cancel();
                    }
                });
                showText = (TextView) dialogView.findViewById(R.id.indeterminate_tv);
                //showText.setText("Processing...");
                showText.setText("...");
                zipDialog = builder.create();
                zipDialog.show();
            }
        });
    }

    @Override
    protected String doInBackground(File... files) {
        try {
            if (files[0] != null && files[1] != null) {
                File directory = files[0];
                zipFile = files[1];
                if (isCancelled()) return zipFile.getAbsolutePath();
                ZipUtility.zipDirectory(directory, zipFile);
                return zipFile.getAbsolutePath();
            }
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unable to zip project: " + e.getMessage());
        }
        return null;
    }

    @Override
    protected void onPostExecute(String file) {
        if (file != null) super.onPostExecute(file);
        try {
            if (isCancelled() && zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
            progressBar.setVisibility(View.INVISIBLE);
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        }
        try {
            zipDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close zip dialog: " + e.getMessage());
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);
        // update zip progress in the progress bar here ...
    }

    @Override
    protected void onCancelled(String file) {
        if (file != null) super.onCancelled(file);
        else super.onCancelled();
        try {
            if (isCancelled() && zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
            progressBar.setVisibility(View.INVISIBLE);
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        }
        try {
            zipDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close zip dialog: " + e.getMessage());
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        try {
            if (isCancelled() && zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
            progressBar.setVisibility(View.INVISIBLE);
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Unable to delete file: " + e.getMessage());
        }
        try {
            zipDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close zip dialog: " + e.getMessage());
        }
    }
}
