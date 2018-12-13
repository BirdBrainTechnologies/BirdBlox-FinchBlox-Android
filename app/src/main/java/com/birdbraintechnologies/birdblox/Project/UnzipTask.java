package com.birdbraintechnologies.birdblox.Project;

import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.birdbraintechnologies.birdblox.Dialogs.ProgressDialog;
import com.birdbraintechnologies.birdblox.MainWebView;
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
 * Async Task used to unzip .bbx directories. Superclass of ImportUnzipTask which is used to unzip
 * files opened from outside the program, and of DropboxDownloadAndUnzipTask which is used to
 * download files from dropbox and then unzip them.
 * @author Shreyan Bakshi (AppyFizz)
 * @author krissie
 */

public class UnzipTask extends AsyncTask<File, Long, String> {
    private final String TAG = this.getClass().getSimpleName();
/*
    private AlertDialog.Builder builder;
    AlertDialog unzipDialog;
    protected ProgressBar progressBar;
    private Button cancelButton;
    private TextView showText;*/
    protected ProgressDialog unzipDialog;

    File zipFile;
    File to;

    //Is there a way to check the progress of this task?
    protected boolean progressIsDeterminate = false;

    public UnzipTask() {
        super();
        //builder = new AlertDialog.Builder(mainWebViewContext);
    }

    /**
     * First step in the task. Used to setup the work. Sets up and shows a progress dialog. On a
     * kindle fire, the entire unzip task takes less than 200ms, so this dialog is not really ever
     * shown. Will be shown for longer processes such as downloading from Dropbox.
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.d(TAG, "onPreExecute");

        unzipDialog = new ProgressDialog(this, progressIsDeterminate);
        unzipDialog.show();
        /*
        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                builder.setCancelable(false);
                unzipDialog = builder.create();

                //Set the layout to be used. If we will show progress with an updated progress bar,
                // the process is determinate.
                int dialogViewLayout;
                int progressBarView;
                int cancelBtnView;
                int showTextView;
                if (progressIsDeterminate){
                    dialogViewLayout = R.layout.progress_determinate;
                    progressBarView = R.id.determinate_pb;
                    cancelBtnView = R.id.determinate_btn;
                    showTextView = R.id.determinate_tv;
                } else {
                    dialogViewLayout = R.layout.progress_indeterminate;
                    progressBarView = R.id.indeterminate_pb;
                    cancelBtnView = R.id.indeterminate_btn;
                    showTextView = R.id.indeterminate_tv;
                }

                final View dialogView = unzipDialog.getLayoutInflater().inflate(dialogViewLayout, null);
                builder.setView(dialogView);

                progressBar = (ProgressBar) dialogView.findViewById(progressBarView);
                if (progressIsDeterminate) {
                    progressBar.setMax(100);
                    progressBar.setProgress(0);
                }
                progressBar.setVisibility(View.VISIBLE);

                cancelButton = (Button) dialogView.findViewById(cancelBtnView);
                cancelButton.setText(MainWebView.cancel_text);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UnzipTask.this.cancel(true);
                        unzipDialog.cancel();
                    }
                });

                showText = (TextView) dialogView.findViewById(showTextView);
                showText.setText(MainWebView.loading_text);
                unzipDialog = builder.create();
                unzipDialog.show();
            }
        });*/
    }

    /**
     * The work of the task.  Invoked on the background thread immediately after onPreExecute()
     * finishes executing.
     * @param files - array of files. Should be 2: file to unzip, and destination.
     * @return - String result of the task (the filename if success, null otherwise).
     */
    @Override
    protected String doInBackground(File... files) {
        // TODO: Check downloaded file
        Log.d(TAG, "doInBackground");
        if (files[0] == null || files[1] == null){
            Log.e(TAG, "Error: files not specified correctly.");
            return null;
        } else {
            zipFile = files[0];
            to = files[1];
        }

        try {
            try {
                if (isCancelled()) return null;
                ZipUtility.unzip(zipFile, to);
                return FilenameUtils.getBaseName(to.getName());
            } catch (ZipException e) {
                // TODO: Legacy Support here
                Log.e(TAG, "doInBackground zip exception: " + e);
                String contents = FileUtils.readFileToString(zipFile, "utf-8");
                to = files[1];
                if (!to.exists()) {
                    to.mkdirs();
                }
                FileUtils.writeStringToFile(new File(to, "program.xml"), contents, "utf-8", false);
                return FilenameUtils.getBaseName(to.getName());
            }
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unzip: " + e.getMessage());
            return null;
        }
    }

    /**
     * Method for display of progress on the UI. Invoked on the UI thread after a call to
     * publishProgress(Progress...).
     * This method could be implemented if we find a way to quantify the progress of unzipping.
     * @param values - Long Progress values passed to publishProgress(Progress... )
     */
    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);
        // update unzip progress in the progress bar here ...
    }

    /**
     * Final step of the task. Invoked on the UI thread after the background computation finishes.
     * This method won't be invoked if the task was cancelled.
     * @param name - String result of the task. Should be the name of the file unzipped.
     */
    @Override
    protected void onPostExecute(String name) {
        Log.d(TAG, "onPostExecute " + name);
        cleanUp();
        if (name != null) {
            super.onPostExecute(name);
            runJavascript("CallbackManager.cloud.downloadComplete('" + bbxEncode(name) + "')");
        }
        //closeUnzipDialog();
        unzipDialog.close();
    }

    /**
     * Clean up the class and check if the resulting file exists. Called after task completion.
     */
    void cleanUp() {
        deleteZipFile();
        try {
            if (!new File(to, "program.xml").exists()) {
                FileUtils.deleteDirectory(to);
                Log.e(TAG, "Could not download file");
                //Toast.makeText(mainWebViewContext, "Could not download file : Invalid file type", Toast.LENGTH_SHORT).show();
            }
            unzipDialog.progressBar.setVisibility(View.INVISIBLE);
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
        }
    }

    /**
     * Close the unzip dialog. Called when unzipping is finished or canceled.
     *//*
    void closeUnzipDialog(){
        try {
            unzipDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close unzip dialog: " + e.getMessage());
        }
    }*/

    /**
     * Delete the zipped file to prevent zip file clutter
     */
    void deleteZipFile() {
        try {
            if (zipFile != null) {
                if (zipFile.delete()){
                    zipFile = null;
                } else {
                    Log.e(TAG, "could not delete zipfile " + zipFile);
                }
            }
        } catch (SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
        }
    }


    /**
     * Runs on the UI thread after cancel(boolean) is invoked and doInBackground(Object[]) has
     * finished. The default method does not use the String result, so calling super.onCancelled(s)
     * is unnecessary.
     * @param s - String result
     */
    @Override
    protected void onCancelled(String s) {
        Log.d(TAG, "onCancelled, result: " + s);
        super.onCancelled();
        deleteZipFile();
        //closeUnzipDialog();
        unzipDialog.close();
    }

    /**
     * This override may not be necessary. This method is only invoked by super.onCancelled(s)
     *//*
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
    }*/
}
