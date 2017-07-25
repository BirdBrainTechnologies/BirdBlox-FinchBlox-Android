package com.birdbraintechnologies.birdblocks.util;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class DownloadUtility {

    /**
     * Downloads the file at the given URL to the given location
     *
     * @param url        The URL of the file to be downloaded.
     * @param outputFile The location where the required file is to be downloaded,
     *                   passed in as a 'File' object.
     * @return true if the download succeeds, false otherwise.
     */
    private static boolean downloadFile(String url, File outputFile) {
        try {
            URL u = new URL(url);
            URLConnection conn = u.openConnection();
            int contentLength = conn.getContentLength();
            DataInputStream stream = new DataInputStream(u.openStream());
            byte[] buffer = new byte[contentLength];
            stream.readFully(buffer);
            stream.close();
            DataOutputStream fos = new DataOutputStream(new FileOutputStream(outputFile));
            fos.write(buffer);
            fos.flush();
            fos.close();
            return true;
        } catch (IOException e) {
            Log.e("DownloadUtility", e.getMessage());
            return false;
        }
    }

    /**
     * TODO: Add 'downloadFileWithProgress(String url, File outputFile)', if required
     * Use {@link ProgressOutputStream} instead of {@link java.io.OutputStream}.
     * Example implementation of {@link ProgressOutputStream} can be found in
     * {@link com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.DropboxRequestHandler},
     * inside the {@link dropboxDownload} function.
     * Can also be found here: https://github.com/dropbox/dropbox-sdk-java/issues/66
     */

}
