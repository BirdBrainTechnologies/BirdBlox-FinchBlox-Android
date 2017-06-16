package com.birdbraintechnologies.birdblocks;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblocks.dialogs.BirdblocksDialog;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.FileManagementHandler;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.birdbraintechnologies.birdblocks.httpservice.requesthandlers.PropertiesHandler.metrics;


/**
 * Displays the webview
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */


public class MainWebView extends AppCompatActivity {

    /*OLDER LOCATIONS FOR LOADING THE LAYOUT ARE IN THE TWO LINES BELOW*/
    // public static final String PAGE_URL = "file:///android_asset/frontend/HummingbirdDragAndDrop.html";
    // public static final String PAGE_URL = "http://rawgit.com/TomWildenhain/HummingbirdDragAndDrop-/dev/HummingbirdDragAndDrop.html";

    // public static boolean locationPermission;
    public static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;

    // True if device has microphone
    public static boolean deviceHasMicrophone;

    private WebView webView;
    private OrientationEventListener mOrientationListener;
    private String importedFile;
    private static final String BIRDBLOCKS_UNZIP_DIR = "Unzipped";
    private static final String BIRDBLOCKS_ZIP_DIR = "Zipped";
    private static final String BIRDBLOCKS_DIR = "Birdblocks";

    /* For double back exit */
    private static final int DOUBLE_BACK_DELAY = 2000;
    private long back_pressed;

