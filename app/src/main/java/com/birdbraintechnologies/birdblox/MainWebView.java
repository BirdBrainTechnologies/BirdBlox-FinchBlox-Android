package com.birdbraintechnologies.birdblox;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsetsAnimationController;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblox.Dialogs.BirdBloxDialog;
import com.birdbraintechnologies.birdblox.JSInterface.JavascriptInterface;
import com.birdbraintechnologies.birdblox.Project.ImportUnzipTask;
import com.birdbraintechnologies.birdblox.Sound.CancelableMediaPlayer;
//import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RecordingHandler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

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
    private String TAG = this.getClass().getSimpleName();

    /* OLDER LOCATIONS FOR LOADING THE LAYOUT ARE IN THE TWO LINES BELOW */
    private static final String PAGE_URL = "file:///android_asset/frontend/HummingbirdDragAndDrop.html";
//    private static final String PAGE_URL = "http://rawgit.com/TomWildenhain/HummingbirdDragAndDrop-/dev/HummingbirdDragAndDrop.html";

    /* Permission request codes */
    public static final int MY_PERMISSIONS_REQUEST_ALL = 0;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;
    public static final int MY_PERMISSIONS_REQUEST_MICROPHONE = 2;
    public static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 3;
    public static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 4;
    public static final int MY_PERMISSIONS_BLUETOOTH_SCANNING = 5;
    public static final int MY_PERMISSIONS_BLUETOOTH_CONNECTING = 6;

    /* Broadcast receiver for displaying Dialogs */
    public static final String SHOW_DIALOG = "com.birdbraintechnologies.birdblox.DIALOG";
    public static final String SHARE_FILE = "com.birdbraintechnologies.birdblox.SHARE_FILE";
    public static final String SHARE_LOG = "com.birdbraintechnologies.birdblox.SHARE_LOG";
    public static final String EXIT = "com.birdbraintechnologies.birdblox.EXIT";
    public static final String LOCATION_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_LOCATION_PERMISSION";
    public static final String MICROPHONE_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_MICROPHONE_PERMISSION";
    public static final String READ_EXTERNAL_STORAGE_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_READ_EXTERNAL_STORAGE_PERMISSION";
    public static final String WRITE_EXTERNAL_STORAGE_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION";
    public static final String BLUETOOTH_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_BLUETOOTH_PERMISSION";
    public static final String BTCONNECT_PERMISSION = "com.birdbraintechnologies.birdblox.REQUEST_BTCONNECT_PERMISSION";

    private static final String BIRDBLOX_UNZIP_DIR = "Unzipped";
    private static final String BIRDBLOX_ZIP_DIR = "Zipped";

    private static final String IMPORT_ZIP_DIR = "ZippedImport";

    /* Popup text - must be retrieved from frontend after content has loaded */
    public static String name_error_already_exists = "";
    public static String cancel_text = "";
    public static String rename_text = "";
    public static String ok_text = "";
    public static String enter_new_name = "";
    public static String delete_text = "";
    public static String delete_question = "";
    public static String loading_text = "";

    private static HashSet<Intent> alreadyReceivedIntents = new HashSet<>();
    private static Uri lastFileUriData;

    /* For double back exit */
    private static final int DOUBLE_BACK_DELAY = 2000;

    public static Context mainWebViewContext;

    public static final ArrayList<CancelableMediaPlayer> mediaPlayers = new ArrayList<>();
    public static ArrayList<AudioTrack> tones = new ArrayList<>();

    LocalBroadcastManager bManager;
    private static WebView webView;
    private boolean webViewReady = false;
    private long back_pressed;


    public static void verifyPermissions(Activity activity) {

        List<String> APP_PERMISSIONS = new ArrayList<String>();
        APP_PERMISSIONS.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        APP_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        APP_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            APP_PERMISSIONS.add(Manifest.permission.BLUETOOTH_SCAN);
            APP_PERMISSIONS.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            APP_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        }
        Log.d("MainWebView", "finchblox? " + BuildConfig.IS_FINCHBLOX + "; SDK " + Build.VERSION.SDK_INT);
        if (!BuildConfig.IS_FINCHBLOX ||
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) &&
                        (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R))) {
            Log.d("MainWebView", "adding fine location permission");
            APP_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        String[] permissions = new String[APP_PERMISSIONS.size()];
        APP_PERMISSIONS.toArray(permissions);

        //Will only ask for permission if permission has not already been granted.
        ActivityCompat.requestPermissions(activity, permissions, MY_PERMISSIONS_REQUEST_ALL);
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
                case BLUETOOTH_PERMISSION:

                    //Also need connect permission, but will request when user has accepted scan
                    // since only one permission can be requested at a time.
                    requestBluetoothScanPermission();
                    break;
                case BTCONNECT_PERMISSION:
                    requestBluetoothConnectPermission();
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
        //stopService(new Intent(this, HttpService.class));

        mainWebViewContext = MainWebView.this;

        verifyPermissions(this);
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
        // Handle the splash screen transition.
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_web_view);

        // Set up an OnPreDrawListener to the root view.
        final View content = findViewById(android.R.id.content);
        content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        // Check if the initial data is ready.
                        if (webViewReady) {
                            // The content is ready; start drawing.
                            content.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        } else {
                            // The content is not ready; suspend.
                            return false;
                        }
                    }
                });

        // Start service
        //startService(new Intent(this, HttpService.class));

        // Create webview
        webView = (WebView) findViewById(R.id.main_webview);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("MyApplication", "Message:" + consoleMessage.message() + ", Line:" + consoleMessage.lineNumber());
                return super.onConsoleMessage(consoleMessage);
            }


        });
