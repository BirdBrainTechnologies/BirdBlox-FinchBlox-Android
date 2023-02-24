package com.birdbraintechnologies.birdblox.Util;

/**
 * @author Shreyan Bakshi (AppyFizz)
 *
 * SOURCE: https://stackoverflow.com/questions/20774525/is-it-possible-to-convert-a-folder-into-a-file
 */

import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;

import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;



public class ZipUtility {

    private static final String TAG = "ZipUtility";

    /**
     * Zip up a directory
     * @param directory - The directory to be zipped.
     * @param zip - The destination file.
     * @throws IOException
     */
    public static final void zipDirectory(File directory, File zip) throws IOException {
        Log.d(TAG, "zip directory " + directory + " to " + zip);
        /* To check the contents of the destination directory...
        String[] list = zip.getParentFile().list();
        if (list == null){
            Log.d(TAG, "destination directory empty");
        } else {
            Log.d(TAG, "destination contents: " + Arrays.toString(list));
        }*/
        if (zip.exists()) Log.d(TAG, "file " + zip.getName() + " exists");
        if (!zip.exists()) {
            if(!zip.getParentFile().exists()){
                boolean success = zip.getParentFile().mkdirs();
                if (!success) {
                    throw new IOException("Unable to make " + zip.getParent());
                }
            }
            boolean success = zip.createNewFile();
            if (!success) {
                throw new IOException("Unable to make " + zip);
            }

        }

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
        // Set compression level to uncompressed.
        zos.setLevel(Deflater.NO_COMPRESSION);
        zip(directory, directory, zos);
        zos.close();
    }

    /**
     * Zip up a directory
     * @param projectName - The name of the project directory to be zipped.
     * @param destination - The destination file.
     * @throws IOException
     */
    public static final void exportZipDir(String projectName, FileOutputStream destination) throws IOException {
        File directory = new File(getBirdbloxDir(), projectName);

        ZipOutputStream zos = new ZipOutputStream(destination);
        // Set compression level to uncompressed.
        zos.setLevel(Deflater.NO_COMPRESSION);
        zip(directory, directory, zos);
        zos.close();
    }

    /**
     * Recursive function to zip the contents of a directory
     * @param directory - to be zipped
     * @param base - top level directory
     * @param zos - zip output stream destination
     * @throws IOException
     */
    private static final void zip(File directory, File base,
                                  ZipOutputStream zos) throws IOException {
        Log.d(TAG, "zipping " + directory.getAbsolutePath());
        File[] files = directory.listFiles();
        byte[] buffer = new byte[8192];
        int read = 0;
        for (int i = 0, n = files.length; i < n; i++) {
            if (files[i].isDirectory()) {
                Log.d(TAG, "zipping dir");
                zip(files[i], base, zos);
            } else {
                Log.d(TAG, "zipping file");
                FileInputStream in = new FileInputStream(files[i]);
                ZipEntry entry = new ZipEntry(files[i].getPath().substring(
                        base.getPath().length() + 1));
                zos.putNextEntry(entry);
                while (-1 != (read = in.read(buffer))) {
                    zos.write(buffer, 0, read);
                }
                in.close();
            }
        }
        Log.d(TAG, "zipping complete");
    }

    /**
     * Unzip a zipped directory
     * @param zip - the zip file to be extracted
     * @param extractTo - the destination directory
     * @throws IOException
     */
    public static final void unzip(File zip, File extractTo) throws IOException {
        ZipFile archive = new ZipFile(zip);
        Enumeration e = archive.entries();
        Log.d(TAG, "unzip " + zip.getName() + " " + extractTo.getName());
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();
            Log.d(TAG, "unzipping " + entry.getName());
            File file = new File(extractTo, entry.getName());
            /*String canonicalPath = file.getCanonicalPath();
            if (!canonicalPath.startsWith(extractTo.getPath())) {
                Log.e(TAG, canonicalPath + " does not start with " + extractTo.getPath());
                throw new SecurityException("Zip Path Traversal Vulnerability");
            }*/
            if (entry.isDirectory() && !file.exists()) {
                if(!file.mkdirs()){
                    throw new IOException("Failed to make " + file.getName());
                }
            } else {
                if (!file.getParentFile().exists()) {
                    if(!file.getParentFile().mkdirs()){
                        throw new IOException("Failed to make parent " + file.getParent());
                    }
                }
                InputStream in = archive.getInputStream(entry);
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                byte[] buffer = new byte[8192];
                int read;
                while (-1 != (read = in.read(buffer))) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.close();
                String unzipped = FileUtils.readFileToString(file, "utf-8");
                Log.d(TAG, "unzipping finished. Contents: '" + unzipped + "'");
            }
        }
    }

}