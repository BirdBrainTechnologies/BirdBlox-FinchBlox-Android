package com.birdbraintechnologies.birdblocks;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class SplashScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Intent that starts SplashScreen
        Intent oldIntent = getIntent();

        // Intent to start MainWebView
        Intent webViewIntent = new Intent(this, MainWebView.class);

        // Pass Action of oldIntent to webViewIntent as "Action"
        webViewIntent.putExtra("Action", oldIntent.getAction());

        // Pass File Uri from oldIntent, if present, to webViewIntent as "Data"
        // Else, pass empty string as "Data".
        if (oldIntent.getDataString() != null)
            webViewIntent.putExtra("Data", oldIntent.getDataString());
        else if (oldIntent.getExtras() != null)
            webViewIntent.putExtra("Data", oldIntent.getExtras().get(Intent.EXTRA_STREAM).toString());
        else
            webViewIntent.putExtra("Data", "");

        // start main webView
        startActivity(webViewIntent);

        // close splash activity
        finish();
    }
}
