package com.birdbraintechnologies.birdblox.Project;

import android.util.Log;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.CURRENT_PREFS_KEY;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.filesPrefs;

/**
 * Class used for importing files from outside of BirdBlox. Used, for example, when opening a file
 * from email or from within the local file system.
 * @author Shreyan Bakshi (AppyFizz)
 * @author krissie
 */

public class ImportUnzipTask extends UnzipTask {
    private final String TAG = this.getClass().getSimpleName();

    /**
     * In the this case, we want to open the file immediately upon unzipping.
     * @param name - String result of the task. Should be the name of the file unzipped.
     */
    @Override
    protected void onPostExecute(String name) {
        Log.d(TAG, "on Post Execute " + name);
        if (name != null) {
            filesPrefs.edit().putString(CURRENT_PREFS_KEY, name).apply();
            runJavascript("CallbackManager.tablet.runFile('" + bbxEncode(name) + "');");
        }
        super.onPostExecute(name);
    }
}
