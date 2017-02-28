package com.birdbraintechnologies.birdblocks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.birdbraintechnologies.birdblocks.dialogs.BirdblocksDialog;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;

/**
 * Displays the webview
 *
 * @author Terence Sun (tsun1215)
 */
public class MainWebView extends AppCompatActivity {
    private static final String PAGE_URL = "file:///android_asset/frontend/HummingbirdDragAndDrop.html";
    private WebView webView;

    /* Broadcast receiver for displaying dialogs */
    public static final String SHOW_DIALOG = "com.birdbraintechnologies.birdblocks.DIALOG";
    public static final String SHARE_FILE = "com.birdbraintechnologies.birdblocks.SHARE_FILE";
    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(SHOW_DIALOG)) {
                // Handles showing Choice and Text dialogs
                showDialog(intent.getExtras());
            } else if (intent.getAction().equals(SHARE_FILE)) {
                // Handles opening a share dialog
                showShareDialog(intent.getExtras());
            }
        }
    };
    LocalBroadcastManager bManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_web_view);

        // Start service
        startService(new Intent(this, HttpService.class));

        // Create webview
        webView = (WebView) findViewById(R.id.main_webview);
        webView.loadUrl(PAGE_URL);
        webView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.resumeTimers();

        // Broadcast receiver
        bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SHOW_DIALOG);
        intentFilter.addAction(SHARE_FILE);
        bManager.registerReceiver(bReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.pauseTimers();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bManager.unregisterReceiver(bReceiver);
        webView.destroy();
        stopService(new Intent(this, HttpService.class));
    }

    private void showDialog(Bundle b) {
        BirdblocksDialog dialog = new BirdblocksDialog();
        dialog.setArguments(b);
        dialog.show(getFragmentManager(), "prompt_question");
    }

    private void showShareDialog(Bundle b) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, (Uri) b.get("file_uri"));
        // TODO: Change to bbx
        sendIntent.setType("text/xml");
        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_to)));
    }
}
