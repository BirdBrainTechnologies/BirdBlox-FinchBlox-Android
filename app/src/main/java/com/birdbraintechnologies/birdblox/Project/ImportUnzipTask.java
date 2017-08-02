package com.birdbraintechnologies.birdblox.Project;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class ImportUnzipTask extends UnzipTask {
    private final String TAG = this.getClass().getName();

    @Override
    protected void onPostExecute(String name) {
        try {
            if (zipFile != null) {
                zipFile.delete();
                zipFile = null;
            }
            if (!new File(to, "program.xml").exists()) {
                FileUtils.deleteDirectory(to);
                Toast.makeText(mainWebViewContext, "Could not import file : Invalid file type", Toast.LENGTH_LONG).show();
            }
            progressBar.setVisibility(View.INVISIBLE);
        } catch (IOException | SecurityException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            Log.e(TAG, "DeleteAfterImport: " + e.getMessage());
        }
        if (name != null) {
            try {
                String contents = FileUtils.readFileToString(new File(to, "program.xml"), "utf-8");
                runJavascript("CallbackManager.data.open('" + bbxEncode(name) + "', '" + bbxEncode(contents) + "');");
            } catch (IOException e) {
                Log.e(TAG, "OpenAfterImport: " + e.getMessage());
            }
        }
        try {
            unzipDialog.cancel();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Unable to close unzip dialog: " + e.getMessage());
        }
    }
}
