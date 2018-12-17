package com.birdbraintechnologies.birdblox.Project;

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import com.birdbraintechnologies.birdblox.Dialogs.ProgressDialog;

import java.io.File;

/**
 * Generic task for file converting. Superclass of ZipTask and UnzipTask
 *
 * @author krissie
 */

public abstract class FileTask extends AsyncTask<File, Long, String> {

    private final String TAG = this.getClass().getSimpleName();

    protected ProgressDialog progressDialog;
    protected File zipFile;
    protected File from;
    protected File to;
    protected boolean progressIsDeterminate = false;
    protected boolean shouldDeleteZipFile = true;

    /**
     * First step in the task. Used to setup the work. Sets up and shows a progress dialog.
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.d(TAG, "onPreExecute");
        progressDialog = new ProgressDialog(this, progressIsDeterminate);
        progressDialog.show();

    }

    /**
     * The work of the task.  Invoked on the background thread immediately after onPreExecute()
     * finishes executing.
     * @param files - array of files. Should contain 2 files.
     * @return - String result of the task
     */
    @Override
    protected String doInBackground(File... files) {
        Log.d(TAG, "doInBackground");
        if (files[0] == null || files[1] == null) {
            Log.e(TAG, "Error: files not specified correctly.");
            this.cancel(true);
        } else {
            from = files[0];
            to = files[1];
        }
        return null;
    }

    /**
     * Final step of the task. Invoked on the UI thread after the background computation finishes.
     * This method won't be invoked if the task was cancelled.
     * @param file - String result of the task. Should be the name of the file unzipped.
     */
    @Override
    protected void onPostExecute(String file) {
        if (file != null) super.onPostExecute(file);
        cleanup();
    }

    /**
     * Runs on the UI thread after cancel(boolean) is invoked and doInBackground(Object[]) has
     * finished. The default method does not use the String result, so calling super.onCancelled(s)
     * is unnecessary.
     * @param file - String result
     */
    @Override
    protected void onCancelled(String file) {
        Log.d(TAG, "operation cancelled");
        super.onCancelled();
        cleanup();
    }

    /**
     * Cleanup needed upon finishing or canceling the task.
     */
    private void cleanup() {
        if (zipFile != null && (isCancelled() || shouldDeleteZipFile)){
            try {
                if (!zipFile.delete()){
                    Log.v(TAG, "failure to delete zipfile " + zipFile.getName());
                }
                zipFile = null;
            } catch (SecurityException e){
                Log.e(TAG, "Unable to delete file " + zipFile.getName() + ": " + e.getMessage());
            }
        }

        progressDialog.progressBar.setVisibility(View.INVISIBLE);
        progressDialog.close();
    }
}
