package com.birdbraintechnologies.birdblox.Util;

/**
 * @author Shreyan Bakshi (AppyFizz)
 *
 * SOURCE: https://stackoverflow.com/questions/20774525/is-it-possible-to-convert-a-folder-into-a-file
 */

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;



public class ZipUtility {

    private static final String TAG = "ZipUtility";
    public static final void zipDirectory(File directory, File zip) throws IOException {
        if (!zip.exists()) {
            zip.getParentFile().mkdirs();
            try {
                zip.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "zipDirectory: " + e.getMessage());
            }
        }
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
            // Set compression level to uncompressed.
            zos.setLevel(Deflater.NO_COMPRESSION);
            zip(directory, directory, zos);
            zos.close();
        } catch (IOException e) {
            Log.e(TAG, "zipDirectory: " + e.getMessage());
        }

    }

    private static final void zip(File directory, File base,
                                  ZipOutputStream zos) throws IOException {
        File[] files = directory.listFiles();
        byte[] buffer = new byte[8192];
        int read = 0;
        for (int i = 0, n = files.length; i < n; i++) {
            if (files[i].isDirectory()) {
                zip(files[i], base, zos);
            } else {
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
    }

    public static final void unzip(File zip, File extractTo) throws IOException {
        ZipFile archive = new ZipFile(zip);
        Enumeration e = archive.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();
            File file = new File(extractTo, entry.getName());
            if (entry.isDirectory() && !file.exists()) {
                file.mkdirs();
            } else {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
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
            }
        }
    }

}