    /* Broadcast receiver for displaying dialogs */
    public static final String SHOW_DIALOG = "com.birdbraintechnologies.birdblocks.DIALOG";
    public static final String SHARE_FILE = "com.birdbraintechnologies.birdblocks.SHARE_FILE";
    public static final String EXIT = "com.birdbraintechnologies.birdblocks.EXIT";
    public static final String LOCATION_PERMISSION = "com.birdbraintechnologies.birdblocks.REQUEST_LOCATION_PERMISSION";
    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("LocPerm", "Entered onReceive Method");
            if (intent.getAction().equals(SHOW_DIALOG)) {
                // Handles showing Choice and Text dialogs
                showDialog(intent.getExtras());
            } else if (intent.getAction().equals(SHARE_FILE)) {
                // Handles opening a share dialog
                showShareDialog(intent.getExtras());
            } else if (intent.getAction().equals(EXIT)) {
                exitApp();
            } else if (intent.getAction().equals(LOCATION_PERMISSION)) {
                // Handles requesting the user for location permissions
                Log.d("LocPerm", "Location Permission Intent Received");
                requestLocationPermissions();
            }
        }
    };
    LocalBroadcastManager bManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        FileManagementHandler.SecretFileDirectory = getFilesDir();
        // FileManagementHandler.SecretFileDirectory = new File(Environment.getExternalStoragePublicDirectory(
        //        Environment.DIRECTORY_DOCUMENTS), BIRDBLOCKS_DIR);

        importedFile = null;

        // Get intent
        Intent intent = getIntent();

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            String str = intent.getDataString();
            String last4 = str == null || str.length() < 4 ? str : str.substring(str.length() - 4);
            if (".bbx".equals(last4)) {
                Log.d("ImportIntent", "Hi, we are about to call copy function");
                importedFile = copyAndReturnFilename(intent);
            }
        }

        // Hide the status bar
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        if (getActionBar() != null)
            getActionBar().hide();

        deviceHasMicrophone = hasMicrophone();

        // locationPermission = (ContextCompat.checkSelfPermission(MainWebView.this,
        //        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);

        // Set hardware volume buttons to control media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // 'Parent' Location where downloaded, unzipped files are to be stored
        // Currently set to our app's 'secret' internal storage location
        final String parent_dir = getFilesDir().toString();

        // Create a new thread to perform download and unzip operations for layout
        Thread t = new Thread() {
            @Override
            public void run() {
                //Check if the locations to download and unzip already exist in the internal storage
                // If they don't, create them
                File f = new File(parent_dir + "/" + BIRDBLOCKS_ZIP_DIR + "/UI.zip");
                if (!f.exists()) try {
                    if (!f.getParentFile().exists())
                        f.getParentFile().mkdirs();
                    f.createNewFile();
                } catch (IOException | SecurityException e) {
                    Log.e("Download", e.getMessage());
                }
                File f2 = new File(parent_dir + "/" + BIRDBLOCKS_UNZIP_DIR);
                if (!f2.exists()) try {
                    f2.mkdirs();
                } catch (SecurityException e) {
                    Log.e("Download", e.getMessage());
                }

                // Download the layout from github
                try {
                    //    downloadFile("https://github.com/TomWildenhain/HummingbirdDragAndDrop-/archive/dev.zip", f);
                    downloadFile("https://github.com/BirdBrainTechnologies/HummingbirdDragAndDrop-/archive/dev.zip", f);
                } catch (NetworkOnMainThreadException | SecurityException e) {
                    Log.e("Download", "Error occurred while downloading file: " + e.getMessage());
                    return;
                }

                // Unzip the downloaded file
                try {
                    unzip(f, f2);
                } catch (IOException e) {
                    Log.e("Unzip", "Java I/O Error while unzipping file: " + e.getMessage());
                }
            }
        };

        // Spawn the thread (for download, unzip of layout)
        t.start();

        // Check device screen size, and adjust rotation settings accordingly
        adjustRotationSettings();

        // Wait for above thread to finish
        try {
            t.join();
        } catch (InterruptedException | NetworkOnMainThreadException e) {
            Log.e("Join Thread", "Exception while joining download thread: " + e.getMessage());
        }

        // Get location of downloaded layout as a 'File'
        File lFile = new File(parent_dir + "/" + BIRDBLOCKS_UNZIP_DIR + "/HummingbirdDragAndDrop--dev/HummingbirdDragAndDrop.html");
        if (!lFile.exists()) try {
            lFile.createNewFile();
        } catch (IOException | SecurityException e) {
            Log.e("LocFile", "Problem: " + e.getMessage());
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_web_view);

        // Start service
        startService(new Intent(this, HttpService.class));

        // Create webview
        webView = (WebView) findViewById(R.id.main_webview);
        webView.loadUrl("file:///" + lFile.getAbsolutePath());
        webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.resumeTimers();

        if (importedFile != null) {
            // Inject the JavaScript command to open the imported file into the webView
            Log.d("ImportIntent", "Final File Name: " + importedFile);
            webView.loadUrl("javascript:SaveManager.import(" + importedFile + ")");
        }

        // Broadcast receiver
        bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SHOW_DIALOG);
        intentFilter.addAction(SHARE_FILE);
        intentFilter.addAction(EXIT);
        intentFilter.addAction(LOCATION_PERMISSION);
        bManager.registerReceiver(bReceiver, intentFilter);

    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
        if (mOrientationListener != null)
            mOrientationListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.pauseTimers();
        webView.onPause();
        if (mOrientationListener != null)
            mOrientationListener.disable();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bManager.unregisterReceiver(bReceiver);
        webView.destroy();
        if (mOrientationListener != null)
            mOrientationListener.disable();
        stopService(new Intent(this, HttpService.class));
        stopService(new Intent(this, BluetoothHelper.class));
    }

    @Override
    public void onBackPressed() {
        if (back_pressed + DOUBLE_BACK_DELAY > System.currentTimeMillis()) {
            webView.evaluateJavascript("SaveManager.checkPromptSave(function() {\n" +
                    "\t\tHtmlServer.sendRequest(\"tablet/exit\");\n" +
                    "\t});", null);
        } else {
            Toast.makeText(getBaseContext(), "Press again to exit",
                    Toast.LENGTH_SHORT).show();
        }
        back_pressed = System.currentTimeMillis();
    }

    /**
     *
     *
     * @param inputPath
     * @param inputFile
     * @param outputPath
     */
    private void copyFile(String inputPath, String inputFile, String outputPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            //create output directory if it doesn't exist
            File dir = new File (outputPath);
            if (!dir.exists())
            {
                try {
                    dir.mkdirs();
                } catch (SecurityException e) {
                    Log.e("Copy Directory", "" + e.getMessage());
                }
            }
            in = new FileInputStream(inputPath + inputFile);
            out = new FileOutputStream(outputPath + inputFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            in.close();
            in = null;
            // write the output file (You have now copied the file)
            out.flush();
            out.close();
            out = null;
        }  catch (IOException e) {
            Log.e("Copy", e.getMessage());
        }
    }

    /**
     *
     *
     * @param intent
     * @return
     */
    private String copyAndReturnFilename(Intent intent) {
        String outputPath = FileManagementHandler.getBirdblocksDir().getAbsolutePath() + "/";
        String inputFile = intent.getData().getLastPathSegment();
        File file = new File(intent.getData().getPath());
        String inputPath = file.getParentFile().toString() + "/";
        copyFile(inputPath, inputFile, outputPath);
        return inputFile.substring(0, inputFile.length() - 4);
    }

    /**
     * Downloads the file at the given URL to the given location
     *
     * @param url        The URL of the file to be downloaded
     * @param outputFile The location where the required file is to be downlaoded,
     *                   passed in as a 'File' object
     */
    private static void downloadFile(String url, File outputFile) {
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
            Log.d("Download", "File Downloaded Successfully!!");
        } catch (IOException e) {
            Log.e("Download", "" + e);
        }
    }

    /**
     * Unzips the file at the given location, and stores the unzipped file at
     * the given target directory.
     *
     * @param zipFile         The location of the zip file (which is to be unzipped),
     *                        passed in as a 'File' object
     * @param targetDirectory The location (target directory) where the required file
     *                        is to be unzipped to, passed in as a 'File' object.
     */
    private static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
                Log.d("Unzip", "File Unzipped Successfully!!");
            }
        } catch (IOException e) {
            Log.e("Unzip", "Exception thrown while unzipping: " + e.toString());
        } finally {
            zis.close();
        }
        Log.d("Unzip", "File Unzipped Successfully!!");
    }

    /**
     * Checks device screen size, and adjusts rotation settings accordingly
     */
    private void adjustRotationSettings() {
        // Get the physical dimensions (width, height) of screen, and update  the static
        // variable metrics in the PropertiesHandler class with this information.
        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        // Store the width and height in inches for use here too
        float yInches = metrics.heightPixels / metrics.ydpi;
        float xInches = metrics.widthPixels / metrics.xdpi;
        // Calculate diagonal length of screen in inches
        double diagonalInches = Math.sqrt(xInches * xInches + yInches * yInches);

        if (diagonalInches >= 6.5) {
            // 6.5 inch device screen or bigger - In this case rotation is allowed
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
            // Resizes the webView upon screen rotation
            mOrientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    // Inject the JavaScript command to resize into webView
                    webView.loadUrl("javascript:GuiElements.updateDims()");
                }
            };
            mOrientationListener.enable();
        } else {
            // device screen smaller than 6.5 inch - In this case rotation is NOT allowed
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    /**
     * Determines availability of given port.
     *
     * @param port Port number of the required port
     * @return Returns true if given is available (not in use), and false otherwise.
     * @exception RuntimeException
     *
     */
    private static boolean port_available(int port) {
        System.out.println("--------------Testing port " + port);
        Socket s = null;
        try {
            s = new Socket("localhost", port);
            // If the code makes it this far without an exception it means
            // something is using the port and has responded.
            System.out.println("--------------Port " + port + " is not available");
            return false;
        } catch (IOException e) {
            System.out.println("--------------Port " + port + " is available");
            return true;
        } finally {
            if(s != null){
                try {
                    s.close();
                } catch (IOException e) {
                    throw new RuntimeException("You should handle this error." , e);
                }
            }
        }
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(MainWebView.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("LocPerm", "Location Permissions are Not Granted");
            ActivityCompat.requestPermissions(MainWebView.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_ACCESS_FINE_LOCATION);
            Log.d("LocPerm", "Location Permission REquested from user");
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.d("LocPerm", "Location Permission Response obtained");
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    // locationPermission = true;
                    Log.d("LocPerm", "Location Permissions allowed by user");
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // locationPermission = false;
                    Log.d("LocPerm", "Location Permissions not allowed by user");
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void showDialog(Bundle b) {
        BirdblocksDialog dialog = new BirdblocksDialog();
        dialog.setArguments(b);
        dialog.show(getFragmentManager(), "prompt_question");
        // dialog.setCancelable(true);
    }

    private void showShareDialog(Bundle b) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, (Uri) b.get("file_uri"));
        // TODO: Change to bbx
        sendIntent.setType("text/xml");
        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_to)));

    }

    private void exitApp() {
        Log.d("APP", "Exiting");
        this.stopService(getIntent());
        this.finishAndRemoveTask();
    }

    private boolean hasMicrophone() {
        PackageManager pManager = this.getPackageManager();
        return pManager.hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }


}
