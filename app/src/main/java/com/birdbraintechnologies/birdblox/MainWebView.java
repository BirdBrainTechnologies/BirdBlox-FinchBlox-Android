package com.birdbraintechnologies.birdblox;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.NetworkOnMainThreadException;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblox.Dialogs.BirdBloxDialog;
import com.birdbraintechnologies.birdblox.Project.ImportUnzipTask;
import com.birdbraintechnologies.birdblox.Sound.CancelableMediaPlayer;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RecordingHandler;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.DB_PREFS_KEY;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxAppOAuth;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxClient;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxConfig;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.DropboxRequestHandler.dropboxWebOAuth;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.findAvailableName;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.getBirdbloxDir;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.FileManagementHandler.sanitizeName;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.PropertiesHandler.metrics;


/**
 * Displays the webview
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */


public class MainWebView extends AppCompatActivity {
    private String TAG = this.getClass().getName();

    /* OLDER LOCATIONS FOR LOADING THE LAYOUT ARE IN THE TWO LINES BELOW */
    private static final String PAGE_URL = "file:///android_asset/frontend/HummingbirdDragAndDrop.html";
//    private static final String PAGE_URL = "http://rawgit.com/TomWildenhain/HummingbirdDragAndDrop-/dev/HummingbirdDragAndDrop.html";

    /* Permission request codes */
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;
    public static final int MY_PERMISSIONS_REQUEST_MICROPHONE = 2;
    public static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 3;
    public static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 4;

    /* Broadcast receiver for displaying Dialogs */
    public static final String SHOW_DIALOG = "com.birdbraintechnologies.birdblox.DIALOG";
    public static final String SHARE_FILE = "com.birdbraintechnologies.birdblox.SHARE_FILE";
    public static final String SHARE_LOG = "com.birdbraintechnologies.birdblox.SHARE_LOG";
    public static final String EXIT = "com.birdbraintechnologies.birdblox.EXIT";
    public static final String LOCATION_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_LOCATION_PERMISSION";
    public static final String MICROPHONE_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_MICROPHONE_PERMISSION";
    public static final String READ_EXTERNAL_STORAGE_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_READ_EXTERNAL_STORAGE_PERMISSION";
    public static final String WRITE_EXTERNAL_STORAGE_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION";

    private static final String BIRDBLOX_UNZIP_DIR = "Unzipped";
    private static final String BIRDBLOX_ZIP_DIR = "Zipped";

    private static final String IMPORT_ZIP_DIR = "ZippedImport";

    private static HashSet<Intent> alreadyReceivedIntents = new HashSet<>();
    private static Uri lastFileUriData;

    /* For double back exit */
    private static final int DOUBLE_BACK_DELAY = 2000;

    public static Context mainWebViewContext;

    public static final ArrayList<CancelableMediaPlayer> mediaPlayers = new ArrayList<>();
    public static ArrayList<AudioTrack> tones = new ArrayList<>();

    LocalBroadcastManager bManager;
    private static WebView webView;
    private long back_pressed;

