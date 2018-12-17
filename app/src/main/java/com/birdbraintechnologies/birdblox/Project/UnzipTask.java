package com.birdbraintechnologies.birdblox.Project;

import android.util.Log;

import com.birdbraintechnologies.birdblox.Util.ZipUtility;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;


/**
 * Async Task used to unzip .bbx directories. Superclass of ImportUnzipTask which is used to unzip
 * files opened from outside the program, and of DropboxDownloadAndUnzipTask which is used to
 * download files from dropbox and then unzip them.
 * @author Shreyan Bakshi (AppyFizz)
 * @author krissie
 */

public abstract class UnzipTask extends FileTask {
    private final String TAG = this.getClass().getSimpleName();

    /**
     * The work of the task.  Invoked on the background thread immediately after onPreExecute()
     * finishes executing.
     * @param files - array of files. Should be 2: file to unzip, and destination.
     * @return - String result of the task (the filename if success, null otherwise).
     */
    @Override
    protected String doInBackground(File... files) {
        // TODO: Check downloaded file
        //In an unzip task, the zipfile has been copied into a temp dir and should be deleted
        // at the end of the task.
        shouldDeleteZipFile = true;

        super.doInBackground(files);

        if (isCancelled()) return null;
        zipFile = from;

        Log.d(TAG, "unzipping from " + from.getAbsolutePath() + " to " + to.getAbsolutePath());

        try {
            try {
                Log.d(TAG, "About to unzip " + from.getName() + " to " + to.getName());
                ZipUtility.unzip(from, to);
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
     * Final step of the task. Invoked on the UI thread after the background computation finishes.
     * This method won't be invoked if the task was cancelled.
     * @param name - String result of the task. Should be the name of the file unzipped.
     */
    @Override
    protected void onPostExecute(String name) {
        Log.d(TAG, "onPostExecute " + name);
        super.onPostExecute(name);
        cleanUp();
    }

    /**
     * Clean up the class and check if the resulting file exists. Called after task completion.
     */
    void cleanUp() {
        try {
            if (!new File(to, "program.xml").exists()) {
                FileUtils.deleteDirectory(to);
                Log.e(TAG, "Could not download file");
            }
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterUnzip: " + e.getMessage());
        }
    }

}
