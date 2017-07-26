package com.birdbraintechnologies.birdblocks.Project;

import android.os.AsyncTask;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.Util.ZipUtility;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

import static com.birdbraintechnologies.birdblocks.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;

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
            Log.d("DROPBOX", "Unzipped to " + to.getAbsolutePath());
            return FilenameUtils.getBaseName(to.getName());
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "Unzip: " + e.getMessage());
            return null;
        } finally {
            try {
                files[0].delete();
            } catch (SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // TODO: Display unzip progress dialog to the user
    }

    @Override
    protected void onPostExecute(String name) {
        if (name != null) {
            super.onPostExecute(name);
            runJavascript("CallbackManager.cloud.downloadComplete('" + bbxEncode(name) + "')");
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
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }
}