    private static final int REQUEST_PERMISSIONS = 1;
    private static String[] APP_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };


    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission2 = ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO);
        int permission3 = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permission != PackageManager.PERMISSION_GRANTED || permission2 != PackageManager.PERMISSION_GRANTED
                || permission3 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    APP_PERMISSIONS,
                    REQUEST_PERMISSIONS
            );
        }
    }


    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            switch (intent.getAction()) {
                case SHOW_DIALOG:
                    // Handles showing Choice and Text Dialogs
                    showDialog(intent.getExtras());
                    break;
                case SHARE_FILE:
                    // Handles opening a share dialog
                    showShareDialog(intent.getExtras());
                    break;
                case SHARE_LOG:
                    // Handles opening a share log file dialog
                    showShareLogDialog(intent.getExtras());
                    break;
                case EXIT:
                    exitApp();
                    break;
                case LOCATION_PERMISSION:
                    // Handles requesting the user for location permission
                    requestLocationPermission();
                    break;
                case MICROPHONE_PERMISSION:
                    // Handles requesting the user for microphone permission
                    requestMicrophonePermission();
                    break;
                case READ_EXTERNAL_STORAGE_PERMISSION:
                    // Handles requesting the user for reading external storage permission
                    requestReadExternalStoragePermission();
                    break;
                case WRITE_EXTERNAL_STORAGE_PERMISSION:
                    // Handles requesting the user for (reading and) writing external storage permissions
                    requestWriteExternalStoragePermission();
                    break;
                default:
                    Log.e(TAG, "Received unknown intent broadcast.");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* If the bluetooth service is already running, stop it. */
        stopService(new Intent(this, BluetoothHelper.class));
        /* If the HTTP service is already running, stop it. */
        stopService(new Intent(this, HttpService.class));

        mainWebViewContext = MainWebView.this;

        verifyStoragePermissions(this);
        // Hide the status bar
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        // We should never show the action bar if the status bar
        // is hidden, so we hide that too if necessary.
        if (getActionBar() != null)
            getActionBar().hide();

        // Set hardware volume buttons to control media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Spawn the thread (for download, unzip of layout)
//        unzipAndDownloadThread.start();

        // Check device screen size, and adjust rotation settings accordingly
        adjustRotationSettings();

        // Wait for above thread to finish
//        try {
//            unzipAndDownloadThread.join();
//        } catch (InterruptedException | NetworkOnMainThreadException e) {
//            Log.e("Join Thread", "Exception while joining download thread: " + e.getMessage());
//        }

        // Get location of downloaded layout as a 'File'
//        File lFile = new File(getFilesDir().toString() + "/" + BIRDBLOX_UNZIP_DIR + "/HummingbirdDragAndDrop--dev/HummingbirdDragAndDrop.html");
//        if (!lFile.exists()) try {
//            lFile.createNewFile();
//        } catch (IOException | SecurityException e) {
//            Log.e("LocFile", "Problem: " + e.getMessage());
//        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_web_view);

        // Start service
        startService(new Intent(this, HttpService.class));

        // Create webview
        webView = (WebView) findViewById(R.id.main_webview);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("MyApplication", "Message:" + consoleMessage.message() + ", Line:" + consoleMessage.lineNumber());
                return super.onConsoleMessage(consoleMessage);
            }
        });