//        webView.loadUrl("file:///" + lFile.getAbsolutePath());
        webView.loadUrl(PAGE_URL);

        /*webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);*/
        setUiVis();

        WebSettings webSettings = webView.getSettings();
        Log.d(TAG, "user agent: " + webSettings.getUserAgentString());
        webSettings.setJavaScriptEnabled(true);
        final JavascriptInterface myJavaScriptInterface
                = new JavascriptInterface(this);
        webView.addJavascriptInterface(myJavaScriptInterface, "AndroidInterface");

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
        intentFilter.addAction(BLUETOOTH_PERMISSION);
        intentFilter.addAction(BTCONNECT_PERMISSION);
        bManager.registerReceiver(bReceiver, intentFilter);

        //Dropbox use discontinued September 2020
        /*SharedPreferences dropboxPrefs = this.getSharedPreferences(DB_PREFS_KEY, MODE_PRIVATE);
        String accessToken = dropboxPrefs.getString("access-token", null);
        if (accessToken != null) {
            // Create Dropbox client
            dropboxConfig = new DbxRequestConfig("BirdBloxAndroid/1.0");
            dropboxClient = new DbxClientV2(dropboxConfig, accessToken);
        }*/

        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                String defaultLang = Locale.getDefault().toString();
                Log.d(TAG, "setting language to " + defaultLang);
                //runJavascript("CallbackManager.tablet.getLanguage('" + bbxEncode(Locale.getDefault().getLanguage()) + "');");
                runJavascript("CallbackManager.tablet.getLanguage('" + bbxEncode(defaultLang) + "');");
                if (getIntent().getData() != null) {
                    Log.d(TAG, "onCreate intent contained " + getIntent().getData().toString());
                    importFromIntent(getIntent());
                    //runJavascript("CallbackManager.tablet.setFile('" + bbxEncode(getIntent().getData().toString()) + "');");
                }
                //runJavascript(" CallbackManager.tablet.changeDeviceLimit('" + bbxEncode("2") + "');");
                Log.d(TAG, "setting focus");
                webView.requestFocus(View.FOCUS_DOWN);
                webViewReady = true;
            }
        });
    }

    public static void setUiVis() {
        Handler mainHandler = new Handler(mainWebViewContext.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setUiVis();
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
        //dropboxAppOAuth(); //Dropbox use discontinued September 2020
        //importFromIntent(getIntent());

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            requestLocationPermission();
        }*/
        //requestBluetoothScanPermission();
        //requestBluetoothConnectPermission();
    }

    /**
     * Called when the app is brought back to the foreground with a new intent. For example,
     * when opening a file in birdblox from another app. onResume() will be called after.
     * @param intent The new Intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent " + intent.toString());
        setIntent(intent); //Without this, getIntent() will still return the original intent.
        //if (getIntent().getData() != null) {
        //    runJavascript("CallbackManager.tablet.runFile('" + bbxEncode(getIntent().getData().toString()) + "');");
        //}
        mainWebViewContext = MainWebView.this;
        //dropboxWebOAuth(intent); //Dropbox use discontinued September 2020
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
        //stopService(new Intent(this, HttpService.class));
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

    /**
     * Called from onCreate and onNewIntent. If the user is trying to open a file from outside of
     * BirdBlox (eg. from email or from the local file system), then the intent will carry that
     * information. This method will import and open the file if possible.
     * @param intent - the Intent used to open or resume BirdBlox
     */
    private void importFromIntent(Intent intent) {
        Log.d(TAG, "import From Intent " + intent.toString());
        if (intent == null) return;

        if (alreadyReceivedIntents.contains(intent)) return;
        else alreadyReceivedIntents.add(intent);

        String type = null;
        Uri data = null;
        String displayName = null;
        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            //TODO: in what case is this block called?
            Log.d(TAG, "import from intent action send");
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
            if (data.toString().startsWith("content://")) {
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(data, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (columnIndex >= 0) {
                            displayName = cursor.getString(columnIndex);
                            Log.d(TAG, "FILE NAME = " + displayName);
                        } else {
                            Log.e(TAG, "Could not retrieve file name");
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        if (type != null && data != null) {
            //if (type.equals("content")) {
            //    sanitizeAndGetContent(data);
            //} else if (type.equals("file")) {
            if (type.equals("file") || type.equals("content")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "about to request read external storage permission");
                    lastFileUriData = data;
                    requestReadExternalStoragePermission();
                }
                if (FilenameUtils.getExtension(data.toString()).equals("bbx") || displayName.endsWith(".bbx")) {
                    Log.d(TAG, "about to sanitize and copy file " + data.toString());
                    sanitizeAndCopyFile(data, type, displayName);
                } else {
                    Log.e(TAG, "Could not open file " + data.toString());
                }
            } else {
                Log.e(TAG, "importFromIntent: unknown type " + type);
            }
        }
    }


    /**
     * Creates a copy of the 'file to be imported' in the app's internal (secret) directory,
     * starts the unzip operation, and then returns the filename of this copy.
     *
     * @param data Uri containing path of file to be imported.
     * @param type String type of request: either file or content
     * @param displayName String name to display in file list. May be null.
     * @return Returns the new (sanitized) filename
     */
    private synchronized String sanitizeAndCopyFile(Uri data, String type, String displayName) {
        Log.d(TAG, "sanitize and copy file " + data.toString() + " of type " + type + " with displayName " + displayName);
        try {
            lastFileUriData = null;
            String newName;
            String extension;
            if (displayName != null) {
                newName = findAvailableName(getBirdbloxDir(), sanitizeName(FilenameUtils.getBaseName(displayName)), "");
                extension = FilenameUtils.getExtension(displayName);
            } else {
                newName = findAvailableName(getBirdbloxDir(), sanitizeName(FilenameUtils.getBaseName(data.getPath())), "");
                extension = FilenameUtils.getExtension(data.getPath());
            }
            File zipFile = new File(getFilesDir(), IMPORT_ZIP_DIR + "/" + newName + "." + extension);

            if (type.equals("file")) {
                File inputFile = new File(data.getPath());
                //String newName = findAvailableName(getBirdbloxDir(), sanitizeName(FilenameUtils.getBaseName(inputFile.getName())), "");
                //String extension = FilenameUtils.getExtension(inputFile.getName());
                //File zipFile = new File(getFilesDir(), IMPORT_ZIP_DIR + "/" + newName + "." + extension);
                FileUtils.copyFile(inputFile, zipFile);
            } else if (type.equals("content")) {
                InputStream source = mainWebViewContext.getContentResolver().openInputStream(data);
                FileUtils.copyInputStreamToFile(source, zipFile);
            } else {
                Log.e("MainWebView", "SanitizeAndCopy: unrecognized type " + type);
            }

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
        Log.d(TAG, "sanitize and get content " + data.toString());
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
     * Called during onCreate() and onConfigurationChanged(newConfig)
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


    /**
     * Shows the text and choice dialogs requested by the frontend.
     * @param b
     */
    private void showDialog(Bundle b) {
        BirdBloxDialog dialog = new BirdBloxDialog();
        dialog.setArguments(b);
        dialog.show(getFragmentManager(), "prompt_question");
    }

    /**
     * Shows the sharing dialog when the user wishes to share a file.
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
            //startActivity(Intent.createChooser(sendIntent, "Send email..."));
            startActivity(Intent.createChooser(sendIntent, null)); //title not necessary
        } catch (Exception e) {
            Log.e("FileProvider", e.getMessage());
        }
    }

    /**
     * Shows a dialog when the user wishes to share log files.
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

    /**
     * Programmatically exit the app. Originally called by the frontend FileMenu, a class which
     * has since been deprecated.
     */
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
                    //Log.d("RUNJS", script);
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

    private boolean requestPermission(String perm, String title, String message, int requestCode) {
        Log.d(TAG, "requesting permission: " + perm);

        //check to see if permission has already been granted.
        if (ActivityCompat.checkSelfPermission(this, perm)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "permission already granted for " + perm);
            return true;
        }

        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
            Log.d(TAG, "Special dialog being created for premission " + perm);
            // Show an explanation to the user *asynchronously* -- don't block this thread waiting
            // for the user's response! After the user sees the explanation, try again to request
            // the permission.
            new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //Prompt the user once explanation has been shown
                            ActivityCompat.requestPermissions(MainWebView.this,
                                    new String[]{perm}, requestCode);
                        }
                    })
                    .create()
                    .show();
        } else {
            Log.d(TAG, "Default request for premission " + perm);
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{perm}, requestCode);
        }
        return false;

    }

    private boolean requestBluetoothScanPermission() {
        return requestPermission(Manifest.permission.BLUETOOTH_SCAN,
                "Bluetooth Scanning",
                "To scan for devices, this app requires bluetooth scanning permission",
                MY_PERMISSIONS_BLUETOOTH_SCANNING);
    }

    private boolean requestBluetoothConnectPermission() {
        return requestPermission(Manifest.permission.BLUETOOTH_CONNECT,
                "Bluetooth Connection",
                "To connect to devices, this app requires bluetooth connect permission",
                MY_PERMISSIONS_BLUETOOTH_CONNECTING);
    }

    //region Request Permission
    private boolean requestLocationPermission() {
        return requestPermission(Manifest.permission.ACCESS_FINE_LOCATION,
                "Location Permission",
                "BirdBlox requires location permission for its location blocks.",
                MY_PERMISSIONS_REQUEST_LOCATION);
    }

    private boolean requestMicrophonePermission() {
        return requestPermission(Manifest.permission.RECORD_AUDIO,
                "Microphone Permission",
                "This app requires microphone permissions to record audio.",
                MY_PERMISSIONS_REQUEST_MICROPHONE);
    }

    private boolean requestReadExternalStoragePermission() {
        return requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
                "Read External Storage Permission",
                "This app requires permission to read external storage, in order to " +
                        "import certain files from external storage.",
                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
    }

    private boolean requestWriteExternalStoragePermission() {
        return requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "Write External Storage Permission",
                "This app requires permission to (read and) write external storage, in order to ... not required as of yet.",
                MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
    }

    /**
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult code " + requestCode);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ALL:
                Log.d(TAG, "onRequestPermissionsResult permissions " +
                        Arrays.toString(permissions) + "; results " + Arrays.toString(grantResults));
                break;
            case MY_PERMISSIONS_BLUETOOTH_SCANNING:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "User has accepted bluetooth scan permission. Now request connect permission.");
                    requestBluetoothConnectPermission();
                } else {
                    Log.d(TAG, "User rejected bluetooth scanning permission");
                }
                break;
            case MY_PERMISSIONS_BLUETOOTH_CONNECTING:
                break;
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
                            sanitizeAndCopyFile(lastFileUriData, "file", null);
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
    //endregion

    /**
     * Checks if a given service is already running
     * <p>
     * SOURCE: https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
     *
     * @param serviceClass The class pertaining to the required service
     * @return true if the given service is running, false otherwise
     */
    /*private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }*/



    /**
     * Create a new thread to perform download and unzip operations for the frontend files.
     * Currently unused.
     */
    /*
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
    };*/

    /**
     * Unzips the file at the given location, and stores the unzipped file at
     * the given target directory.
     * Used only in unzipping the frontend files.
     *
     * @param zipFile         The location of the zip file (which is to be unzipped),
     *                        passed in as a 'File' object
     * @param targetDirectory The location (target directory) where the required file
     *                        is to be unzipped to, passed in as a 'File' object.
     */
    /*
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
    }*/

    /**
     * Downloads the file at the given URL to the given location. Used only in downloading files
     * for the frontend.
     *
     * @param url        The URL of the file to be downloaded
     * @param outputFile The location where the required file is to be downloaded,
     *                   passed in as a 'File' object
     */
    /*
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
    }*/
}
