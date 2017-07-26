package com.birdbraintechnologies.birdblocks.Project;

import android.os.AsyncTask;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.Util.ZipUtility;

import java.io.File;
import java.io.IOException;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class UnzipTask extends AsyncTask<File, Long, String> {
    private final String TAG = this.getClass().getName();

    public UnzipTask() {
        super();
    }

    @Override
    protected String doInBackground(File... files) {
        try {
            File zip = files[0];
            File to = files[1];
            ZipUtility.unzip(zip, to);
            return to.getName();
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unzip: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // TODO: Display unzip progress dialog to the user
    }

    @Override
    protected void onPostExecute(String s) {
        if (s != null) super.onPostExecute(s);
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
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }
}