//        webView.loadUrl("file:///" + lFile.getAbsolutePath());
        webView.loadUrl(PAGE_URL);

        webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.resumeTimers();


        // Broadcast receiver
        bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SHOW_DIALOG);
        intentFilter.addAction(SHARE_FILE);
        intentFilter.addAction(SHARE_LOG);
        intentFilter.addAction(EXIT);
        intentFilter.addAction(LOCATION_PERMISSION);
        intentFilter.addAction(MICROPHONE_PERMISSION);
        intentFilter.addAction(READ_EXTERNAL_STORAGE_PERMISSION);
        intentFilter.addAction(WRITE_EXTERNAL_STORAGE_PERMISSION);
        bManager.registerReceiver(bReceiver, intentFilter);

        SharedPreferences dropboxPrefs = this.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE);
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken != null) {
            // Create Dropbox client
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, accessToken);
        }

        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                runJavascript("CallbackManager.tablet.getLanguage('" + bbxEncode(Locale.getDefault().getCountry()) + "');");
                if (getIntent().getData() != null) {
                    runJavascript("CallbackManager.tablet.setFile('" + bbxEncode(getIntent().getData().toString()) + "');");
                }
                runJavascript(" CallbackManager.tablet.changeDeviceLimit('" + bbxEncode("2") + "');");

            }
        });
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
    protected void onStart() {
        super.onStart();
        mainWebViewContext = MainWebView.this;
    }


    @Override
    protected void onResume() {

        super.onResume();
        mainWebViewContext = MainWebView.this;
        webView.onResume();
        webView.resumeTimers();
        dropboxAppOAuth();
        importFromIntent(getIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestLocationPermission();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (getIntent().getData() != null) {
            runJavascript("CallbackManager.tablet.runFile('" + bbxEncode(getIntent().getData().toString()) + "');");
        }
        mainWebViewContext = MainWebView.this;
        dropboxWebOAuth(intent);
        importFromIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        (new RecordingHandler()).stopRecording();
        runJavascript("CallbackManager.sounds.recordingEnded()");
        webView.pauseTimers();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bManager.unregisterReceiver(bReceiver);
        webView.destroy();
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustRotationSettings();
    }

    private void importFromIntent(Intent intent) {
        if (intent == null) return;

        if (alreadyReceivedIntents.contains(intent)) return;
        else alreadyReceivedIntents.add(intent);

        String type = null;
        Uri data = null;
        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            String extraStream = null;
            if (intent.getExtras() != null && intent.getExtras().get(Intent.EXTRA_STREAM) != null) {
                extraStream = intent.getExtras().get(Intent.EXTRA_STREAM).toString();
            }
            if (extraStream != null) {
                int position = extraStream.indexOf(':');
                if (position >= 0) {
                    type = extraStream.substring(0, position);
                }
                data = Uri.parse(extraStream);
            }
        } else if (intent.getAction().equals(Intent.ACTION_VIEW)) {
            type = intent.getScheme();
            data = intent.getData();
        }
        if (type != null)
            Log.d("INTENTTYPE", "Type: " + type);
        if (data != null)
            Log.d("INTENTTYPE", "Data: " + data);
        if (type != null && data != null) {
            if (type.equals("content")) {
                sanitizeAndGetContent(data);
            } else if (type.equals("file")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    lastFileUriData = data;
                    requestReadExternalStoragePermission();
                }
                if (FilenameUtils.getExtension(data.toString()).equals("bbx")) {
                    sanitizeAndCopyFile(data);
                }
            }
        }
    }

    /**
     * Downloads the file at the given URL to the given location
     *
     * @param url        The URL of the file to be downloaded
     * @param outputFile The location where the required file is to be downloaded,
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
            Log.e("Download", e.getMessage());
        }
    }

    // Create a new thread to perform download and unzip operations for layout
    Thread unzipAndDownloadThread = new Thread() {
        @Override
        public void run() {
            // 'Parent' Location where downloaded, unzipped files are to be stored
            // Currently set to our app's 'secret' internal storage location
            final String parent_dir = getFilesDir().toString();
            //Check if the locations to download and unzip already exist in the internal storage
            // If they don't, create them
            File f = new File(parent_dir + "/" + BIRDBLOX_ZIP_DIR + "/UI.zip");
            if (!f.exists()) try {
                if (!f.getParentFile().exists())
                    f.getParentFile().mkdirs();
                f.createNewFile();
            } catch (IOException | SecurityException e) {
                Log.e("Download", e.getMessage());
            }
            File f2 = new File(parent_dir + "/" + BIRDBLOX_UNZIP_DIR);
            if (!f2.exists()) try {
                f2.mkdirs();
            } catch (SecurityException e) {
                Log.e("Download", e.getMessage());
            }
            // Download the layout from github
            try {
                downloadFile("https://github.com/TomWildenhain/HummingbirdDragAndDrop-/archive/dev.zip", f);
//                downloadFile("https://github.com/BirdBrainTechnologies/HummingbirdDragAndDrop-/archive/dev.zip", f);
//                downloadFile("https://github.com/BirdBrainTechnologies/HummingbirdDragAndDrop-/archive/stable.zip", f);
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
        Log.d("Unzip", "File Unzipped Successfully!");
    }

    /**
     * Creates a copy of the 'file to be imported' in the app's internal (secret) directory,
     * starts the unzip operation, and then returns the filename of this copy.
     *
     * @param data Uri containing path of file to be imported.
     * @return Returns the new (sanitized) filename
     */
    private synchronized String sanitizeAndCopyFile(Uri data) {
        try {
            lastFileUriData = null;
            File inputFile = new File(data.getPath());
            String newName = findAvailableName(getBirdbloxDir(), sanitizeName(FilenameUtils.getBaseName(inputFile.getName())), "");
            String extension = FilenameUtils.getExtension(inputFile.getName());
            File zipFile = new File(getFilesDir(), IMPORT_ZIP_DIR + "/" + newName + "." + extension);
            FileUtils.copyFile(inputFile, zipFile);
            File outputFile = new File(getBirdbloxDir(), newName);
            new ImportUnzipTask().execute(zipFile, outputFile);
            return newName;
        } catch (IOException | SecurityException | NullPointerException e) {
            Log.e("MainWebView", "SanitizeAndCopy: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gets the 'file to be imported' from the content, creates a copy of it in the app's internal
     * (secret) directory, starts the unzip operation, and then returns the filename of this copy.
     *
     * @param data Uri containing the file content.
     * @return Returns the new (sanitized) filename
     */
    private synchronized String sanitizeAndGetContent(Uri data) {
        try {
            String name = null;
            InputStream is;
            Cursor cursor = getContentResolver().query(data, new String[]{
                    MediaStore.MediaColumns.DISPLAY_NAME
            }, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            String fileName = FilenameUtils.getBaseName(name);
            String fileExt = FilenameUtils.getExtension(name);
            if (fileName == null || fileName == "" || fileExt == null || !fileExt.equals("bbx"))
                return null;
            File dir = new File(getFilesDir(), IMPORT_ZIP_DIR);
            if (!dir.exists())
                dir.mkdirs();
            String newName = findAvailableName(getBirdbloxDir(), fileName, "");
            File zipFile = new File(dir, newName + "." + fileExt);
            is = getContentResolver().openInputStream(data);
            FileUtils.copyInputStreamToFile(is, zipFile);
            is.close();
            File outputFile = new File(getBirdbloxDir(), newName);
            new ImportUnzipTask().execute(zipFile, outputFile);
            return newName;
        } catch (NullPointerException | IOException | SecurityException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
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
        if (diagonalInches < 6.5) {
            // Device screen smaller than 6.5 inch - In this case
            // rotation is allowed only within landscape mode
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        }
    }

    private void showDialog(Bundle b) {
        BirdBloxDialog dialog = new BirdBloxDialog();
        dialog.setArguments(b);
        dialog.show(getFragmentManager(), "prompt_question");
    }

    /**
     * @param b
     */
    private void showShareDialog(Bundle b) {
        try {
            String filename = b.getString("file_name");
            File filelocation = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), filename);
            Uri path = Uri.fromFile(filelocation);
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sendIntent.putExtra(Intent.EXTRA_STREAM, path);
            sendIntent.setType("application/zip");
            startActivity(Intent.createChooser(sendIntent, "Send email..."));

        } catch (Exception e) {
            Log.e("FileProvider", e.getMessage());
        }
    }

    /**
     * @param b
     */
    private void showShareLogDialog(Bundle b) {
        try {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID,
                    new File((String) b.get("log_file_path")));
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            // We are sharing txt files, so we give it a valid MIME type
            sendIntent.setType("text/*");
            if (sendIntent.resolveActivity(MainWebView.this.getPackageManager()) != null) {
                startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.share_with)));
            }
        } catch (Exception e) {
            Log.e("FileProvider", e.getMessage());
        }
    }

    private void exitApp() {
        Log.d("APP", "Exiting");
        this.stopService(getIntent());
        this.finishAndRemoveTask();
    }

    /**
     * Runs the given javascript within the main webview.
     *
     * @param script The required js, with all user inputs PERCENT-ENCODED using bbxEncode.
     */
    public static void runJavascript(final String script) {
        // TODO: Send JavaScript commands as broadcasts instead of making webview static
        Handler mainHandler = new Handler(mainWebViewContext.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript(script, null);
                    Log.d("RUNJS", script);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    /**
     * Custom percent-encode a String, to fit BirdBlox requirements.
     *
     * @param s The String to be percent-encoded.
     * @return The custom percent-encoded form of String s
     */
    public static String bbxEncode(String s) {
        if (s == null) return "";
        try {
            s = URLEncoder.encode(s, "utf-8");
            s = s.replace("+", "%20");
            // s = URLEncoder.encode(s, "utf-8");
            return s;
        } catch (UnsupportedEncodingException | NullPointerException e) {
            Log.e("bbxEncode", " " + e.getMessage());
        }
        return "";
    }

    private boolean requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                String message;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    message = "BirdBlox requires location permission in order to perform " +
                            "Bluetooth scans and get user location.";
                } else {
                    message = "BirdBlox requires location permission in order to get user location.";
                }
                new AlertDialog.Builder(this)
                        .setTitle("Location Permission")
                        .setMessage(message)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainWebView.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    private boolean requestMicrophonePermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                new AlertDialog.Builder(this)
                        .setTitle("Microphone Permission")
                        .setMessage("BirdBlox requires microphone permissions in order to record audio.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MainWebView.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        MY_PERMISSIONS_REQUEST_MICROPHONE);
                            }
                        })
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_MICROPHONE);
            }
            return false;
        } else {
            return true;
        }
    }

    private boolean requestReadExternalStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Read External Storage Permission")
                        .setMessage("BirdBlox requires permission to read external storage, in order to " +
                                "import certain files from external storage.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MainWebView.this,
                                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                            }
                        })
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            }
            return false;
        } else {
            return true;
        }
    }

    private boolean requestWriteExternalStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Write External Storage Permission")
                        .setMessage("BirdBlox requires permission to (read and) write external storage, in order to ... not required as of yet.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(MainWebView.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                            }
                        })
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        // DO REQUIRED TASK HERE
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) &&
                            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        // user also CHECKED "never ask again"
                        Toast.makeText(MainWebView.this, "Location permissions are required in order to use this feature.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
            break;

            case MY_PERMISSIONS_REQUEST_MICROPHONE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) &&
                            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        Toast.makeText(MainWebView.this, "Microphone permissions are required in order to use this feature.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
            break;

            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                        if (lastFileUriData != null) {
                            sanitizeAndCopyFile(lastFileUriData);
                        }
                    }
                } else {
                }
            }
            break;

            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                    }
                } else {
                }
            }
            break;

            default:
                Log.e(TAG, "Unrecognized permission request code.");
                break;
        }
    }

    /**
     * Checks if a given service is already running
     * <p>
     * SOURCE: https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
     *
     * @param serviceClass The class pertaining to the required service
     * @return true if the given service is running, false otherwise
     */
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
