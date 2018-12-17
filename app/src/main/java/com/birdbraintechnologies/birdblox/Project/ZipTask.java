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

import java.io.File;
import java.io.IOException;

import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;

/**
 * Subclass of FileTask used to zip programs into .bbx directories.
 *
 * @author Shreyan Bakshi (AppyFizz)
 * @author krissie
 */

public abstract class ZipTask extends FileTask {
    private final String TAG = this.getClass().getSimpleName();

    /**
     * The work of the task.  Invoked on the background thread immediately after onPreExecute()
     * finishes executing.
     * @param files - array of files. Should be 2: directory to zip, and destination.
     * @return - String result of the task (the filename if success, null otherwise).
     */
    @Override
    protected String doInBackground(File... files) {
        //In a zip task, the zipped file is the final product and should not be deleted unless
        // the task is canceled.
        shouldDeleteZipFile = false;

        super.doInBackground(files);

        if(isCancelled()) return null;
        zipFile = to;

        try {
            ZipUtility.zipDirectory(from, to);
            return zipFile.getAbsolutePath();
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unable to zip project: " + e.getMessage());
        }
        return null;
    }

}
