package com.birdbraintechnologies.birdblox.Project;

import android.os.AsyncTask;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Util.ZipUtility;

import java.io.File;
import java.io.IOException;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class ZipTask extends AsyncTask<File, Long, String> {
    private final String TAG = this.getClass().getName();

    public ZipTask() {
        super();
    }

    @Override
    protected String doInBackground(File... files) {
        try {
            File directory = files[0];
            File zip = files[1];
            ZipUtility.zipDirectory(directory, zip);
            return zip.getAbsolutePath();
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unable to zip project: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // TODO: Display zip progress dialog to the user
    }

    @Override
    protected void onPostExecute(String file) {
        if (file != null) super.onPostExecute(file);
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
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }
}
